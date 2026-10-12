package com.ako.dbuff.service.replay.analysis;

import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.Set;

/**
 * Works out conclusions from what the replay extractors stored, never from the replay itself, so
 * the rules can change and be run again over old matches. Implementations are discovered as beans.
 */
public interface ReplayAnalyzer {
  String name();

  /** Whether the analyzer applies to the mode; a common one keeps the default. */
  default boolean supports(GameModeProfile mode) {
    return true;
  }

  /** Bumped when the rules change; stored with the rows so old and new results are told apart. */
  int version();

  /**
   * The extractors and earlier analyzers whose output this analyzer reads, by name; it is skipped
   * when one of them failed or was itself skipped.
   */
  default Set<String> requires() {
    return Set.of();
  }

  /**
   * The extractor and analyzer names this analyzer reads for a match of the mode; an analyzer whose
   * inputs differ by mode overrides it, so an extractor that matters to one mode does not hold back
   * another.
   */
  default Set<String> requires(GameModeProfile mode) {
    return requires();
  }

  /** Replaces what an earlier run stored for the match. */
  void analyze(long matchId);

  void deleteByMatchId(long matchId);
}
