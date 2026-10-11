package com.ako.dbuff.service.replay.analysis;

import java.util.Set;

/**
 * Works out conclusions from what the replay extractors stored, never from the replay itself, so
 * the rules can change and be run again over old matches. Implementations are discovered as beans.
 */
public interface ReplayAnalyzer {
  String name();

  /** Bumped when the rules change; stored with the rows so old and new results are told apart. */
  int version();

  /** The extractors whose output this analyzer reads; it is skipped when one of them failed. */
  default Set<String> requires() {
    return Set.of();
  }

  /** Replaces what an earlier run stored for the match. */
  void analyze(long matchId);

  void deleteByMatchId(long matchId);
}
