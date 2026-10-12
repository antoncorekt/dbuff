package com.ako.dbuff.resources;

import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  private static final List<ReplayStatus> ANALYSED =
      List.of(ReplayStatus.DONE, ReplayStatus.PARTIAL);

  private final ReplayParseRunRepo runRepo;
  private final ReplayAnalysisService analysis;
  private final ReplayProcessingProperties properties;

  /** How the replay run of a match ended. */
  @GetMapping("/{matchId}")
  public ResponseEntity<Map<String, Object>> run(@PathVariable long matchId) {
    return runRepo
        .findByMatchIdAndParserVersion(matchId, properties.getParserVersion())
        .map(
            run -> {
              Map<String, Object> body = new LinkedHashMap<>();
              body.put("matchId", run.getMatchId());
              body.put("status", run.getStatus());
              body.put("attempts", run.getAttempts());
              body.put("error", run.getError());
              body.put("mapVersion", run.getMapVersion());
              return ResponseEntity.ok(body);
            })
        .orElse(ResponseEntity.notFound().build());
  }

  /** Runs every analyzer again for one match from what is stored. */
  @PostMapping("/{matchId}/reanalyze")
  public ResponseEntity<Map<String, Object>> reanalyze(@PathVariable long matchId) {
    ReplayParseRunDomain run =
        runRepo.findByMatchIdAndParserVersion(matchId, properties.getParserVersion()).orElse(null);
    if (run == null || !ANALYSED.contains(run.getStatus())) {
      return ResponseEntity.notFound().build();
    }
    List<String> failed = analysis.analyze(matchId, List.of());
    log.info("Re-analysed replay of match {}, failed analyzers: {}", matchId, failed);
    return ResponseEntity.ok(Map.of("matchId", matchId, "failedAnalyzers", failed));
  }

  /**
   * Runs every analyzer again for the finished runs, up to {@code limit} (1 to 500), oldest match
   * first. One call at a time.
   */
  @PostMapping("/reanalyze")
  public ResponseEntity<Map<String, Object>> reanalyzeAll(
      @RequestParam(defaultValue = "100") int limit) {
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
      for (ReplayParseRunDomain run : runs.stream().limit(limit).toList()) {
        done++;
        if (failed(run.getMatchId())) {
          withFailures++;
        }
      }
      log.info(
          "Re-analysed {} of {} replay runs, {} with failures", done, runs.size(), withFailures);
      return ResponseEntity.ok(
          Map.of("analysed", done, "available", runs.size(), "withFailures", withFailures));
    } finally {
      reanalysing.set(false);
    }
  }

  private boolean failed(long matchId) {
    try {
      return !analysis.analyze(matchId, List.of()).isEmpty();
    } catch (RuntimeException e) {
      log.warn("Re-analysis of match {} failed", matchId, e);
      return true;
    }
  }
}
