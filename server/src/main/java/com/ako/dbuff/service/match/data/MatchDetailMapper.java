package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dotapi.model.MatchResponse;

/**
 * Persists one slice of the OpenDota match payload beyond the core match and player statistics.
 * Implementations are discovered as beans, so a new slice needs no change to the callers.
 */
public interface MatchDetailMapper {

  void map(MatchResponse response, MatchDomain match);

  void deleteByMatchId(long matchId);
}
