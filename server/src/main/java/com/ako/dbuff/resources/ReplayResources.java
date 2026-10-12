package com.ako.dbuff.resources;

import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            run ->
                ResponseEntity.ok(
                    Map.<String, Object>of(
                        "matchId", run.getMatchId(),
                        "status", run.getStatus().name(),
                        "attempts", run.getAttempts() == null ? 0 : run.getAttempts(),
                        "error", String.valueOf(run.getError()),
                        "mapVersion", String.valueOf(run.getMapVersion()))))
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

  /** Runs every analyzer again for the finished runs, up to {@code limit}, oldest match first. */
  @PostMapping("/reanalyze")
  public Map<String, Object> reanalyzeAll(@RequestParam(defaultValue = "100") int limit) {
    List<ReplayParseRunDomain> runs =
        runRepo.findByParserVersionAndStatusInOrderByMatchId(
            properties.getParserVersion(), ANALYSED);
    int done = 0;
    int withFailures = 0;
    for (ReplayParseRunDomain run : runs.stream().limit(Math.max(1, limit)).toList()) {
      done++;
      if (!analysis.analyze(run.getMatchId(), List.of()).isEmpty()) {
        withFailures++;
      }
    }
    log.info("Re-analysed {} of {} replay runs, {} with failures", done, runs.size(), withFailures);
    return Map.of("analysed", done, "available", runs.size(), "withFailures", withFailures);
  }
}
