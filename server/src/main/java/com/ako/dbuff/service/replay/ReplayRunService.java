package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.file.ReplayFile;
import com.ako.dbuff.service.replay.file.ReplayFileProvider;
import com.ako.dbuff.service.replay.map.MapLayouts;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Runs the replay pipeline for one match and records how it went. Nothing thrown by the replay work
 * leaves this class: every failure becomes a stored status, so match ingestion and reports never
 * depend on a replay.
 */
@Slf4j
@Service
public class ReplayRunService {
  private static final long NANOS_PER_SECOND = 1_000_000_000L;
  private static final int MESSAGE_LIMIT = 1000;

  private final ReplayParseRunRepo runRepo;
  private final PlayerGameStatisticRepo playerStatRepo;
  private final ReplayEligibility eligibility;
  private final ReplayFileProvider fileProvider;
  private final ReplayParser parser;
  private final ReplayRosterVerifier rosterVerifier;
  private final List<ReplayExtractor> extractors;
  private final ReplayProcessingProperties properties;
  private final Semaphore parseSemaphore;
  private final MapLayouts mapLayouts;

  public ReplayRunService(
      ReplayParseRunRepo runRepo,
      PlayerGameStatisticRepo playerStatRepo,
      ReplayEligibility eligibility,
      ReplayFileProvider fileProvider,
      ReplayParser parser,
      ReplayRosterVerifier rosterVerifier,
      List<ReplayExtractor> extractors,
      ReplayProcessingProperties properties,
      @Qualifier("replayParseSemaphore") Semaphore parseSemaphore,
      MapLayouts mapLayouts) {
    this.runRepo = runRepo;
    this.playerStatRepo = playerStatRepo;
    this.eligibility = eligibility;
    this.fileProvider = fileProvider;
    this.parser = parser;
    this.rosterVerifier = rosterVerifier;
    this.extractors = extractors;
    this.properties = properties;
    this.parseSemaphore = parseSemaphore;
    this.mapLayouts = mapLayouts;
  }

  /**
   * @return the status the run ended with
   */
  public ReplayStatus process(MatchDomain match) {
    try {
      return run(match);
    } catch (RuntimeException e) {
      log.error("Replay run for match {} could not be recorded", match.getId(), e);
      return ReplayStatus.FAILED_RESOURCE;
    }
  }

