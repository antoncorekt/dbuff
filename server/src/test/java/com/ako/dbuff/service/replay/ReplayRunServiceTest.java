package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisService;
import com.ako.dbuff.service.replay.file.ReplayFile;
import com.ako.dbuff.service.replay.file.ReplayFileProvider;
import com.ako.dbuff.service.replay.map.MapLayouts;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayRunServiceTest {

  private final Map<Long, ReplayParseRunDomain> runs = new HashMap<>();
  private final ReplayParseRunRepo runRepo = mock(ReplayParseRunRepo.class);
  private final PlayerGameStatisticRepo statRepo = mock(PlayerGameStatisticRepo.class);
  private final ReplayFileProvider fileProvider = mock(ReplayFileProvider.class);
  private final ReplayParser parser = mock(ReplayParser.class);
  private final ReplayRosterVerifier verifier = mock(ReplayRosterVerifier.class);
  private final ReplayExtractor goodExtractor = mock(ReplayExtractor.class);
  private final ReplayExtractor brokenExtractor = mock(ReplayExtractor.class);
  private final Semaphore semaphore = new Semaphore(1);
  private final ReplayProcessingProperties properties = new ReplayProcessingProperties();
  private final MapLayouts mapLayouts = mock(MapLayouts.class);
  private final ReplayAnalysisService analysis = mock(ReplayAnalysisService.class);
  private ReplayRunService service;

  private final ReplayFacts facts =
      ReplayFacts.builder().clockStartTick(17136).gameStartTime(596.5).players(List.of()).build();

  @BeforeEach
  void setUp() {
    when(runRepo.findByMatchIdAndParserVersion(any(), any()))
        .thenAnswer(i -> Optional.ofNullable(runs.get(i.<Long>getArgument(0))));
    when(runRepo.save(any(ReplayParseRunDomain.class)))
        .thenAnswer(
            i -> {
              ReplayParseRunDomain run = i.getArgument(0);
              runs.put(run.getMatchId(), run);
              return run;
            });
    when(statRepo.findAllByMatchId(anyLong())).thenReturn(List.<PlayerMatchStatisticDomain>of());
    when(analysis.analyze(anyLong())).thenAnswer(i -> new java.util.ArrayList<String>());
    when(goodExtractor.name()).thenReturn("good");
    when(brokenExtractor.name()).thenReturn("broken");
    when(parser.version()).thenReturn("5.0.1");
    when(fileProvider.open(any())).thenReturn(new ReplayFile(Path.of("/nonexistent.dem"), 1234));
    when(parser.parse(any(), any())).thenReturn(facts);
    service = serviceWith(List.of(goodExtractor));
  }

  @Test
  void process_recordsADoneRunAndRunsEveryExtractor() {
    ReplayStatus status = service.process(match());

    assertThat(status).isEqualTo(ReplayStatus.DONE);
    ReplayParseRunDomain run = runs.get(1L);
    assertThat(run.getStatus()).isEqualTo(ReplayStatus.DONE);
    assertThat(run.getReplayBytes()).isEqualTo(1234);
    assertThat(run.getClockStartTick()).isEqualTo(17136);
    assertThat(run.getClarityVersion()).isEqualTo("5.0.1");
    assertThat(run.getLeaseUntil()).isNull();
    verify(goodExtractor).extract(any(), any());
  }

  @Test
  void process_marksTheRunPartialWhenOneExtractorFailsAndStillRunsTheRest() {
    doThrow(new IllegalStateException("boom")).when(brokenExtractor).extract(any(), any());
    service = serviceWith(List.of(brokenExtractor, goodExtractor));

    assertThat(service.process(match())).isEqualTo(ReplayStatus.PARTIAL);

    assertThat(runs.get(1L).getError()).contains("broken");
    assertThat(runs.get(1L).getNextAttemptAt()).isNotNull();
    verify(goodExtractor).extract(any(), any());
  }

  @Test
  void process_triesAPartialRunAgainOnlyOnce_becauseTheCauseIsOftenPermanent() {
    doThrow(new IllegalStateException("mapping bug")).when(brokenExtractor).extract(any(), any());
    service = serviceWith(List.of(brokenExtractor));

    service.process(match());
    assertThat(runs.get(1L).getNextAttemptAt()).isNotNull();
    service.process(match());

    assertThat(runs.get(1L).getAttempts()).isEqualTo(2);
    assertThat(runs.get(1L).getNextAttemptAt()).isNull();
  }

  @Test
  void process_marksTheRunPartialWhenAnAnalyzerFails_andRunsTheAnalyzersOnlyAfterTheExtractors() {
    when(analysis.analyze(1L)).thenReturn(new java.util.ArrayList<>(List.of("activity")));

    assertThat(service.process(match())).isEqualTo(ReplayStatus.PARTIAL);

    assertThat(runs.get(1L).getError()).contains("activity");
    org.mockito.InOrder order = org.mockito.Mockito.inOrder(goodExtractor, analysis);
    order.verify(goodExtractor).extract(any(), any());
    order.verify(analysis).analyze(1L);
  }

  @Test
  void process_skipsAnIneligibleMatchWithoutTouchingTheNetwork() {
    MatchDomain noLocation = match();
    noLocation.setReplaySalt(null);

    assertThat(service.process(noLocation)).isEqualTo(ReplayStatus.SKIPPED_NO_REPLAY_INFO);

    verify(fileProvider, never()).open(any());
  }

  @Test
  void process_storesAPermanentFailureWithoutARetry() {
    doThrow(new ReplayException(ReplayStatus.FAILED_IDENTITY, "wrong match"))
        .when(verifier)
        .verify(anyLong(), any(), any());

    assertThat(service.process(match())).isEqualTo(ReplayStatus.FAILED_IDENTITY);

    assertThat(runs.get(1L).getNextAttemptAt()).isNull();
    verify(goodExtractor, never()).extract(any(), any());
  }

  @Test
  void process_schedulesATransientFailureWithGrowingBackoffUntilTheAttemptCap() {
    when(fileProvider.open(any()))
        .thenThrow(new ReplayException(ReplayStatus.FAILED_DOWNLOAD, "timeout"));
    LocalDateTime before = LocalDateTime.now();

    service.process(match());
    LocalDateTime first = runs.get(1L).getNextAttemptAt();
    service.process(match());
    LocalDateTime second = runs.get(1L).getNextAttemptAt();

    assertThat(runs.get(1L).getAttempts()).isEqualTo(2);
    assertThat(first).isBetween(before.plusMinutes(59), before.plusMinutes(61));
    assertThat(second).isBetween(before.plusMinutes(119), before.plusMinutes(121));

    for (int i = 0; i < 5; i++) {
      service.process(match());
    }
    assertThat(runs.get(1L).getAttempts()).isEqualTo(7);
    assertThat(runs.get(1L).getNextAttemptAt()).isNull();
  }

  @Test
  void process_turnsAnOutOfMemoryErrorIntoAResourceFailureAndFreesThePermit() {
    when(parser.parse(any(), any())).thenThrow(new OutOfMemoryError("heap"));

    assertThat(service.process(match())).isEqualTo(ReplayStatus.FAILED_RESOURCE);

    assertThat(semaphore.availablePermits()).isEqualTo(1);
  }

  @Test
  void process_turnsALibraryLoadFailureIntoAResourceFailure() {
    when(parser.parse(any(), any())).thenThrow(new UnsatisfiedLinkError("zstd-jni"));

    assertThat(service.process(match())).isEqualTo(ReplayStatus.FAILED_RESOURCE);

    assertThat(semaphore.availablePermits()).isEqualTo(1);
  }

  @Test
  void process_turnsAnUnexpectedExceptionIntoAParserFailureInsteadOfThrowing() {
    when(parser.parse(any(), any())).thenThrow(new IllegalStateException("surprise"));

    assertThat(service.process(match())).isEqualTo(ReplayStatus.FAILED_PARSER);

    assertThat(runs.get(1L).getError()).contains("surprise");
    assertThat(semaphore.availablePermits()).isEqualTo(1);
  }

  @Test
  void process_neverThrowsWhenTheRunCannotBeRecorded() {
    when(runRepo.save(any(ReplayParseRunDomain.class)))
        .thenThrow(new IllegalStateException("db down"));

    assertThat(service.process(match())).isEqualTo(ReplayStatus.FAILED_RESOURCE);
  }

  @Test
  void process_givesTheAttemptBackWhenInterruptedBeforeParsing() throws InterruptedException {
    semaphore.acquire();
    Thread.currentThread().interrupt();

    ReplayStatus status = service.process(match());

    assertThat(Thread.interrupted()).isTrue();
    assertThat(status).isEqualTo(ReplayStatus.FAILED_RESOURCE);
    assertThat(runs.get(1L).getAttempts()).isZero();
    assertThat(runs.get(1L).getNextAttemptAt()).isNotNull();
    verify(fileProvider, never()).open(any());
  }

  @Test
  void process_countsAnotherAttemptOnTheSameRow() {
    service.process(match());

    service.process(match());

    assertThat(runs).hasSize(1);
    assertThat(runs.get(1L).getAttempts()).isEqualTo(2);
  }

  private ReplayRunService serviceWith(List<ReplayExtractor> extractors) {
    return new ReplayRunService(
        runRepo,
        statRepo,
        new ReplayEligibility(properties),
        fileProvider,
        parser,
        verifier,
        extractors,
        properties,
        semaphore,
        mapLayouts,
        analysis);
  }

  private static MatchDomain match() {
    MatchDomain match = new MatchDomain();
    match.setId(1L);
    match.setReplayCluster(1L);
    match.setReplaySalt(2L);
    match.setGameModeId(18L);
    match.setDuration(2700L);
    match.setStartTime(Instant.now().minus(1, ChronoUnit.DAYS).getEpochSecond());
    return match;
  }
}
