package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayReportPostedDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.LaneReasonRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MatchReportThreadRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayReportPostedRepo;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.match.report.formatter.DiscordReportFormatter;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.lane.LaneProperties;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.ako.dbuff.service.scheduler.QuietHoursGuard;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

  private final ReplayParseRunRepo runRepo = mock(ReplayParseRunRepo.class);

  @BeforeEach
  void setUp() {
    properties.setEnabled(true);
    ReplaySection section = new ReplaySection();
    service =
        new ReplayReportService(
            properties,
            new ReplayProcessingProperties(),
            runRepo,
            matchRepo,
            statRepo,
            instanceRepo,
            threadRepo,
            postedRepo,
            List.of(section),
            new SectionSwitch(properties, List.of(section)),
            new DiscordReportFormatter(),
            discord,
            quiet,
            TestModes.registry());
    when(matchRepo.findById(1L))
        .thenReturn(Optional.of(MatchDomain.builder().id(1L).gameModeId(18L).build()));
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
    public String name() {
      return "replay";
    }

    @Override
    public boolean defaultEnabled() {
      return true;
    }

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
  void deliver_leavesOutASectionThatDoesNotApplyToTheMatchMode() {
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
            List.of(new AbilityDraftOnlySection()),
            new SectionSwitch(properties, List.of(new AbilityDraftOnlySection())),
            new DiscordReportFormatter(),
            discord,
            quiet,
            TestModes.registry());
    when(matchRepo.findById(2L))
        .thenReturn(Optional.of(MatchDomain.builder().id(2L).gameModeId(22L).build()));
    when(statRepo.findAllByMatchId(2L))
        .thenReturn(
            List.of(
                PlayerMatchStatisticDomain.builder()
                    .matchId(2L)
                    .playerSlot(0L)
                    .playerId(5L)
                    .build()));
    when(threadRepo.findByMatchIdAndInstanceId(2L, "i1"))
        .thenReturn(
            Optional.of(
                MatchReportThreadDomain.builder()
                    .matchId(2L)
                    .instanceId("i1")
                    .threadId(9L)
                    .build()));

    service.deliver(2L);
    verify(discord, never()).sendThreadMessageBlocking(any(), any());

    service.deliver(1L);
    verify(discord).sendThreadMessageBlocking(any(), any());
  }

  private static class AbilityDraftOnlySection extends ReplaySection {
    @Override
    public boolean supports(GameModeProfile mode) {
      return mode.gameModeId() == 18L;
    }
  }

  private ReplayReportService serviceWith(ReplayReportSection... all) {
    List<ReplayReportSection> list = List.of(all);
    return new ReplayReportService(
        properties,
        new ReplayProcessingProperties(),
        runRepo,
        matchRepo,
        statRepo,
        instanceRepo,
        threadRepo,
        postedRepo,
        list,
        new SectionSwitch(properties, list),
        new DiscordReportFormatter(),
        discord,
        quiet,
        TestModes.registry());
  }

  private static ReplayReportSection named(String name, boolean defaultEnabled) {
    return new ReplaySection() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public boolean defaultEnabled() {
        return defaultEnabled;
      }

      @Override
      public List<String> lines(ReplayReportInput input) {
        return List.of("**" + name + "**", "10:00: line of " + name);
      }
    };
  }

  private String posted() {
    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(discord, Mockito.atLeastOnce())
        .sendThreadMessageBlocking(Mockito.eq(thread), text.capture());
    return String.join("\n", text.getAllValues());
  }

  private void enable(String name, boolean on) {
    ReplayReportProperties.SectionToggle toggle = new ReplayReportProperties.SectionToggle();
    toggle.setEnabled(on);
    properties.getSections().put(name, toggle);
  }

  @Test
  void deliver_aDisabledSectionPostsNothingWhileTheOthersStillPost() {
    enable("beta", false);
    service = serviceWith(named("alpha", true), named("beta", true));

    service.deliver(1L);

    assertThat(posted()).contains("line of alpha").doesNotContain("line of beta");
  }

  @Test
  void deliver_aDefaultOffSectionPostsOnlyOnceItsPropertyIsTrue() {
    service = serviceWith(named("alpha", true), named("fresh", false));
    service.deliver(1L);
    assertThat(posted()).contains("line of alpha").doesNotContain("line of fresh");

    Mockito.clearInvocations(discord, postedRepo);
    enable("fresh", true);
    service.deliver(1L);
    assertThat(posted()).contains("line of fresh");
  }

  @Test
  void deliver_theLanesSectionPostsNothingUntilItsPropertyIsTrue() {
    LaneResultRepo lanes = Mockito.mock(LaneResultRepo.class);
    when(lanes.findByMatchId(1L))
        .thenReturn(
            List.of(
                LaneResultDomain.builder()
                    .pairId(0)
                    .lane("TOP")
                    .team("RADIANT")
                    .result("WON")
                    .margin(0.5)
                    .build()));
    LanesSection section =
        new LanesSection(
            lanes,
            Mockito.mock(LaneAssignmentRepo.class),
            Mockito.mock(LaneReasonRepo.class),
            Mockito.mock(ReportHeroNames.class),
            new LaneProperties(),
            properties);
    service = serviceWith(named("alpha", true), section);
    service.deliver(1L);
    assertThat(posted()).contains("line of alpha").doesNotContain("Top lane");

    Mockito.clearInvocations(discord, postedRepo);
    enable("lanes", true);
    service.deliver(1L);
    assertThat(posted()).contains("Top lane").contains("Radiant (your team) won clearly");
  }

  @Test
  void deliver_aDefaultOnSectionCanBeSwitchedOffAndAnUnsetOneFollowsItsDefault() {
    enable("alpha", false);
    service = serviceWith(named("alpha", true), named("beta", true));

    service.deliver(1L);

    assertThat(posted()).doesNotContain("line of alpha").contains("line of beta");
  }

  @Test
  void deliver_neverReadsADisabledSectionAndSavesNothingWhenEverySectionIsOff() {
    ReplayReportSection off = Mockito.spy(named("alpha", true));
    enable("alpha", false);
    service = serviceWith(off);

    service.deliver(1L);

    verify(off, never()).lines(any());
    verify(postedRepo, never()).save(any());
    verify(discord, never()).sendThreadMessageBlocking(any(), any());
  }

  @Test
  void validate_ignoresLeftoverPropertiesWhileTheReportIsOff() {
    properties.setEnabled(false);
    enable("nope", true);

    new SectionSwitch(properties, List.of(named("alpha", true), named("alpha", true))).validate();
  }

  @Test
  void validate_failsOnABlankNameNamingTheBeanClass() {
    SectionSwitch sw = new SectionSwitch(properties, List.of(named(" ", true)));

    assertThatThrownBy(sw::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ReplayReportServiceTest");
  }

  @Test
  void isEnabled_treatsANullSectionsMapAsEmptyAndMatchesNamesIgnoringCaseDashAndUnderscore() {
    ReplayReportSection splits = named("team-splits", false);
    properties.setSections(null);
    assertThat(new SectionSwitch(properties, List.of(splits)).isEnabled(splits)).isFalse();

    properties.setSections(new java.util.HashMap<>());
    enable("TEAM_SPLITS", true);
    assertThat(new SectionSwitch(properties, List.of(splits)).isEnabled(splits)).isTrue();
    properties.getSections().clear();
    enable("teamsplits", true);
    assertThat(new SectionSwitch(properties, List.of(splits)).isEnabled(splits)).isTrue();
  }

  @Test
  void validate_failsOnAPropertyForAnUnknownSectionNamingTheKnownOnes() {
    enable("nope", true);
    SectionSwitch sw =
        new SectionSwitch(properties, List.of(named("alpha", true), named("beta", true)));

    assertThatThrownBy(sw::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nope")
        .hasMessageContaining("[alpha, beta]");
  }

  @Test
  void validate_failsOnADuplicateSectionName() {
    SectionSwitch sw =
        new SectionSwitch(properties, List.of(named("alpha", true), named("alpha", true)));

    assertThatThrownBy(sw::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("alpha");
  }

  @Test
  void deliverFinished_postsForADoneRun() {
    when(runRepo.findByMatchIdAndParserVersion(1L, 1))
        .thenReturn(Optional.of(run(ReplayStatus.DONE, null)));

    service.deliverFinished(1L);

    verify(postedRepo).save(any());
  }

  @Test
  void deliverFinished_postsForAPartialRunOnlyWhenNoRetryIsLeft() {
    when(runRepo.findByMatchIdAndParserVersion(1L, 1))
        .thenReturn(Optional.of(run(ReplayStatus.PARTIAL, LocalDateTime.now().plusHours(1))));

    service.deliverFinished(1L);

    verify(postedRepo, never()).save(any());

    when(runRepo.findByMatchIdAndParserVersion(1L, 1))
        .thenReturn(Optional.of(run(ReplayStatus.PARTIAL, null)));

    service.deliverFinished(1L);

    verify(postedRepo).save(any());
  }

  @Test
  void deliverFinished_doesNothingForAFailedRunOrNoRun() {
    when(runRepo.findByMatchIdAndParserVersion(1L, 1))
        .thenReturn(Optional.of(run(ReplayStatus.FAILED_DOWNLOAD, null)));

    service.deliverFinished(1L);
    service.deliverFinished(2L);

    verify(postedRepo, never()).save(any());
  }

  private static ReplayParseRunDomain run(ReplayStatus status, LocalDateTime nextAttemptAt) {
    return ReplayParseRunDomain.builder()
        .matchId(1L)
        .parserVersion(1)
        .status(status)
        .nextAttemptAt(nextAttemptAt)
        .build();
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
