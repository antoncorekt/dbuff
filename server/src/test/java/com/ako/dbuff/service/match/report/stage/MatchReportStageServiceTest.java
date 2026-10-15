package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MatchReportThreadRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.discord.icon.TestIcons;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
  private final ReplayStageTargets targets = mock(ReplayStageTargets.class);
  private final MatchRepo matchRepo = mock(MatchRepo.class);
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
            new MatchFactsFactory(constants, statRepo, mock(DbufInstanceConfigRepository.class)),
            new HeaderRenderer(),
            new ReportHeaderPublisher(discord),
            new StageOneCardBuilder(constants, TestIcons.off()),
            new ComponentsViewRenderer(new PlainViewRenderer()),
            discord,
            new MatchThreadRecorder(threadRepo),
            forecast,
            targets,
            matchRepo);
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
    when(threadRepo.advanceStage(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
  }

  private void verifyAdvanced(MatchReportStage stage) {
    verify(threadRepo)
        .advanceStage(
            eq(9L),
            eq(stage),
            org.mockito.ArgumentMatchers.anyCollection(),
            any(),
            any(),
            any(),
            any());
  }

  private void verifyNotAdvanced() {
    verify(threadRepo, never()).advanceStage(any(), any(), any(), any(), any(), any(), any());
  }

  private static NewMatchSummary summary() {
    PlayerLine line = new PlayerLine(1L, 0L, 14L, 10L, 2L, 5L, 512L, 640L, true);
    return new NewMatchSummary(2100L, 22L, List.of(line));
  }

  private static MatchReportThreadDomain row(MatchReportStage stage) {
    return MatchReportThreadDomain.builder()
        .id(9L)
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

  private static MatchDomain timedOutMatch() {
    return MatchDomain.builder().id(MATCH).build();
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
    ArgumentCaptor<MatchReportThreadDomain> saved =
        ArgumentCaptor.forClass(MatchReportThreadDomain.class);
    verify(threadRepo).save(saved.capture());
    assertThat(saved.getValue().getStage()).isNull();
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
    verifyAdvanced(MatchReportStage.REPLAY_PENDING);
    verify(discord, never()).sendThreadComponentsBlocking(any(), anyList());
  }

  @Test
  void stageTwo_saysDataReadyWhenNoReplayIsExpected() {
    statsOfTrackedPlayer();
    MatchReportThreadDomain row = row(MatchReportStage.REQUESTED);
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.of(row));

    service.parsed(parsedMatch(), config);

    assertThat(editedTitle()).contains("OpenDota data ready");
    verifyAdvanced(MatchReportStage.PARSED);
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
    assertThatCode(() -> service.parseTimedOut(timedOutMatch(), INSTANCE))
        .doesNotThrowAnyException();
  }

  @Test
  void aRejectedEditFallsBackToPlainText() {
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE))
        .thenReturn(Optional.of(row(MatchReportStage.REQUESTED)));
    org.mockito.Mockito.doThrow(new IllegalStateException("not v2"))
        .when(discord)
        .editComponentsMessage(anyString(), anyLong(), anyList());

    service.parseTimedOut(timedOutMatch(), INSTANCE);

    verify(discord)
        .editMessage(eq(CHANNEL), eq(500L), org.mockito.ArgumentMatchers.contains("did not parse"));
  }

  @Test
  void timeout_keepsTheStageOneFactsInTheTitle() {
    MatchReportThreadDomain row = row(MatchReportStage.REQUESTED);
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.of(row));

    service.parseTimedOut(timedOutMatch(), INSTANCE);

    assertThat(editedTitle())
        .isEqualTo(
            "**Match 77 · ability draft · Win · 35 min · OpenDota did not parse this match**");
    verifyAdvanced(MatchReportStage.PARSE_TIMED_OUT);
  }

  @Test
  void aLegacyRowIsNeverEditedAsV2_butTheTimeoutIsToldInPlainText() {
    MatchReportThreadDomain legacy = row(null);
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.of(legacy));
    statsOfTrackedPlayer();

    service.parsed(parsedMatch(), config);
    service.parseTimedOut(timedOutMatch(), INSTANCE);

    verify(discord, never()).editComponentsMessage(anyString(), anyLong(), anyList());
    verify(discord)
        .editMessage(eq(CHANNEL), eq(500L), org.mockito.ArgumentMatchers.contains("did not parse"));
    verifyNotAdvanced();
    assertThat(service.headerKind(MATCH, INSTANCE))
        .isEqualTo(MatchReportStageService.HeaderKind.PLAIN);
  }

  @Test
  void aStagedRowKeepsItsStagedEditsEvenAfterTheSwitchIsTurnedOff() {
    properties.setEnabled(false);
    statsOfTrackedPlayer();
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE))
        .thenReturn(Optional.of(row(MatchReportStage.REQUESTED)));

    assertThat(service.headerKind(MATCH, INSTANCE))
        .isEqualTo(MatchReportStageService.HeaderKind.STAGED);
    service.parsed(parsedMatch(), config);

    verifyAdvanced(MatchReportStage.PARSED);
  }

  @Test
  void theStageNeverMovesBackwards() {
    statsOfTrackedPlayer();
    MatchReportThreadDomain done = row(MatchReportStage.REPLAY_DONE);
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.of(done));
    when(forecast.isExpected(any())).thenReturn(true);

    service.parsed(parsedMatch(), config);
    service.parseTimedOut(timedOutMatch(), INSTANCE);

    verify(discord, never()).editComponentsMessage(anyString(), anyLong(), anyList());
    verifyNotAdvanced();
  }

  @Test
  void theStageIsStoredOnlyWhenDiscordShowsTheNewTitle() {
    statsOfTrackedPlayer();
    MatchReportThreadDomain row = row(MatchReportStage.REQUESTED);
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.of(row));
    org.mockito.Mockito.doThrow(new RuntimeException("Unknown Message"))
        .when(discord)
        .editComponentsMessage(anyString(), anyLong(), anyList());

    service.parsed(parsedMatch(), config);

    verifyNotAdvanced();
  }

  @Test
  void theTimeoutTitleKeepsTheScoreOnceKnown() {
    MatchReportThreadDomain row = row(MatchReportStage.REQUESTED);
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.of(row));

    service.parseTimedOut(parsedMatch(), INSTANCE);

    assertThat(editedTitle()).contains("(40-25)");
  }

  @Test
  void stageOne_keepsTheHeaderIdWhenTheThreadCannotBeMade() {
    when(discord.createThread(any(), anyString())).thenThrow(new RuntimeException("no permission"));

    PartialReportResult result =
        service.start(MatchDomain.builder().id(MATCH).build(), config, summary(), Map.of());

    assertThat(result).isEqualTo(new PartialReportResult(0L, 500L));
    ArgumentCaptor<MatchReportThreadDomain> saved =
        ArgumentCaptor.forClass(MatchReportThreadDomain.class);
    verify(threadRepo).save(saved.capture());
    assertThat(saved.getValue().getThreadId()).isNull();
    assertThat(saved.getValue().getHeaderMessageId()).isEqualTo(500L);
    assertThat(saved.getValue().getStage()).isEqualTo(MatchReportStage.REQUESTED);
  }

  @Test
  void stageOne_aFailingCardStillReturnsTheRealThread() {
    when(discord.sendThreadComponentsBlocking(any(), anyList()))
        .thenThrow(new RuntimeException("boom"));
    when(constants.getHeroConstantMap()).thenThrow(new IllegalStateException("down"));

    PartialReportResult result =
        service.start(MatchDomain.builder().id(MATCH).build(), config, summary(), Map.of());

    assertThat(result).isEqualTo(new PartialReportResult(600L, 500L));
  }

  private void matrix(boolean switchOn, MatchReportStage rowStage, boolean timeout) {
    properties.setEnabled(switchOn);
    statsOfTrackedPlayer();
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE))
        .thenReturn(Optional.of(row(rowStage)));
    if (timeout) {
      service.parseTimedOut(timedOutMatch(), INSTANCE);
    } else {
      service.parsed(parsedMatch(), config);
    }
  }

  @ParameterizedTest(name = "switch on={0}, staged={1}, timeout={2}")
  @CsvSource({
    "false, false, false, NOTHING",
    "false, false, true, NOTHING",
    "false, true, false, STAGED",
    "false, true, true, STAGED",
    "true, false, false, NOTHING",
    "true, false, true, PLAIN",
    "true, true, false, STAGED",
    "true, true, true, STAGED"
  })
  void theSwitchAndTheRowDecideWhatIsEdited(
      boolean switchOn, boolean staged, boolean timeout, String expected) {
    matrix(switchOn, staged ? MatchReportStage.REQUESTED : null, timeout);

    boolean v2 = false;
    try {
      verify(discord).editComponentsMessage(anyString(), anyLong(), anyList());
      v2 = true;
    } catch (AssertionError e) {
      verify(discord, never()).editComponentsMessage(anyString(), anyLong(), anyList());
    }
    boolean plain = false;
    try {
      verify(discord).editMessage(anyString(), anyLong(), anyString());
      plain = true;
    } catch (AssertionError e) {
      verify(discord, never()).editMessage(anyString(), anyLong(), anyString());
    }
    assertThat(v2 ? "STAGED" : plain ? "PLAIN" : "NOTHING").isEqualTo(expected);
  }

  @Test
  void aFailedRowLookupLeavesTheHeaderAlone() {
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE))
        .thenThrow(new RuntimeException("db down"));

    assertThat(service.headerKind(MATCH, INSTANCE))
        .isEqualTo(MatchReportStageService.HeaderKind.UNKNOWN);
  }

  @Test
  void stageTwo_withoutAStagedRowReadsNothingElse() {
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.empty());

    service.parsed(parsedMatch(), config);

    org.mockito.Mockito.verifyNoInteractions(forecast, statRepo, discord);
  }

  @Test
  void stageTwo_aParseWithoutTheTrackedPlayersKeepsTheKnownResult() {
    when(statRepo.findAllByMatchId(MATCH)).thenReturn(List.of());
    MatchReportThreadDomain row = row(MatchReportStage.REQUESTED);
    when(threadRepo.findByMatchIdAndInstanceId(MATCH, INSTANCE)).thenReturn(Optional.of(row));

    service.parsed(parsedMatch(), config);

    assertThat(editedTitle()).contains("Win").doesNotContain("Unknown");
    verify(threadRepo)
        .advanceStage(
            eq(9L),
            eq(MatchReportStage.PARSED),
            org.mockito.ArgumentMatchers.anyCollection(),
            eq("ability draft"),
            eq(1),
            eq(0),
            eq(2100L));
  }
}
