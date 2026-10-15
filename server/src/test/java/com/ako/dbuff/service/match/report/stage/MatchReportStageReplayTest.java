package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MatchReportThreadRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.discord.icon.TestIcons;
import com.ako.dbuff.service.replay.ReplayForecast;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import com.ako.dbuff.service.replay.report.view.ComponentsViewRenderer;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchReportStageReplayTest {
  private static final long MATCH = 77L;
  private static final String CHANNEL = "channel-1";

  private final ReportStagesProperties properties = new ReportStagesProperties();
  private final DiscordMessageService discord = mock(DiscordMessageService.class);
  private final MatchReportThreadRepo threadRepo = mock(MatchReportThreadRepo.class);
  private final PlayerGameStatisticRepo statRepo = mock(PlayerGameStatisticRepo.class);
  private final ReplayForecast forecast = mock(ReplayForecast.class);
  private final ReplayParseRunRepo runRepo = mock(ReplayParseRunRepo.class);
  private final MatchRepo matchRepo = mock(MatchRepo.class);
  private final ReplayProcessingProperties processing = new ReplayProcessingProperties();
  private MatchReportStageService service;
  private DbufInstanceConfigDomain instance;

  @BeforeEach
  void setUp() {
    properties.setEnabled(true);
    service =
        new MatchReportStageService(
            properties,
            new MatchFactsFactory(
                mock(ConstantsManagers.class), statRepo, mock(DbufInstanceConfigRepository.class)),
            new HeaderRenderer(),
            new ReportHeaderPublisher(discord),
            new StageOneCardBuilder(
                new ReportHeroNames(
                    mock(PlayerGameStatisticRepo.class),
                    mock(ReplayPlayerRepo.class),
                    mock(HeroKits.class),
                    TestIcons.off())),
            new ComponentsViewRenderer(new PlainViewRenderer()),
            discord,
            new MatchThreadRecorder(threadRepo),
            forecast,
            new ReplayStageTargets(runRepo, processing, forecast),
            matchRepo);
    instance = DbufInstanceConfigDomain.builder().id("i1").discordChannelId(CHANNEL).build();
    when(matchRepo.findById(MATCH)).thenReturn(Optional.of(match()));
    when(threadRepo.advanceStage(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(threadRepo.forceStage(any(), any(), any(), any(), any(), any())).thenReturn(1);
    PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
    stat.setPlayerId(1L);
    stat.setWin(1L);
    when(statRepo.findAllByMatchId(MATCH)).thenReturn(List.of(stat));
    instance.setPlayers(null);
  }

  private static MatchDomain match() {
    return MatchDomain.builder()
        .id(MATCH)
        .gameModeName("game_mode_ability_draft")
        .radiantScore(40L)
        .direScore(25L)
        .duration(2100L)
        .detailsMappedAt(LocalDateTime.now())
        .build();
  }

  private static MatchReportThreadDomain row(long id, String instanceId, MatchReportStage stage) {
    return MatchReportThreadDomain.builder()
        .id(id)
        .matchId(MATCH)
        .instanceId(instanceId)
        .channelId(CHANNEL)
        .threadId(600L)
        .headerMessageId(500L + id)
        .stage(stage)
        .wins(1)
        .losses(0)
        .durationSeconds(2100L)
        .modeName("ability draft")
        .build();
  }

  private void rows(MatchReportThreadDomain... rows) {
    when(threadRepo.findAllByMatchId(MATCH)).thenReturn(List.of(rows));
    for (MatchReportThreadDomain row : rows) {
      when(threadRepo.findByMatchIdAndInstanceId(MATCH, row.getInstanceId()))
          .thenReturn(Optional.of(row));
    }
  }

  private void run(ReplayStatus status, LocalDateTime nextAttemptAt) {
    when(runRepo.findByMatchIdAndParserVersion(MATCH, processing.getParserVersion()))
        .thenReturn(
            Optional.of(
                ReplayParseRunDomain.builder()
                    .matchId(MATCH)
                    .status(status)
                    .nextAttemptAt(nextAttemptAt)
                    .build()));
  }

  @SuppressWarnings("unchecked")
  private List<String> titles(long messageId, int times) {
    ArgumentCaptor<List<MessageTopLevelComponent>> captor = ArgumentCaptor.forClass(List.class);
    verify(discord, times(times))
        .editComponentsMessage(eq(CHANNEL), eq(messageId), captor.capture());
    return captor.getAllValues().stream()
        .map(c -> ((TextDisplay) ((Container) c.get(0)).getComponents().get(0)).getContent())
        .toList();
  }

  @ParameterizedTest
  @EnumSource(ReplayStatus.class)
  void replayFinished_retitlesOnlyForAFinalState(ReplayStatus status) {
    rows(row(1, "i1", MatchReportStage.REPLAY_PENDING));

    service.replayFinished(MATCH, status, true);

    boolean titled = status == ReplayStatus.DONE || status.isPermanent();
    verify(discord, times(titled ? 1 : 0)).editComponentsMessage(anyString(), anyLong(), anyList());
  }

  @Test
  void replayFinished_wordsDonePartialAndUnavailable_keepingResultScoreModeAndDuration() {
    rows(row(1, "i1", MatchReportStage.REPLAY_PENDING));

    service.replayFinished(MATCH, ReplayStatus.DONE, false);
    service.replayFinished(MATCH, ReplayStatus.PARTIAL, false);
    service.replayFinished(MATCH, ReplayStatus.EXPIRED, false);

    assertThat(titles(501L, 3))
        .containsExactly(
            "**Match 77 · ability draft · Win (40-25) · 35 min · replay analysis ready**",
            "**Match 77 · ability draft · Win (40-25) · 35 min · replay analysis ready (partial)**",
            "**Match 77 · ability draft · Win (40-25) · 35 min · replay analysis not available**");
  }

  @Test
  void replayFinished_aPartialRunWithARetryLeftChangesNothing() {
    rows(row(1, "i1", MatchReportStage.REPLAY_PENDING));

    service.replayFinished(MATCH, ReplayStatus.PARTIAL, true);
    service.replayFinished(MATCH, ReplayStatus.FAILED_DOWNLOAD, true);

    verify(discord, never()).editComponentsMessage(anyString(), anyLong(), anyList());
  }

  @Test
  void replayFinished_retitlesEveryThreadOfTheMatchAndAdvancesEachRow() {
    rows(row(1, "i1", MatchReportStage.REPLAY_PENDING), row(2, "i2", MatchReportStage.PARSED));

    service.replayFinished(MATCH, ReplayStatus.DONE, false);

    verify(discord).editComponentsMessage(eq(CHANNEL), eq(501L), anyList());
    verify(discord).editComponentsMessage(eq(CHANNEL), eq(502L), anyList());
    verify(threadRepo)
        .advanceStage(
            eq(1L), eq(MatchReportStage.REPLAY_DONE), anyCollection(), any(), any(), any(), any());
    verify(threadRepo)
        .advanceStage(
            eq(2L), eq(MatchReportStage.REPLAY_DONE), anyCollection(), any(), any(), any(), any());
  }

  @Test
  void replayFinished_isForwardOnlyAndLeavesAFinishedHeaderAlone() {
    rows(row(1, "i1", MatchReportStage.REPLAY_DONE));

    service.replayFinished(MATCH, ReplayStatus.EXPIRED, false);

    verify(discord, never()).editComponentsMessage(anyString(), anyLong(), anyList());
  }

  @Test
  void replayFinished_aPlainRowIsNeverEdited() {
    rows(row(1, "i1", null));

    service.replayFinished(MATCH, ReplayStatus.DONE, false);

    verify(discord, never()).editMessage(anyString(), anyLong(), anyString());
    verify(discord, never()).editComponentsMessage(anyString(), anyLong(), anyList());
    verify(threadRepo, never()).advanceStage(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void replayFinished_aDoneRunMayFollowAnUnavailableHeader() {
    rows(row(1, "i1", MatchReportStage.REPLAY_UNAVAILABLE));

    service.replayFinished(MATCH, ReplayStatus.DONE, false);

    verify(discord).editComponentsMessage(eq(CHANNEL), eq(501L), anyList());
  }

  @Test
  void replayFinished_aMatchWithoutRowsAndASwitchedOffServiceDoNothing() {
    when(threadRepo.findAllByMatchId(MATCH)).thenReturn(List.of());
    service.replayFinished(MATCH, ReplayStatus.DONE, false);

    properties.setEnabled(false);
    rows(row(1, "i1", MatchReportStage.REPLAY_PENDING));
    service.replayFinished(MATCH, ReplayStatus.DONE, false);

    verify(discord, never()).editComponentsMessage(anyString(), anyLong(), anyList());
    verify(discord, never()).editMessage(anyString(), anyLong(), anyString());
  }

  @Test
  void replayFinished_neverThrowsAndOneFailingRowDoesNotStopTheNext() {
    rows(
        row(1, "i1", MatchReportStage.REPLAY_PENDING),
        row(2, "i2", MatchReportStage.REPLAY_PENDING));
    doThrow(new RuntimeException("boom"))
        .when(discord)
        .editComponentsMessage(eq(CHANNEL), eq(501L), anyList());

    assertThatCode(() -> service.replayFinished(MATCH, ReplayStatus.DONE, false))
        .doesNotThrowAnyException();
    verify(discord).editComponentsMessage(eq(CHANNEL), eq(502L), anyList());

    when(threadRepo.findAllByMatchId(MATCH)).thenThrow(new RuntimeException("db down"));
    assertThatCode(() -> service.replayFinished(MATCH, ReplayStatus.DONE, false))
        .doesNotThrowAnyException();
  }

  private void verifyForced(MatchReportStage stage) {
    verify(threadRepo).forceStage(eq(1L), eq(stage), any(), any(), any(), any());
    verify(threadRepo, never()).advanceStage(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void refresh_aDoneRunSetsReplayDoneEvenAfterAHigherStage() {
    rows(row(1, "i1", MatchReportStage.REPLAY_UNAVAILABLE));
    run(ReplayStatus.DONE, null);

    assertThat(service.refresh(match(), instance)).isTrue();

    assertThat(titles(501L, 1).get(0)).endsWith("replay analysis ready**");
    verifyForced(MatchReportStage.REPLAY_DONE);
  }

  @Test
  void refresh_aPartialRunWithNoRetryIsPartialAndAPermanentFailureIsUnavailable() {
    rows(row(1, "i1", MatchReportStage.REPLAY_DONE));
    run(ReplayStatus.PARTIAL, null);
    service.refresh(match(), instance);
    run(ReplayStatus.FAILED_PARSER, null);
    service.refresh(match(), instance);

    assertThat(titles(501L, 2))
        .satisfiesExactly(
            first -> assertThat(first).endsWith("replay analysis ready (partial)**"),
            second -> assertThat(second).endsWith("replay analysis not available**"));
  }

  @Test
  void refresh_withoutARunTheForecastDecidesBetweenPendingAndParsed() {
    rows(row(1, "i1", MatchReportStage.REPLAY_DONE));
    when(runRepo.findByMatchIdAndParserVersion(anyLong(), any())).thenReturn(Optional.empty());
    when(forecast.isExpected(any())).thenReturn(true);
    service.refresh(match(), instance);
    when(forecast.isExpected(any())).thenReturn(false);
    service.refresh(match(), instance);

    assertThat(titles(501L, 2))
        .satisfiesExactly(
            first -> assertThat(first).endsWith("replay processing**"),
            second -> assertThat(second).endsWith("OpenDota data ready**"));
  }

  @Test
  void refresh_aRunningOrRetryableRunIsPendingWhateverTheForecastSays() {
    rows(row(1, "i1", MatchReportStage.REPLAY_DONE));
    when(forecast.isExpected(any())).thenReturn(false);
    run(ReplayStatus.RUNNING, null);
    service.refresh(match(), instance);
    run(ReplayStatus.PARTIAL, LocalDateTime.now().plusHours(1));
    service.refresh(match(), instance);

    assertThat(titles(501L, 2)).allSatisfy(t -> assertThat(t).endsWith("replay processing**"));
  }

  @Test
  void refresh_noRunAndAnExpectedReplayIsPending() {
    rows(row(1, "i1", MatchReportStage.PARSED));
    when(runRepo.findByMatchIdAndParserVersion(anyLong(), any())).thenReturn(Optional.empty());
    when(forecast.isExpected(any())).thenReturn(true);

    assertThat(service.refresh(match(), instance)).isTrue();

    verifyForced(MatchReportStage.REPLAY_PENDING);
  }

  @Test
  void refresh_aMatchThatIsNotMappedLeavesTheHeaderAlone() {
    rows(row(1, "i1", MatchReportStage.REQUESTED));
    when(runRepo.findByMatchIdAndParserVersion(anyLong(), any())).thenReturn(Optional.empty());
    MatchDomain unmapped = MatchDomain.builder().id(MATCH).build();

    assertThat(service.refresh(unmapped, instance)).isFalse();

    verify(discord, never()).editComponentsMessage(anyString(), anyLong(), anyList());
  }

  @Test
  void refresh_aPlainOrMissingRowOrASwitchedOffServiceDoesNothing() {
    run(ReplayStatus.DONE, null);
    rows(row(1, "i1", null));
    assertThat(service.refresh(match(), instance)).isFalse();

    when(threadRepo.findByMatchIdAndInstanceId(MATCH, "i1")).thenReturn(Optional.empty());
    assertThat(service.refresh(match(), instance)).isFalse();

    properties.setEnabled(false);
    rows(row(1, "i1", MatchReportStage.REQUESTED));
    assertThat(service.refresh(match(), instance)).isFalse();

    verify(discord, never()).editComponentsMessage(anyString(), anyLong(), anyList());
    verify(discord, never()).editMessage(anyString(), anyLong(), anyString());
  }

  @Test
  void refresh_aFailedEditChangesNoStageAndNeverThrows() {
    rows(row(1, "i1", MatchReportStage.REPLAY_PENDING));
    run(ReplayStatus.DONE, null);
    doThrow(new RuntimeException("Unknown Message"))
        .when(discord)
        .editComponentsMessage(anyString(), anyLong(), anyList());

    assertThat(service.refresh(match(), instance)).isFalse();

    verify(threadRepo, never()).forceStage(any(), any(), any(), any(), any(), any());
  }
}
