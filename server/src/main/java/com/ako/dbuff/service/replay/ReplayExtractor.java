package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;

/**
 * Writes one slice of replay facts for a match. Implementations are discovered as beans, so a new
 * slice needs no change to the run service. Writing replaces what an earlier run stored.
 */
public interface ReplayExtractor {
  String name();

  void extract(ReplayFacts facts, MatchDomain match);

  void deleteByMatchId(long matchId);
}
