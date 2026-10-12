package com.ako.dbuff.service.replay.analysis;

import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
   * An analyzer whose input extractor or input analyzer failed or was skipped is skipped, so that
   * rows from an earlier run are kept rather than replaced by conclusions drawn from missing or
   * stale data.
   *
   * @param mode analyzers that do not apply to it are left out
   * @param failedExtractors the extractors that failed in this run; an analyzer that failed or was
   *     skipped counts as failed for the analyzers after it that name it in {@code requires(mode)}
   * @return the names of the analyzers that failed
   */
  public List<String> analyze(
      long matchId, GameModeProfile mode, Collection<String> failedExtractors) {
    List<String> failed = new ArrayList<>();
    Set<String> unavailable = new HashSet<>(failedExtractors);
    for (ReplayAnalyzer analyzer : analyzers) {
      if (!analyzer.supports(mode)) {
        continue;
      }
      List<String> missing =
          analyzer.requires(mode).stream().filter(unavailable::contains).sorted().toList();
      if (!missing.isEmpty()) {
        log.info(
            "Skipping analyzer {} for match {}: inputs failed or were skipped: {}",
            analyzer.name(),
            matchId,
            missing);
        unavailable.add(analyzer.name());
        continue;
      }
      try {
        analyzer.analyze(matchId);
      } catch (RuntimeException e) {
        log.warn("Replay analyzer {} failed for match {}", analyzer.name(), matchId, e);
        failed.add(analyzer.name());
        unavailable.add(analyzer.name());
      }
    }
    return failed;
  }

  public void deleteByMatchId(long matchId) {
    analyzers.forEach(analyzer -> analyzer.deleteByMatchId(matchId));
  }
}
