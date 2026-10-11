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
   * with the parse state and replay location. The caller persists the match itself.
   */
  @Transactional
  public void replace(MatchResponse response, MatchDomain match) {
    applyReplayInfo(response, match);
    deleteByMatchId(match.getId());
    mappers.forEach(mapper -> mapper.map(response, match));
    match.setDetailsMappedAt(LocalDateTime.now());
  }

  public void deleteByMatchId(long matchId) {
    mappers.forEach(mapper -> mapper.deleteByMatchId(matchId));
  }

  private void applyReplayInfo(MatchResponse response, MatchDomain match) {
    match.setReplayCluster(response.getCluster());
    match.setReplaySalt(response.getReplaySalt());
    match.setHasParsed(
        response.getOdData() != null && Boolean.TRUE.equals(response.getOdData().getHasParsed()));
  }
}
