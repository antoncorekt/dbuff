package com.ako.dbuff.service.replay.analysis.importance;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides whether a fight changed the advantage graphs. The window runs from the start of the fight
 * for as long as the fight lasted (at least the minimum window) plus the lag, because the series is
 * per minute. Important: the winner's gold advantage grew by the minimum share of its net worth or
 * the minimum gold, or the lead flipped to the winner inside the window and that flip carried at
 * least the minimum flip gold or the minimum share. Experience and the slope break are recorded but
 * never decide.
 */
@Component
@RequiredArgsConstructor
public class ImportantFightRule {
  private final ImportantFightProperties properties;

  /**
   * @param goldChange the winner's gold advantage gained over the window, negative when it lost,
   *     null when a minute it needs has no figure
   * @param xpChange the same for experience
   * @param netWorth the winner's team net worth at the end of the window
   * @param leadFlip the sign of the gold advantage changed inside the window
   * @param flipToWinner the flip left the winner in the lead
   * @param slopeBreak the advantage moved the other way after the fight than before it
   */
  public record Measurement(
      Long goldChange,
      Long xpChange,
      long netWorth,
      double sharePercent,
      boolean leadFlip,
      boolean flipToWinner,
      boolean slopeBreak) {}

  public Measurement measure(
      AdvantageSeries series, TeamSide winner, double startSeconds, double endSeconds) {
    double last = series.endSeconds();
    double minimum = properties.getMinWindowSeconds();
    double span = Math.max(endSeconds - startSeconds, minimum);
    double from = Math.max(0, Math.min(startSeconds, last - minimum));
    double to = Math.min(from + span + properties.getLagSeconds(), last);
    double goldBefore = series.gold(winner, from);
    double goldAfter = series.gold(winner, to);
    boolean goldKnown = complete(series.radiantGold(), from, to);
    long netWorth = Math.round(series.netWorth(winner, to));
    Long goldChange = goldKnown ? Math.round(goldAfter - goldBefore) : null;
    double share = goldKnown && netWorth > 0 ? 100.0 * goldChange / netWorth : 0;
    double xpChange = series.xp(winner, to) - series.xp(winner, from);
    boolean flip = goldKnown && flips(series, winner, from, to);
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
        complete(series.radiantXp(), from, to) ? Math.round(xpChange) : null,
        netWorth,
        share,
        flip,
        flipToWinner,
        slopeBreak);
  }

  public boolean isImportant(Measurement m) {
    boolean grew =
        m.goldChange() != null
            && m.goldChange() > 0
            && (m.goldChange() >= properties.getMinGold()
                || m.sharePercent() >= properties.getMinSharePercent());
    boolean flipped =
        m.flipToWinner()
            && ((m.goldChange() != null && m.goldChange() >= properties.getMinFlipGold())
                || m.sharePercent() >= properties.getMinSharePercent());
    return grew || flipped;
  }

  /** Whether every minute the window touches has a figure. */
  private static boolean complete(double[] values, double from, double to) {
    int first = (int) Math.floor(from / AdvantageSeries.MINUTE);
    int last = Math.min((int) Math.ceil(to / AdvantageSeries.MINUTE), values.length - 1);
    for (int i = first; i <= last; i++) {
      if (Double.isNaN(values[i])) {
        return false;
      }
    }
    return true;
  }

  /** Whether the advantage has opposite signs at two successive marks of the window. */
  private static boolean flips(AdvantageSeries series, TeamSide team, double from, double to) {
    double previous = series.gold(team, from);
    int firstMinute = (int) Math.floor(from / AdvantageSeries.MINUTE) + 1;
    for (double t = firstMinute * (double) AdvantageSeries.MINUTE;
        t < to;
        t += AdvantageSeries.MINUTE) {
      double value = series.gold(team, t);
      if (Double.isNaN(value)) {
        continue;
      }
      if (previous * value < 0) {
        return true;
      }
      previous = value != 0 ? value : previous;
    }
    return previous * series.gold(team, to) < 0;
  }
}
