package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dotapi.model.MatchResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchDetailService {

  private final List<MatchDetailMapper> mappers;

  /**
   * Replaces every stored detail row of the match with the ones in the payload and stamps the match
   * with the parse state and replay location. All rows are written or none are. A match OpenDota
   * has not parsed yet is only marked as attempted, so it stays eligible for a later backfill. The
   * caller persists the match itself.
   */
  @Transactional
  public void replace(MatchResponse response, MatchDomain match) {
    applyReplayInfo(response, match);
    deleteByMatchId(match.getId());
    mappers.forEach(mapper -> mapper.map(response, match));
    LocalDateTime now = LocalDateTime.now();
    match.setDetailsAttemptedAt(now);
    if (Boolean.TRUE.equals(match.getHasParsed())) {
      match.setDetailsMappedAt(now);
    }
  }

  public void deleteByMatchId(long matchId) {
    mappers.forEach(mapper -> mapper.deleteByMatchId(matchId));
  }

  private void applyReplayInfo(MatchResponse response, MatchDomain match) {
    if (response.getCluster() != null) {
      match.setReplayCluster(response.getCluster());
    }
    if (response.getReplaySalt() != null) {
      match.setReplaySalt(response.getReplaySalt());
    }
    match.setHasParsed(
        response.getOdData() != null && Boolean.TRUE.equals(response.getOdData().getHasParsed()));
  }
}
