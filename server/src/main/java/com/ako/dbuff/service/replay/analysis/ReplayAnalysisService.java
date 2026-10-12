package com.ako.dbuff.service.replay.analysis;

import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
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
  private final MatchRepo matchRepo;
  private final GameModeRegistry modes;

  /**
   * Analyses a match already stored, taking its mode from the match; a match whose mode is not
   * active has nothing to analyse.
   */
  public List<String> analyze(long matchId, Collection<String> failedExtractors) {
    return matchRepo
        .findById(matchId)
        .flatMap(match -> modes.activeFor(match.getGameModeId()))
        .map(mode -> analyze(matchId, mode, failedExtractors))
        .orElseGet(List::of);
  }

  /**
   * An analyzer whose input extractor failed is skipped, so that rows from an earlier run are kept
   * rather than replaced by conclusions drawn from missing or stale data.
   *
   * @param mode analyzers that do not apply to it are left out
   * @param failedExtractors the extractors that failed in this run
   * @return the names of the analyzers that failed
   */
  public List<String> analyze(
      long matchId, GameModeProfile mode, Collection<String> failedExtractors) {
    List<String> failed = new ArrayList<>();
    for (ReplayAnalyzer analyzer : analyzers) {
      if (!analyzer.supports(mode)) {
        continue;
      }
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
