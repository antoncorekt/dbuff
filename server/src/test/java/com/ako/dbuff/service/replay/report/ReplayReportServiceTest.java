package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReplayReportPostedDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MatchReportThreadRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayReportPostedRepo;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.match.report.formatter.DiscordReportFormatter;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.scheduler.QuietHoursGuard;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayReportServiceTest {

  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final MatchRepo matchRepo = mock(MatchRepo.class);
  private final PlayerGameStatisticRepo statRepo = mock(PlayerGameStatisticRepo.class);
  private final DbufInstanceConfigRepository instanceRepo =
      mock(DbufInstanceConfigRepository.class);
  private final MatchReportThreadRepo threadRepo = mock(MatchReportThreadRepo.class);
  private final ReplayReportPostedRepo postedRepo = mock(ReplayReportPostedRepo.class);
  private final DiscordMessageService discord = mock(DiscordMessageService.class);
  private final QuietHoursGuard quiet = mock(QuietHoursGuard.class);
  private final ThreadChannel thread = mock(ThreadChannel.class);
  private ReplayReportService service;

  private DbufInstanceConfigDomain instance(InstanceSettings settings) {
    return DbufInstanceConfigDomain.builder()
        .id("i1")
        .discordChannelId("c1")
        .settings(settings)
        .players(Set.of(PlayerDomain.builder().id(5L).name("Anton").build()))
        .build();
  }

  @BeforeEach
  void setUp() {
    properties.setEnabled(true);
    ReplaySection section = new ReplaySection();
    service =
        new ReplayReportService(
            properties,
            new ReplayProcessingProperties(),
            mock(ReplayParseRunRepo.class),
            matchRepo,
            statRepo,
            instanceRepo,
            threadRepo,
            postedRepo,
            List.of(section),
            new DiscordReportFormatter(),
            discord,
            quiet);
    when(matchRepo.findById(1L)).thenReturn(Optional.of(MatchDomain.builder().id(1L).build()));
    when(statRepo.findAllByMatchId(1L))
        .thenReturn(
            List.of(
                PlayerMatchStatisticDomain.builder()
                    .matchId(1L)
                    .playerSlot(0L)
                    .playerId(5L)
                    .build()));
    when(instanceRepo.findActiveByPlayerId(5L)).thenReturn(List.of(instance(null)));
    when(threadRepo.findByMatchIdAndInstanceId(1L, "i1"))
        .thenReturn(
            Optional.of(
                MatchReportThreadDomain.builder()
                    .matchId(1L)
                    .instanceId("i1")
                    .threadId(9L)
                    .build()));
    when(postedRepo.findByMatchIdAndInstanceId(any(), any())).thenReturn(Optional.empty());
    when(discord.getThreadById(9L)).thenReturn(thread);
  }

  private static class ReplaySection implements ReplayReportSection {
    @Override
    public int order() {
      return 1;
    }

    @Override
    public ReportDetail minimumDetail() {
      return ReportDetail.STANDARD;
    }

    @Override
    public List<String> lines(ReplayReportInput input) {
      return List.of("**Swings**", "10:00: " + input.namesBySlot().get(0L) + " did something");
    }
  }

  @Test
  void deliver_postsTheSectionsInTheThreadOnceAndRemembersIt() {
    service.deliver(1L);

    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(discord).sendThreadMessageBlocking(Mockito.eq(thread), text.capture());
    assertThat(text.getValue()).contains("Swings").contains("Anton did something");
    ArgumentCaptor<ReplayReportPostedDomain> posted =
        ArgumentCaptor.forClass(ReplayReportPostedDomain.class);
    verify(postedRepo).save(posted.capture());
    assertThat(posted.getValue().getMessages()).isEqualTo(1);
  }

  @Test
  void deliver_doesNothingWhileTheServerSwitchIsOff() {
    properties.setEnabled(false);

    service.deliver(1L);

    verify(discord, never()).sendThreadMessageBlocking(any(), any());
  }

  @Test
  void deliver_skipsAChannelThatTurnedItOffOrAskedForMinimalReports() {
    when(instanceRepo.findActiveByPlayerId(5L))
        .thenReturn(
            List.of(instance(InstanceSettings.builder().replayReportEnabled(false).build())));
    service.deliver(1L);
    when(instanceRepo.findActiveByPlayerId(5L))
        .thenReturn(
            List.of(
                instance(InstanceSettings.builder().reportDetail(ReportDetail.MINIMAL).build())));
    service.deliver(1L);

    verify(discord, never()).sendThreadMessageBlocking(any(), any());
  }

  @Test
  void deliver_leavesItForLaterInQuietHoursAndWithoutAThread() {
    when(quiet.isQuietTime(any())).thenReturn(true);
    service.deliver(1L);
    when(quiet.isQuietTime(any())).thenReturn(false);
    when(threadRepo.findByMatchIdAndInstanceId(1L, "i1")).thenReturn(Optional.empty());
    service.deliver(1L);

    verify(discord, never()).sendThreadMessageBlocking(any(), any());
    verify(postedRepo, never()).save(any());
  }

  @Test
  void deliver_marksTheReportPostedBeforeSending_soAFailureDoesNotRepostTheFirstMessage() {
    org.mockito.InOrder order = Mockito.inOrder(postedRepo, discord);

    service.deliver(1L);

    order.verify(postedRepo).save(any());
    order.verify(discord).sendThreadMessageBlocking(any(), any());
  }

  @Test
  void deliver_neverPostsTwice() {
    when(postedRepo.findByMatchIdAndInstanceId(1L, "i1"))
        .thenReturn(Optional.of(ReplayReportPostedDomain.builder().build()));

    service.deliver(1L);

    verify(discord, never()).sendThreadMessageBlocking(any(), any());
  }

  @Test
  void deliver_triesAgainLaterWhenTheThreadCannotBeFoundNow() {
    when(discord.getThreadById(9L)).thenReturn(null);

    service.deliver(1L);

    verify(discord, never()).sendThreadMessageBlocking(any(), any());
    verify(postedRepo, never()).save(any());
  }
}
