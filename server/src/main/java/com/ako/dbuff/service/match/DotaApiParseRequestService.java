package com.ako.dbuff.service.match;

import com.ako.dbuff.dotapi.api.MatchesApi;
import com.ako.dbuff.dotapi.api.RequestApi;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DotaApiParseRequestService {

  private final RequestApi requestApi;
  private final MatchesApi matchesApi;
  private final RateLimiter dotaApiRateLimiter;

  public void submitParseRequest(long matchId) {
    for (int i = 0; i < 10; i++) {
      dotaApiRateLimiter.acquire();
    }
    try {
      Object result = requestApi.postRequestByJobId(matchId);
      log.info("Parse request submitted for match {}, response: {}", matchId, result);
    } catch (ApiException e) {
      log.warn("Failed to submit parse request for match {}: {}", matchId, e.getMessage());
    }
  }

  public boolean isMatchParsed(long matchId) {
    dotaApiRateLimiter.acquire();
    try {
      MatchResponse response = matchesApi.getMatchesByMatchId(matchId);
      if (response.getOdData() != null
          && Boolean.TRUE.equals(response.getOdData().getHasParsed())) {
        log.info("Match {} is parsed", matchId);
        return true;
      }
      log.debug("Match {} not yet parsed, od_data: {}", matchId, response.getOdData());
      return false;
    } catch (ApiException e) {
      log.warn("Failed to check parse status for match {}: {}", matchId, e.getMessage());
      return false;
    }
  }
}
