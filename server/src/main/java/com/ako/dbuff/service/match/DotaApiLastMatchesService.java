package com.ako.dbuff.service.match;

import com.ako.dbuff.dotapi.api.PlayersApi;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.PlayerRecentMatchesResponse;
import com.google.common.util.concurrent.RateLimiter;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class DotaApiLastMatchesService {

  private final RateLimiter dotaApiRateLimiter;
  private final PlayersApi playersApi;

  public List<Long> fetchLastMatches(long accountId) {

    try {
      dotaApiRateLimiter.acquire();
      log.info("Fetching recentMatches for account {}", accountId);
      List<PlayerRecentMatchesResponse> recentMatches =
          playersApi.getPlayersByAccountIdSelectRecentMatches(accountId);
      log.info("Fetched recentMatches for account {}", accountId);

      return recentMatches.stream().map(PlayerRecentMatchesResponse::getMatchId).toList();
    } catch (ApiException e) {
      // todo custom exception
      throw new RuntimeException(e);
    }
  }
}
