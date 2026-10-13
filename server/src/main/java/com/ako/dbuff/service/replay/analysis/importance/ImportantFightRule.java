package com.ako.dbuff.service.replay.analysis.importance;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides whether a fight changed the advantage graphs. The window runs from the start of the fight
 * for as long as the fight lasted (at least the minimum window) plus the lag, because the series is
 * per minute. Important: the winner's gold advantage grew by the minimum share of its net worth or
 * the minimum gold, or the lead flipped to the winner inside the window. Experience and the slope
 * break are recorded but never decide.
 */
@Component
@RequiredArgsConstructor
public class ImportantFightRule {
  private final ImportantFightProperties properties;

  /**
   * @param goldChange the winner's gold advantage gained over the window, negative when it lost
   * @param xpChange the same for experience
   * @param netWorth the winner's team net worth at the end of the window
   * @param leadFlip the sign of the gold advantage changed inside the window
   * @param flipToWinner the flip left the winner in the lead
   * @param slopeBreak the advantage moved the other way after the fight than before it
   */
  public record Measurement(
      long goldChange,
      long xpChange,
      long netWorth,
      double sharePercent,
      boolean leadFlip,
      boolean flipToWinner,
      boolean slopeBreak) {}

  public Measurement measure(
      AdvantageSeries series, TeamSide winner, double startSeconds, double endSeconds) {
    double span = Math.max(endSeconds - startSeconds, properties.getMinWindowSeconds());
    double from = Math.min(startSeconds, series.endSeconds());
    double to = Math.min(startSeconds + span + properties.getLagSeconds(), series.endSeconds());
    double goldBefore = series.gold(winner, from);
    double goldAfter = series.gold(winner, to);
    long goldChange = Math.round(goldAfter - goldBefore);
    long netWorth = Math.round(series.netWorth(winner, to));
    double share = netWorth > 0 ? 100.0 * goldChange / netWorth : 0;
    boolean flip = flips(series, winner, from, to);
    boolean flipToWinner = flip && goldAfter > 0;
    double earlier = series.gold(winner, Math.max(0, from - (to - from)));
    double slopeBefore = goldBefore - earlier;
    double slopeAfter = goldAfter - goldBefore;
    boolean slopeBreak =
        Math.abs(slopeBefore) >= properties.getMinSlopeGold()
            && Math.abs(slopeAfter) >= properties.getMinSlopeGold()
            && slopeBefore * slopeAfter < 0;
    return new Measurement(
        goldChange,
        Math.round(series.xp(winner, to) - series.xp(winner, from)),
        netWorth,
        share,
        flip,
        flipToWinner,
        slopeBreak);
  }

  public boolean isImportant(Measurement m) {
    boolean grew =
        m.goldChange() > 0
            && (m.goldChange() >= properties.getMinGold()
                || m.sharePercent() >= properties.getMinSharePercent());
    return grew || m.flipToWinner();
  }

  /** Whether the advantage has opposite signs at two successive marks of the window. */
  private static boolean flips(AdvantageSeries series, TeamSide team, double from, double to) {
    double previous = series.gold(team, from);
    int firstMinute = (int) Math.floor(from / AdvantageSeries.MINUTE) + 1;
    for (double t = firstMinute * (double) AdvantageSeries.MINUTE;
        t < to;
        t += AdvantageSeries.MINUTE) {
      double value = series.gold(team, t);
      if (previous * value < 0) {
        return true;
      }
      previous = value != 0 ? value : previous;
    }
    return previous * series.gold(team, to) < 0;
  }
}
