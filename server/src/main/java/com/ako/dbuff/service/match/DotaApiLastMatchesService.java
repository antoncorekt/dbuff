package com.ako.dbuff.service.match;

import com.ako.dbuff.dotapi.api.PlayersApi;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.PlayerRecentMatchesResponse;
import com.google.common.util.concurrent.RateLimiter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

  /**
   * A recent match as the player list reports it.
   *
   * @param id the match ID
   * @param startTime start, epoch seconds; null when OpenDota omitted it
   * @param duration length in seconds; null when OpenDota omitted it
   * @param gameMode OpenDota game mode ID; null when omitted
   */
  public record RecentMatch(long id, Long startTime, Long duration, Long gameMode) {

    /** False when the end time is unknown, so an unreadable match is reported, not hidden. */
    public boolean endedBefore(LocalDateTime moment) {
      if (startTime == null) {
        return false;
      }
      Instant end = Instant.ofEpochSecond(startTime + (duration == null ? 0 : duration));
      return LocalDateTime.ofInstant(end, ZoneId.systemDefault()).isBefore(moment);
    }
  }

  public List<RecentMatch> fetchLastMatches(long accountId) {

    try {
      dotaApiRateLimiter.acquire();
      log.info("Fetching recentMatches for account {}", accountId);
      List<PlayerRecentMatchesResponse> recentMatches =
          playersApi.getPlayersByAccountIdSelectRecentMatches(accountId);
      log.info("Fetched recentMatches for account {}", accountId);

      return recentMatches.stream()
          .map(
              match ->
                  new RecentMatch(
                      match.getMatchId(),
                      match.getStartTime(),
                      match.getDuration(),
                      match.getGameMode()))
          .toList();
    } catch (ApiException e) {
      // todo custom exception
      throw new RuntimeException(e);
    }
  }
}
