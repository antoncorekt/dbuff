package com.ako.dbuff.service.match;

import com.ako.dbuff.dotapi.api.MatchesApi;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.google.common.util.concurrent.RateLimiter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class DotaApiMatchDetailsService {

  private final RateLimiter dotaApiRateLimiter;
  private final MatchesApi matchesApi;

  public MatchResponse fetchMatchDetails(long matchId) {
    MatchResponse matchResponse = null;
    try {
      log.debug("Try to fetch match {}", matchId);
      dotaApiRateLimiter.acquire();
      log.info("Fetching match {}", matchId);
      matchResponse = matchesApi.getMatchesByMatchId(matchId);
      log.info("Finished fetching match {}", matchId);

    } catch (ApiException e) {
      // todo custom exception
      throw new RuntimeException(e);
    }

    return matchResponse;
  }
}
