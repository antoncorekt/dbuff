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

  private static final int MAX_RETRIES = 4;
  private static final long INITIAL_BACKOFF_MS = 30_000; // 30s, 60s, 120s, 240s

  private final RateLimiter dotaApiRateLimiter;
  private final MatchesApi matchesApi;

  public MatchResponse fetchMatchDetails(long matchId) {
    ApiException lastException = null;

    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      try {
        dotaApiRateLimiter.acquire();
        log.info("Fetching match {} (attempt {})", matchId, attempt + 1);
        MatchResponse response = matchesApi.getMatchesByMatchId(matchId);
        log.info("Finished fetching match {}", matchId);
        return response;
      } catch (ApiException e) {
        lastException = e;
        if (attempt < MAX_RETRIES) {
          long waitMs = INITIAL_BACKOFF_MS * (1L << attempt);
          log.warn(
              "DotaAPI error for match {} (attempt {}), retrying in {}s: {}",
              matchId,
              attempt + 1,
              waitMs / 1000,
              e.getMessage());
          try {
            Thread.sleep(waitMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while retrying match " + matchId, ie);
          }
        }
      }
    }

    log.error("DotaAPI failed for match {} after {} attempts", matchId, MAX_RETRIES + 1);
    throw new RuntimeException(lastException);
  }
}
