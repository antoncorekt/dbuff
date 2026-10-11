package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dotapi.model.MatchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchDetailService {

  private final List<MatchDetailMapper> mappers;

  public void map(MatchResponse response, MatchDomain match) {
    mappers.forEach(mapper -> mapper.map(response, match));
  }

  public void deleteByMatchId(long matchId) {
    mappers.forEach(mapper -> mapper.deleteByMatchId(matchId));
  }
}
