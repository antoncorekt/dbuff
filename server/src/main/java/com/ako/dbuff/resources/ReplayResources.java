package com.ako.dbuff.resources;

import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisService;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.read.AnalysisAssembler;
import com.ako.dbuff.service.replay.read.InvalidSectionsException;
import com.ako.dbuff.service.replay.read.MatchNotStoredException;
import com.ako.dbuff.service.replay.read.RunStatus;
import com.ako.dbuff.service.replay.read.RunStatusReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations on the replay analysis. Re-running the analyzers needs no replay, only the stored
 * data, so it is safe to do after a rule changed.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/replay")
public class ReplayResources {
  /**
   * A partial run is left out: which extractors failed is not kept, so its analyzers cannot be
   * chosen.
   */
  private static final List<ReplayStatus> ANALYSED = List.of(ReplayStatus.DONE);

  private static final int MAX_LIMIT = 500;
  private static final String NO_ACTIVE_MODE =
      "The match is not stored or its game mode is not active for replay processing";

  private final AtomicBoolean reanalysing = new AtomicBoolean();

  private final ReplayParseRunRepo runRepo;
  private final ReplayAnalysisService analysis;
  private final StoredMatchModes matchModes;
  private final ReplayProcessingProperties properties;
  private final RunStatusReader runStatus;
  private final AnalysisAssembler assembler;

  /** How the replay run of a match ended. */
  @GetMapping("/{matchId}")
  public ResponseEntity<RunStatus> run(@PathVariable long matchId) {
    return runStatus
        .read(matchId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * The stored analysis of a match as one JSON document, read from the tables only. {@code
   * sections} is a comma separated list of section names, all when absent.
   */
  @GetMapping("/{matchId}/analysis")
  public Map<String, Object> analysis(
      @PathVariable long matchId, @RequestParam(required = false) String sections) {
    return assembler.assemble(matchId, sections);
  }

  @ExceptionHandler(InvalidSectionsException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> invalidSections(InvalidSectionsException e) {
    return Map.of("error", e.getMessage());
  }

  @ExceptionHandler(MatchNotStoredException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void notStored() {}

  /** Runs every analyzer again for one match from what is stored. */
  @PostMapping("/{matchId}/reanalyze")
  public ResponseEntity<Map<String, Object>> reanalyze(@PathVariable long matchId) {
    ReplayParseRunDomain run =
        runRepo.findByMatchIdAndParserVersion(matchId, properties.getParserVersion()).orElse(null);
    if (run == null || !ANALYSED.contains(run.getStatus())) {
      return ResponseEntity.notFound().build();
    }
    GameModeProfile mode = matchModes.activeFor(matchId).orElse(null);
    if (mode == null) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", NO_ACTIVE_MODE));
    }
    if (!reanalysing.compareAndSet(false, true)) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("error", "A re-analysis is already running"));
    }
    try {
      List<String> failed = analysis.analyze(matchId, mode, List.of());
      log.info("Re-analysed replay of match {}, failed analyzers: {}", matchId, failed);
      return ResponseEntity.ok(Map.of("matchId", matchId, "failedAnalyzers", failed));
    } finally {
      reanalysing.set(false);
    }
  }

  /**
   * Runs every analyzer again for the finished runs of matches after {@code after}, up to {@code
   * limit} (1 to 500), oldest match first, and says the last match id it reached so the next call
   * can continue from there. One re-analysis at a time, bulk or single.
   */
  @PostMapping("/reanalyze")
  public ResponseEntity<Map<String, Object>> reanalyzeAll(
      @RequestParam(defaultValue = "100") int limit, @RequestParam(defaultValue = "0") long after) {
    if (limit < 1 || limit > MAX_LIMIT) {
      return ResponseEntity.badRequest().body(Map.of("error", "limit must be 1 to " + MAX_LIMIT));
    }
    if (!reanalysing.compareAndSet(false, true)) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("error", "A re-analysis is already running"));
    }
    try {
      List<ReplayParseRunDomain> runs =
          runRepo.findByParserVersionAndStatusInOrderByMatchId(
              properties.getParserVersion(), ANALYSED);
      int done = 0;
      int withFailures = 0;
      int skipped = 0;
      long last = after;
      for (ReplayParseRunDomain run :
          runs.stream().filter(r -> r.getMatchId() > after).limit(limit).toList()) {
        last = run.getMatchId();
        GameModeProfile mode = matchModes.activeFor(last).orElse(null);
        if (mode == null) {
          skipped++;
          continue;
        }
        done++;
        if (failed(last, mode)) {
          withFailures++;
        }
      }
      log.info(
          "Re-analysed {} of {} replay runs, {} with failures, {} skipped",
          done,
          runs.size(),
          withFailures,
          skipped);
      return ResponseEntity.ok(
          Map.of(
              "analysed", done,
              "available", runs.size(),
              "withFailures", withFailures,
              "skipped", skipped,
              "lastMatchId", last));
    } finally {
      reanalysing.set(false);
    }
  }

  private boolean failed(long matchId, GameModeProfile mode) {
    try {
      return !analysis.analyze(matchId, mode, List.of()).isEmpty();
    } catch (RuntimeException e) {
      log.warn("Re-analysis of match {} failed", matchId, e);
      return true;
    }
  }
}
