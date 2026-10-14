package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.MatchReportThreadRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.match.DotaApiLastMatchesService.PlayerLine;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator.PartialReportResult;
import com.ako.dbuff.service.replay.ReplayForecast;
import com.ako.dbuff.service.replay.report.view.ComponentsViewRenderer;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchReportStageServiceTest {
  private static final String CHANNEL = "channel-1";
  private static final String INSTANCE = "instance-1";
  private static final long MATCH = 77L;

  private final ReportStagesProperties properties = new ReportStagesProperties();
  private final DiscordMessageService discord = mock(DiscordMessageService.class);
  private final MatchReportThreadRepo threadRepo = mock(MatchReportThreadRepo.class);
  private final PlayerGameStatisticRepo statRepo = mock(PlayerGameStatisticRepo.class);
  private final ConstantsManagers constants = mock(ConstantsManagers.class);
  private final ReplayForecast forecast = mock(ReplayForecast.class);
  private final Message headerMessage = mock(Message.class);
  private final ThreadChannel thread = mock(ThreadChannel.class);
  private MatchReportStageService service;
  private DbufInstanceConfigDomain config;

  @BeforeEach
  void setUp() {
    properties.setEnabled(true);
    service =
        new MatchReportStageService(
            properties,
            new MatchFactsFactory(constants, statRepo),
            new HeaderRenderer(),
            new ReportHeaderPublisher(discord),
            new StageOneCardBuilder(constants),
            new ComponentsViewRenderer(new PlainViewRenderer()),
            discord,
            new MatchThreadRecorder(threadRepo),
            forecast);
    config =
        DbufInstanceConfigDomain.builder()
            .id(INSTANCE)
            .discordChannelId(CHANNEL)
            .players(Set.of(PlayerDomain.builder().id(1L).name("Tigress").build()))
            .build();
    when(headerMessage.getIdLong()).thenReturn(500L);
    when(thread.getIdLong()).thenReturn(600L);
    when(discord.sendChannelComponents(eq(CHANNEL), anyList())).thenReturn(headerMessage);
    when(discord.createThread(any(), anyString())).thenReturn(thread);
    when(constants.getHeroConstantMap()).thenReturn(Map.of());
    when(constants.getMatchTypeConstantMap()).thenReturn(Map.of());
  }

  private static NewMatchSummary summary() {
    PlayerLine line = new PlayerLine(1L, 0L, 14L, 10L, 2L, 5L, 512L, 640L, true);
    return new NewMatchSummary(2100L, 22L, List.of(line));
  }

  private static MatchReportThreadDomain row(MatchReportStage stage) {
    return MatchReportThreadDomain.builder()
        .matchId(MATCH)
        .instanceId(INSTANCE)
        .channelId(CHANNEL)
        .threadId(600L)
        .headerMessageId(500L)
        .stage(stage)
        .wins(1)
        .losses(0)
        .durationSeconds(2100L)
        .modeName("ability draft")
        .build();
  }

  private static MatchDomain parsedMatch() {
    return MatchDomain.builder()
        .id(MATCH)
        .gameModeName("game_mode_ability_draft")
        .radiantScore(40L)
        .direScore(25L)
        .duration(2100L)
        .build();
  }

  @SuppressWarnings("unchecked")
  private String editedTitle() {
    ArgumentCaptor<List<MessageTopLevelComponent>> captor = ArgumentCaptor.forClass(List.class);
    verify(discord).editComponentsMessage(eq(CHANNEL), eq(500L), captor.capture());
    Container card = (Container) captor.getValue().get(0);
    return ((net.dv8tion.jda.api.components.textdisplay.TextDisplay) card.getComponents().get(0))
        .getContent();
  }

  @Test
  void stageOne_postsAV2HeaderThreadAndOneCardAndStoresTheStage() {
    PartialReportResult result =
        service.start(
            MatchDomain.builder().id(MATCH).build(), config, summary(), Map.of(1L, "Tigress"));

    assertThat(result).isEqualTo(new PartialReportResult(600L, 500L));
    verify(discord, never()).sendChannelMessage(anyString(), anyString());
    verify(discord).createThread(headerMessage, "Match 77");
    verify(discord).sendThreadComponentsBlocking(eq(thread), anyList());
    ArgumentCaptor<MatchReportThreadDomain> saved =
        ArgumentCaptor.forClass(MatchReportThreadDomain.class);
    verify(threadRepo).save(saved.capture());
    assertThat(saved.getValue().getStage()).isEqualTo(MatchReportStage.REQUESTED);
    assertThat(saved.getValue().getWins()).isEqualTo(1);
    assertThat(saved.getValue().getDurationSeconds()).isEqualTo(2100L);
  }

  @Test
  void stageOne_aRejectedV2HeaderFallsBackToPlainText() {
    when(discord.sendChannelComponents(eq(CHANNEL), anyList()))
        .thenThrow(new IllegalArgumentException("bad components"));
    when(discord.sendChannelMessage(eq(CHANNEL), anyString())).thenReturn(headerMessage);

    PartialReportResult result =
        service.start(MatchDomain.builder().id(MATCH).build(), config, summary(), Map.of());

    assertThat(result).isNotNull();
    verify(discord)
        .sendChannelMessage(
            eq(CHANNEL), org.mockito.ArgumentMatchers.contains("OpenDota parse requested"));
  }

  @Test
  void stageOne_aRejectedCardIsPostedAsPlainText() {
    when(discord.sendThreadComponentsBlocking(any(), anyList()))
        .thenThrow(new IllegalStateException("bad components"));

    service.start(MatchDomain.builder().id(MATCH).build(), config, summary(), Map.of());

    verify(discord)
        .sendThreadMessageBlocking(eq(thread), org.mockito.ArgumentMatchers.contains("Win"));
  }

  @Test
  void stageOne_withoutLinesPostsNoCard() {
    service.start(
        MatchDomain.builder().id(MATCH).build(), config, NewMatchSummary.empty(), Map.of());

    verify(discord, never()).sendThreadComponentsBlocking(any(), anyList());
  }

  @Test
  void stageOne_aFailingChannelNeverThrows() {
    when(discord.sendChannelComponents(eq(CHANNEL), anyList()))
        .thenThrow(new RuntimeException("discord down"));

    assertThat(service.start(MatchDomain.builder().id(MATCH).build(), config, summary(), Map.of()))
        .isNull();
  }

  @Test
  void stageOne_aChannelThatCannotBeFoundGivesNothing() {
    when(discord.sendChannelComponents(eq(CHANNEL), anyList())).thenReturn(null);

    assertThat(service.start(MatchDomain.builder().id(MATCH).build(), config, summary(), Map.of()))
        .isNull();
    verify(discord, never()).createThread(any(), anyString());
  }

  private void statsOfTrackedPlayer() {
    PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
    stat.setPlayerId(1L);
    stat.setWin(1L);
    when(statRepo.findAllByMatchId(MATCH)).thenReturn(List.of(stat));
  }

  @Test
  void stageTwo_retitlesToReplayProcessingWhenTheForecastSaysSo() {
    statsOfTrackedPlayer();
    MatchReportThreadDomain row = row(MatchReportStage.REQUESTED);
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.of(row));
    MatchDomain match = parsedMatch();
    when(forecast.isExpected(match)).thenReturn(true);

    service.parsed(match, config);

    assertThat(editedTitle())
        .isEqualTo("**Match 77 · ability draft · Win (40-25) · 35 min · replay processing**");
    assertThat(row.getStage()).isEqualTo(MatchReportStage.REPLAY_PENDING);
    verify(threadRepo).save(row);
    verify(discord, never()).sendThreadComponentsBlocking(any(), anyList());
  }

  @Test
  void stageTwo_saysDataReadyWhenNoReplayIsExpected() {
    statsOfTrackedPlayer();
    MatchReportThreadDomain row = row(MatchReportStage.REQUESTED);
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.of(row));

    service.parsed(parsedMatch(), config);

    assertThat(editedTitle()).contains("OpenDota data ready");
    assertThat(row.getStage()).isEqualTo(MatchReportStage.PARSED);
  }

  @Test
  void stageTwo_withoutAThreadRowOrHeaderDoesNothingHarmful() {
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.empty());
    assertThatCode(() -> service.parsed(parsedMatch(), config)).doesNotThrowAnyException();

    MatchReportThreadDomain noHeader = row(MatchReportStage.REQUESTED);
    noHeader.setHeaderMessageId(null);
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.of(noHeader));
    assertThatCode(() -> service.parsed(parsedMatch(), config)).doesNotThrowAnyException();

    verify(discord, never()).editComponentsMessage(anyString(), anyLong(), anyList());
  }

  @Test
  void anEditThatFailsOrFindsADeletedMessageNeverThrows() {
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE))
        .thenReturn(Optional.of(row(MatchReportStage.REQUESTED)));
    org.mockito.Mockito.doThrow(new RuntimeException("Unknown Message"))
        .when(discord)
        .editComponentsMessage(anyString(), anyLong(), anyList());

    assertThatCode(() -> service.parsed(parsedMatch(), config)).doesNotThrowAnyException();
    assertThatCode(() -> service.parseTimedOut(MATCH, INSTANCE)).doesNotThrowAnyException();
  }

  @Test
  void aRejectedEditFallsBackToPlainText() {
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE))
        .thenReturn(Optional.of(row(MatchReportStage.REQUESTED)));
    org.mockito.Mockito.doThrow(new IllegalStateException("not v2"))
        .when(discord)
        .editComponentsMessage(anyString(), anyLong(), anyList());

    service.parseTimedOut(MATCH, INSTANCE);

    verify(discord)
        .editMessage(eq(CHANNEL), eq(500L), org.mockito.ArgumentMatchers.contains("did not parse"));
  }

  @Test
  void timeout_keepsTheStageOneFactsInTheTitle() {
    MatchReportThreadDomain row = row(MatchReportStage.REQUESTED);
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.of(row));

    service.parseTimedOut(MATCH, INSTANCE);

    assertThat(editedTitle())
        .isEqualTo(
            "**Match 77 · ability draft · Win · 35 min · OpenDota did not parse this match**");
    assertThat(row.getStage()).isEqualTo(MatchReportStage.PARSE_TIMED_OUT);
  }

  @Test
  void switchedOff_nothingIsEditedOrLookedUp() {
    properties.setEnabled(false);

    service.parsed(parsedMatch(), config);
    service.parseTimedOut(MATCH, INSTANCE);

    org.mockito.Mockito.verifyNoInteractions(discord, threadRepo);
    assertThat(service.enabled()).isFalse();
  }
}
