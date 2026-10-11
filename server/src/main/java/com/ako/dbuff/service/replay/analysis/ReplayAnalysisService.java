package com.ako.dbuff.service.replay.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Runs every analyzer for a match; one failing does not stop the others. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayAnalysisService {
  private final List<ReplayAnalyzer> analyzers;

  /**
   * An analyzer whose input extractor failed is skipped, so that rows from an earlier run are kept
   * rather than replaced by conclusions drawn from missing or stale data.
   *
   * @param failedExtractors the extractors that failed in this run
   * @return the names of the analyzers that failed
   */
  public List<String> analyze(long matchId, Collection<String> failedExtractors) {
    List<String> failed = new ArrayList<>();
    for (ReplayAnalyzer analyzer : analyzers) {
      if (analyzer.requires().stream().anyMatch(failedExtractors::contains)) {
        log.info(
            "Skipping analyzer {} for match {}: an input extractor failed",
            analyzer.name(),
            matchId);
        continue;
      }
      try {
        analyzer.analyze(matchId);
      } catch (RuntimeException e) {
        log.warn("Replay analyzer {} failed for match {}", analyzer.name(), matchId, e);
        failed.add(analyzer.name());
      }
    }
    return failed;
  }

  public void deleteByMatchId(long matchId) {
    analyzers.forEach(analyzer -> analyzer.deleteByMatchId(matchId));
  }
}
