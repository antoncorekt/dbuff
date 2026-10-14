package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayReportPostedDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.model.SmokeUseDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.LaneReasonRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MatchReportThreadRepo;
import com.ako.dbuff.dao.repo.ObjectiveConversionRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayReportPostedRepo;
import com.ako.dbuff.dao.repo.SmokeUseRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.match.report.formatter.DiscordReportFormatter;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.lane.LaneProperties;
import com.ako.dbuff.service.replay.metrics.ReplayMetrics;
import com.ako.dbuff.service.replay.metrics.ReplayRunCounts;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ComponentsViewRenderer;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.scheduler.QuietHoursGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
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
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final MatchRepo matchRepo = mock(MatchRepo.class);
  private final PlayerGameStatisticRepo statRepo = mock(PlayerGameStatisticRepo.class);
  private final DbufInstanceConfigRepository instanceRepo =
      mock(DbufInstanceConfigRepository.class);
  private final MatchReportThreadRepo threadRepo = mock(MatchReportThreadRepo.class);
  private final ReplayReportPostedRepo postedRepo = mock(ReplayReportPostedRepo.class);
  private final DiscordMessageService discord = mock(DiscordMessageService.class);
  private final QuietHoursGuard quiet = mock(QuietHoursGuard.class);
  private final ThreadChannel thread = mock(ThreadChannel.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
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
    service = serviceWith(new ReplaySection());
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
  void deliver_namesATrackedPlayerWithHisHero() {
    when(heroNames.bySlot(1L)).thenReturn(Map.of(0L, "Nature's Prophet"));

    service.deliver(1L);

    assertThat(posted()).contains("Anton (Nature's Prophet) did something");
  }

  @Test
  void deliver_namesATrackedPlayerAloneWhenHisHeroIsUnknown() {
    when(heroNames.bySlot(1L)).thenReturn(Map.of(128L, "Axe"));

    service.deliver(1L);

    assertThat(posted()).contains("Anton did something").doesNotContain("Anton (");
  }

  @Test
  void deliver_leavesOutASectionThatDoesNotApplyToTheMatchMode() {
    service = serviceWith(new AbilityDraftOnlySection());
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
        TestModes.registry(),
        new ReplayMetrics(registry, mock(ReplayRunCounts.class)),
        new ComponentsViewRenderer(new PlainViewRenderer()),
        heroNames);
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
  void deliver_theRotationsSectionPostsNothingUntilItsPropertyIsTrue() {
    TeamRotationRepo rotations = Mockito.mock(TeamRotationRepo.class);
    when(rotations.findByMatchId(1L))
        .thenReturn(
            List.of(
                TeamRotationDomain.builder()
                    .id(1L)
                    .team("RADIANT")
                    .playerSlot(0L)
                    .startSeconds(600)
                    .toZone("LANE")
                    .toLane("TOP")
                    .verdict("DIED")
                    .reasons("DIED")
                    .build()));
    RotationsSection section =
        new RotationsSection(rotations, Mockito.mock(ReportHeroNames.class), properties);
    service = serviceWith(named("alpha", true), section);
    service.deliver(1L);
    assertThat(posted()).contains("line of alpha").doesNotContain("Team rotations");

    Mockito.clearInvocations(discord, postedRepo);
    enable("rotations", true);
    service.deliver(1L);
    assertThat(posted()).contains("Team rotations").contains("died on the trip");
  }

  @Test
  void deliver_theSmokeSectionPostsNothingUntilItsPropertyIsTrueAndIsIndependentOfRotations() {
    SmokeUseRepo smokes = Mockito.mock(SmokeUseRepo.class);
    when(smokes.findByMatchId(1L))
        .thenReturn(
            List.of(
                SmokeUseDomain.builder()
                    .id(1L)
                    .team("RADIANT")
                    .userSlot(0L)
                    .groupSlots("0")
                    .startSeconds(600)
                    .endSeconds(640)
                    .outcome("PAID_OFF")
                    .endReason("EXPIRED")
                    .build()));
    SmokeSection section =
        new SmokeSection(smokes, Mockito.mock(ReportHeroNames.class), properties);
    service = serviceWith(named("alpha", true), section);
    enable("rotations", true);
    service.deliver(1L);
    assertThat(posted()).doesNotContain("Smoke of Deceit");

    Mockito.clearInvocations(discord, postedRepo);
    enable("smoke", true);
    service.deliver(1L);
    assertThat(posted()).contains("Smoke of Deceit").contains("paid off");
  }

  @Test
  void deliver_theObjectivesSectionPostsNothingUntilItsPropertyIsTrueAndIsIndependentOfSmoke() {
    ObjectiveConversionRepo objectives = Mockito.mock(ObjectiveConversionRepo.class);
    when(objectives.findByMatchId(1L))
        .thenReturn(
            List.of(
                ObjectiveConversionDomain.builder()
                    .id(1L)
                    .fightIndex(0)
                    .winnerTeam("RADIANT")
                    .result("MISSED")
                    .build()));
    ObjectivesSection section =
        new ObjectivesSection(
            objectives,
            Mockito.mock(FightRepo.class),
            Mockito.mock(FightImportanceRepo.class),
            Mockito.mock(ReportHeroNames.class),
            properties);
    service = serviceWith(named("alpha", true), section);
    enable("smoke", true);
    service.deliver(1L);
    assertThat(posted()).doesNotContain("Won fights turned into objectives");

    Mockito.clearInvocations(discord, postedRepo);
    enable("objectives", true);
    service.deliver(1L);
    assertThat(posted()).contains("Won fights turned into objectives: 0 of 1");
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

  private void storedRun(ReplayStatus status) {
    when(runRepo.findByMatchIdAndParserVersion(any(), any()))
        .thenReturn(Optional.of(ReplayParseRunDomain.builder().matchId(1L).status(status).build()));
    when(instanceRepo.findById("i1")).thenReturn(Optional.of(instance(null)));
  }

  @Test
  void redeliver_postsAgainWhereDeliverAlreadyPostedOnce() {
    storedRun(ReplayStatus.DONE);

    service.deliver(1L);
    when(postedRepo.findByMatchIdAndInstanceId(1L, "i1"))
        .thenReturn(Optional.of(ReplayReportPostedDomain.builder().id(3L).messages(1).build()));
    service.deliver(1L);
    verify(discord, Mockito.times(1)).sendThreadMessageBlocking(any(), any());

    assertThat(service.redeliver(1L, "i1")).isEqualTo(ReportDelivery.POSTED);

    verify(discord, Mockito.times(2)).sendThreadMessageBlocking(any(), any());
    ArgumentCaptor<ReplayReportPostedDomain> saved =
        ArgumentCaptor.forClass(ReplayReportPostedDomain.class);
    verify(postedRepo, Mockito.times(2)).save(saved.capture());
    assertThat(saved.getAllValues().get(1).getId()).isEqualTo(3L);
  }

  @Test
  void deliverSection_postsOnlyThatSectionEvenWhenItIsOffAndTheReportIsOff() {
    storedRun(ReplayStatus.DONE);
    properties.setEnabled(false);
    service = serviceWith(named("alpha", true), named("beta", false));

    assertThat(service.deliverSection(1L, "i1", "beta")).isEqualTo(ReportDelivery.POSTED);

    String text = posted();
    assertThat(text).contains("line of beta").doesNotContain("line of alpha");
    verify(postedRepo, never()).save(any());
  }

  @Test
  void deliverSection_componentsFormatPostsTheCard() {
    storedRun(ReplayStatus.DONE);
    properties.setFormat(ReportFormat.COMPONENTS);
    service = serviceWith(named("beta", false));

    assertThat(service.deliverSection(1L, "i1", "beta")).isEqualTo(ReportDelivery.POSTED);

    verify(discord).sendThreadComponentsBlocking(Mockito.eq(thread), any());
  }

  @Test
  void deliverSection_saysWhyNothingWasPosted() {
    storedRun(ReplayStatus.DONE);
    service = serviceWith(named("alpha", true), emptySection("quiet"));

    assertThat(service.deliverSection(1L, "i1", "quiet")).isEqualTo(ReportDelivery.SECTION_EMPTY);
    assertThat(service.deliverSection(1L, "i1", "missing")).isEqualTo(ReportDelivery.NO_INSTANCE);
    when(threadRepo.findByMatchIdAndInstanceId(1L, "i1")).thenReturn(Optional.empty());
    assertThat(service.deliverSection(1L, "i1", "alpha")).isEqualTo(ReportDelivery.NO_THREAD);
    when(runRepo.findByMatchIdAndParserVersion(any(), any())).thenReturn(Optional.empty());
    assertThat(service.deliverSection(1L, "i1", "alpha")).isEqualTo(ReportDelivery.NOT_STORED);

    verify(discord, never()).sendThreadMessageBlocking(any(), any());
  }

  private static ReplayReportSection emptySection(String name) {
    return new ReplaySection() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public List<String> lines(ReplayReportInput input) {
        return List.of();
      }
    };
  }

  @Test
  void redeliver_ignoresQuietHoursButHonoursTheOtherGates() {
    storedRun(ReplayStatus.PARTIAL);
    when(quiet.isQuietTime(any())).thenReturn(true);

    service.redeliver(1L, "i1");
    verify(discord, Mockito.times(1)).sendThreadMessageBlocking(any(), any());

    when(instanceRepo.findById("i1"))
        .thenReturn(
            Optional.of(
                instance(InstanceSettings.builder().reportDetail(ReportDetail.MINIMAL).build())));
    assertThat(service.redeliver(1L, "i1")).isEqualTo(ReportDelivery.MINIMAL_DETAIL);
    properties.setEnabled(false);
    when(instanceRepo.findById("i1")).thenReturn(Optional.of(instance(null)));
    assertThat(service.redeliver(1L, "i1")).isEqualTo(ReportDelivery.REPORT_SWITCHED_OFF);

    verify(discord, Mockito.times(1)).sendThreadMessageBlocking(any(), any());
  }

  @Test
  void redeliver_saysWhyNothingWasPosted() {
    storedRun(ReplayStatus.DONE);
    when(threadRepo.findByMatchIdAndInstanceId(1L, "i1")).thenReturn(Optional.empty());
    assertThat(service.redeliver(1L, "i1")).isEqualTo(ReportDelivery.NO_THREAD);

    when(threadRepo.findByMatchIdAndInstanceId(1L, "i1"))
        .thenReturn(
            Optional.of(
                MatchReportThreadDomain.builder()
                    .matchId(1L)
                    .instanceId("i1")
                    .threadId(9L)
                    .build()));
    when(discord.getThreadById(9L)).thenReturn(null);
    assertThat(service.redeliver(1L, "i1")).isEqualTo(ReportDelivery.THREAD_NOT_FOUND);

    when(discord.getThreadById(9L)).thenReturn(thread);
    service = serviceWith(named("off", false));
    assertThat(service.redeliver(1L, "i1")).isEqualTo(ReportDelivery.NO_SECTIONS);
  }

  @Test
  void redeliver_postsNothingWithoutAStoredReplayAndNeverThrows() {
    storedRun(ReplayStatus.FAILED_DOWNLOAD);
    assertThat(service.redeliver(1L, "i1")).isEqualTo(ReportDelivery.NOT_STORED);

    storedRun(ReplayStatus.DONE);
    when(instanceRepo.findById("i1")).thenReturn(Optional.empty());
    assertThat(service.redeliver(1L, "i1")).isEqualTo(ReportDelivery.NO_INSTANCE);

    storedRun(ReplayStatus.DONE);
    when(discord.getThreadById(9L)).thenThrow(new IllegalStateException("discord down"));

    assertThat(service.redeliver(1L, "i1")).isEqualTo(ReportDelivery.FAILED);
    verify(discord, never()).sendThreadMessageBlocking(any(), any());
  }

  @Test
  void deliver_triesAgainLaterWhenTheThreadCannotBeFoundNow() {
    when(discord.getThreadById(9L)).thenReturn(null);

    service.deliver(1L);

    verify(discord, never()).sendThreadMessageBlocking(any(), any());
    verify(postedRepo, never()).save(any());
  }

  @Test
  void deliver_countsTheReportOnlyWhenItIsPosted() {
    service.deliver(1L);
    when(postedRepo.findByMatchIdAndInstanceId(1L, "i1"))
        .thenReturn(Optional.of(ReplayReportPostedDomain.builder().build()));
    service.deliver(1L);

    assertThat(registry.get("dbuff.replay.report.posted").counter().count()).isEqualTo(1);
    assertThat(registry.find("dbuff.replay.report.skipped").counters()).isEmpty();
  }

  @Test
  void format_isPlainByDefault() {
    assertThat(new ReplayReportProperties().getFormat()).isEqualTo(ReportFormat.PLAIN);
    new ReplayReportProperties().validate();
  }

  @Test
  void validate_rejectsANullFormat() {
    ReplayReportProperties bad = new ReplayReportProperties();
    bad.setFormat(null);

    assertThatThrownBy(bad::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("replay.report.format");
  }

  @Test
  void format_bindsFromTheYamlForm() {
    ReplayReportProperties bound =
        new org.springframework.boot.context.properties.bind.Binder(
                new org.springframework.boot.context.properties.source
                    .MapConfigurationPropertySource(
                    java.util.Map.of("replay.report.format", "components")))
            .bindOrCreate("replay.report", ReplayReportProperties.class);

    assertThat(bound.getFormat()).isEqualTo(ReportFormat.COMPONENTS);
  }

  @Test
  void deliver_plainFormatPostsExactlyTheFormattedTextOfTheSectionLines() {
    service = serviceWith(named("alpha", true), named("beta", true));

    service.deliver(1L);

    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(discord, Mockito.times(2)).sendThreadMessageBlocking(Mockito.eq(thread), text.capture());
    assertThat(text.getAllValues())
        .containsExactly("**alpha**\n10:00: line of alpha", "**beta**\n10:00: line of beta");
    verify(discord, never()).sendThreadComponentsBlocking(any(), any());
  }

  @Test
  void deliver_plainFormatCapsLongReportsWithTheLeftOutNote() {
    properties.setMaxMessages(1);
    service = serviceWith(longSection("alpha"), longSection("beta"), longSection("gamma"));

    service.deliver(1L);

    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(discord, Mockito.times(1)).sendThreadMessageBlocking(Mockito.eq(thread), text.capture());
    assertThat(text.getValue()).contains("more left out").hasSizeLessThanOrEqualTo(2000);
  }

  private static ReplayReportSection longSection(String name) {
    return new ReplaySection() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public int order() {
        return name.charAt(0);
      }

      @Override
      public List<String> lines(ReplayReportInput input) {
        return java.util.stream.IntStream.range(0, 12)
            .mapToObj(i -> name + "x".repeat(150))
            .toList();
      }
    };
  }

  @Test
  void deliver_componentsFormatPacksTheSectionCardsIntoOneMessageInOrder() {
    properties.setFormat(ReportFormat.COMPONENTS);
    service = serviceWith(named("alpha", true), named("beta", true));

    service.deliver(1L);

    ArgumentCaptor<List<MessageTopLevelComponent>> cards = ArgumentCaptor.forClass(List.class);
    verify(discord).sendThreadComponentsBlocking(Mockito.eq(thread), cards.capture());
    verify(discord, never()).sendThreadMessageBlocking(any(), any());
    List<MessageTopLevelComponent> message = cards.getValue();
    assertThat(message).hasSize(2).allMatch(c -> c instanceof Container);
    assertThat(((Container) message.get(0)).getAccentColorRaw()).isEqualTo(Accent.INFO.rgb());
    ArgumentCaptor<ReplayReportPostedDomain> posted =
        ArgumentCaptor.forClass(ReplayReportPostedDomain.class);
    verify(postedRepo).save(posted.capture());
    assertThat(posted.getValue().getMessages()).isEqualTo(1);
  }

  private static ReplayReportSection bulky(String name) {
    return new ReplaySection() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public int order() {
        return name.charAt(0);
      }

      @Override
      public List<String> lines(ReplayReportInput input) {
        return java.util.stream.IntStream.range(0, 3).mapToObj(i -> "x".repeat(900)).toList();
      }
    };
  }

  @Test
  void deliver_componentsFormatCapsMessagesNotSectionsAndAddsTheLeftOutNote() {
    properties.setFormat(ReportFormat.COMPONENTS);
    properties.setMaxMessages(2);
    service = serviceWith(bulky("a"), bulky("b"), bulky("c"), bulky("d"));

    service.deliver(1L);

    ArgumentCaptor<List<MessageTopLevelComponent>> cards = ArgumentCaptor.forClass(List.class);
    verify(discord, Mockito.times(2))
        .sendThreadComponentsBlocking(Mockito.eq(thread), cards.capture());
    List<MessageTopLevelComponent> last = cards.getAllValues().get(1);
    assertThat(((TextDisplay) last.get(last.size() - 1)).getContent())
        .isEqualTo("_(2 more left out)_");
  }

  private static ErrorResponseException rejected(ErrorResponse code) {
    ErrorResponseException e = mock(ErrorResponseException.class);
    when(e.getErrorResponse()).thenReturn(code);
    return e;
  }

  @Test
  void deliver_otherFailuresOfAComponentsMessageAreNotRetriedAsPlainText() {
    properties.setFormat(ReportFormat.COMPONENTS);
    service = serviceWith(bulky("a"));
    Mockito.doThrow(new java.io.UncheckedIOException(new java.io.IOException("timeout")))
        .when(discord)
        .sendThreadComponentsBlocking(any(), any());
    service.deliver(1L);
    ErrorResponseException denied = rejected(ErrorResponse.MISSING_PERMISSIONS);
    Mockito.doThrow(denied).when(discord).sendThreadComponentsBlocking(any(), any());
    service.deliver(1L);

    verify(discord, never()).sendThreadMessageBlocking(any(), any());
  }

  @Test
  void deliver_aPayloadJdaRejectsLocallyAlsoFallsBackToPlainText() {
    properties.setFormat(ReportFormat.COMPONENTS);
    service = serviceWith(bulky("a"));
    Mockito.doThrow(new IllegalArgumentException("too long"))
        .when(discord)
        .sendThreadComponentsBlocking(any(), any());
    service.deliver(1L);
    Mockito.doThrow(new IllegalStateException("too many"))
        .when(discord)
        .sendThreadComponentsBlocking(any(), any());
    service.deliver(1L);

    verify(discord, Mockito.atLeast(2)).sendThreadMessageBlocking(Mockito.eq(thread), any());
  }

  @Test
  void deliver_aRejectedComponentsMessageFallsBackToItsPlainTextAndTheRestIsStillPosted() {
    properties.setFormat(ReportFormat.COMPONENTS);
    properties.setMaxMessages(4);
    service = serviceWith(bulky("a"), bulky("b"), bulky("c"));
    ErrorResponseException invalid = rejected(ErrorResponse.INVALID_FORM_BODY);
    when(discord.sendThreadComponentsBlocking(any(), any())).thenThrow(invalid).thenReturn(null);

    service.deliver(1L);

    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(discord, Mockito.atLeastOnce())
        .sendThreadMessageBlocking(Mockito.eq(thread), text.capture());
    assertThat(String.join("\n", text.getAllValues())).contains("x".repeat(900));
    text.getAllValues().forEach(t -> assertThat(t.length()).isLessThanOrEqualTo(2000));
    verify(discord, Mockito.atLeast(2)).sendThreadComponentsBlocking(any(), any());
    verify(postedRepo).save(any());
  }
}