  private ReplayStatus run(MatchDomain match) {
    ReplayParseRunDomain run = start(match);
    var skip = eligibility.skipReason(match);
    if (skip.isPresent()) {
      return finish(run, skip.get().getStatus(), skip.get().getMessage());
    }
    boolean acquired = false;
    try {
      parseSemaphore.acquire();
      acquired = true;
      return parse(match, run);
    } catch (ReplayException e) {
      return fail(run, e.getStatus(), e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return release(run, "Interrupted while waiting to parse");
    } catch (OutOfMemoryError e) {
      return fail(run, ReplayStatus.FAILED_RESOURCE, "Out of memory while parsing");
    } catch (LinkageError e) {
      log.error("Replay libraries cannot be loaded", e);
      return fail(run, ReplayStatus.FAILED_RESOURCE, "Library failure: " + e);
    } catch (StackOverflowError e) {
      return fail(run, ReplayStatus.FAILED_PARSER, "Stack overflow while parsing");
    } catch (RuntimeException e) {
      log.warn("Replay run for match {} failed unexpectedly", match.getId(), e);
      return fail(
          run, ReplayStatus.FAILED_PARSER, e.getClass().getSimpleName() + ": " + e.getMessage());
    } finally {
      if (acquired) {
        parseSemaphore.release();
      }
    }
  }

  private ReplayStatus parse(MatchDomain match, ReplayParseRunDomain run) {
    ReplaySource source =
        new ReplaySource(match.getId(), match.getReplayCluster(), match.getReplaySalt());
    try (ReplayFile file = fileProvider.open(source)) {
      long started = System.nanoTime();
      long deadline = started + properties.getMaxParseSeconds() * NANOS_PER_SECOND;
      ReplayFacts facts = parser.parse(file.path(), () -> System.nanoTime() > deadline);
      rosterVerifier.verify(
          match.getId(), facts.players(), playerStatRepo.findAllByMatchId(match.getId()));
      List<String> failed = runExtractors(facts, match);
      run.setReplayBytes(file.compressedBytes());
      run.setClockStartTick(facts.clockStartTick());
      run.setClarityVersion(parser.version());
      recordMapVersion(run, facts);
      run.setParseMillis((System.nanoTime() - started) / 1_000_000);
      if (failed.isEmpty()) {
        return finish(run, ReplayStatus.DONE, null);
      }
      return fail(run, ReplayStatus.PARTIAL, "Failed extractors: " + String.join(", ", failed));
    }
  }

  private void recordMapVersion(ReplayParseRunDomain run, ReplayFacts facts) {
    if (facts.map() == null) {
      return;
    }
    run.setMapVersion(facts.map().version());
    if (mapLayouts.forVersion(run.getMapVersion()).isEmpty()) {
      log.warn(
          "Match {} was played on map layout {}, the frozen one is {}; map analyses will skip it",
          run.getMatchId(),
          run.getMapVersion(),
          mapLayouts.frozenVersion());
    }
  }

  /** One extractor failing must not stop the others. */
  private List<String> runExtractors(ReplayFacts facts, MatchDomain match) {
    List<String> failed = new ArrayList<>();
    for (ReplayExtractor extractor : extractors) {
      try {
        extractor.extract(facts, match);
      } catch (RuntimeException e) {
        log.warn("Replay extractor {} failed for match {}", extractor.name(), match.getId(), e);
        failed.add(extractor.name());
      }
    }
    return failed;
  }

  private ReplayParseRunDomain start(MatchDomain match) {
    LocalDateTime now = LocalDateTime.now();
    ReplayParseRunDomain run =
        runRepo
            .findByMatchIdAndParserVersion(match.getId(), properties.getParserVersion())
            .orElseGet(
                () ->
                    ReplayParseRunDomain.builder()
                        .matchId(match.getId())
                        .parserVersion(properties.getParserVersion())
                        .attempts(0)
                        .build());
    run.setStatus(ReplayStatus.RUNNING);
    run.setAttempts(run.getAttempts() + 1);
    run.setStartedAt(now);
    run.setFinishedAt(null);
    run.setNextAttemptAt(null);
    run.setLeaseUntil(now.plusMinutes(properties.getLeaseMinutes()));
    run.setError(null);
    return runRepo.save(run);
  }

  /** Nothing was tried, so the attempt is given back and the match is due again at once. */
  private ReplayStatus release(ReplayParseRunDomain run, String message) {
    run.setAttempts(run.getAttempts() - 1);
    run.setNextAttemptAt(LocalDateTime.now());
    return finish(run, ReplayStatus.FAILED_RESOURCE, message);
  }

  private ReplayStatus fail(ReplayParseRunDomain run, ReplayStatus status, String message) {
    int cap =
        status == ReplayStatus.PARTIAL
            ? properties.getMaxPartialAttempts()
            : properties.getMaxAttempts();
    if (!status.isPermanent() && run.getAttempts() < cap) {
      long hours = (long) properties.getRetryBackoffHours() << (run.getAttempts() - 1);
      run.setNextAttemptAt(LocalDateTime.now().plusHours(hours));
    }
    return finish(run, status, message);
  }

  private ReplayStatus finish(ReplayParseRunDomain run, ReplayStatus status, String message) {
    run.setStatus(status);
    run.setFinishedAt(LocalDateTime.now());
    run.setLeaseUntil(null);
    run.setError(message == null ? null : truncate(message));
    runRepo.save(run);
    log.info(
        "Replay run for match {}: {} (attempt {}, {} bytes, {} ms)",
        run.getMatchId(),
        status,
        run.getAttempts(),
        run.getReplayBytes(),
        run.getParseMillis());
    return status;
  }

  private static String truncate(String message) {
    return message.length() <= MESSAGE_LIMIT ? message : message.substring(0, MESSAGE_LIMIT);
  }
}
