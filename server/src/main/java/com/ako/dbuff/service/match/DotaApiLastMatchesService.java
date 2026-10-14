package com.ako.dbuff.service.match;

import com.ako.dbuff.dotapi.api.PlayersApi;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.PlayerRecentMatchesResponse;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
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
   * @param line how the player whose list this is did; null when built without it
   */
  public record RecentMatch(
      long id, Long startTime, Long duration, Long gameMode, PlayerLine line) {

    public RecentMatch(long id, Long startTime, Long duration, Long gameMode) {
      this(id, startTime, duration, gameMode, null);
    }

    /** False when the end time is unknown, so an unreadable match is reported, not hidden. */
    public boolean endedBefore(LocalDateTime moment) {
      if (startTime == null) {
        return false;
      }
      Instant end = Instant.ofEpochSecond(startTime + (duration == null ? 0 : duration));
      return LocalDateTime.ofInstant(end, ZoneId.systemDefault()).isBefore(moment);
    }
  }

  /**
   * One tracked player's part of a recent match; every field but the account may be null because
   * OpenDota omits what it does not know.
   */
  public record PlayerLine(
      long accountId,
      Long playerSlot,
      Long heroId,
      Long kills,
      Long deaths,
      Long assists,
      Long goldPerMin,
      Long xpPerMin,
      Boolean radiantWin) {

    /** Whether the player won; null when the side or the winner is unknown. */
    public Boolean won() {
      if (playerSlot == null || radiantWin == null) {
        return null;
      }
      return (TeamSide.ofSlot(playerSlot) == TeamSide.RADIANT) == radiantWin;
    }
  }

  static RecentMatch toRecentMatch(long accountId, PlayerRecentMatchesResponse match) {
    PlayerLine line =
        new PlayerLine(
            accountId,
            match.getPlayerSlot(),
            match.getHeroId(),
            match.getKills(),
            match.getDeaths(),
            match.getAssists(),
            match.getGoldPerMin(),
            match.getXpPerMin(),
            match.getRadiantWin());
    return new RecentMatch(
        match.getMatchId(), match.getStartTime(), match.getDuration(), match.getGameMode(), line);
  }

  public List<RecentMatch> fetchLastMatches(long accountId) {

    try {
      dotaApiRateLimiter.acquire();
      log.info("Fetching recentMatches for account {}", accountId);
      List<PlayerRecentMatchesResponse> recentMatches =
          playersApi.getPlayersByAccountIdSelectRecentMatches(accountId);
      log.info("Fetched recentMatches for account {}", accountId);

      return recentMatches.stream().map(match -> toRecentMatch(accountId, match)).toList();
    } catch (ApiException e) {
      // todo custom exception
      throw new RuntimeException(e);
    }
  }
}
