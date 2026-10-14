package com.ako.dbuff.service.replay.analysis.outlier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one plain sentence that says why an outlier is what it is. It names no hero, so a reader can
 * put any hero in front of it; every part is left out when its figure is not known.
 */
public final class OutlierWording {
  public static final int MAX_LENGTH = 400;
  private static final double MOSTLY_SHARE = 0.5;
  private static final String ELLIPSIS = "...";

  private static final Map<String, String> DEATH_REASONS =
      Map.ofEntries(
          Map.entry("CAUGHT_OUT", "caught out"),
          Map.entry("TOWER", "to a tower"),
          Map.entry("BURST", "burst down"),
          Map.entry("DISABLE_CHAIN", "chain-disabled"),
          Map.entry("OUT_OF_POSITION", "out of position"),
          Map.entry("DIED_IMMUNE", "through spell immunity"),
          Map.entry("BKB_PIERCED", "disabled through Black King Bar"),
          Map.entry("BKB_EXPIRED", "just after Black King Bar ran out"),
          Map.entry("BKB_READY_UNUSED", "with Black King Bar ready"),
          Map.entry("BKB_ON_COOLDOWN", "with Black King Bar on cooldown"),
          Map.entry("NO_BKB", "without Black King Bar"));

  private OutlierWording() {}

  /**
   * @param feeder whether the outlier is a feeder, else a net worth outlier
   * @param breakMinute the minute his income broke from the team's; null if not found
   * @param main the main gold source; null if not known
   * @param fightsJoined fights he joined
   * @param totalFights fights of the match
   * @param medianFights the team's median of fights joined
   * @param windowDeaths deaths in the outlier window
   * @param topDeathReason the commonest death reason in the window; null if none
   * @param topDeathCount deaths with that reason
   */
  public record Facts(
      boolean feeder,
      Integer breakMinute,
      IncomeSource.Main main,
      int fightsJoined,
      int totalFights,
      double medianFights,
      int windowDeaths,
      String topDeathReason,
      int topDeathCount) {}

  public static String sentence(Facts f) {
    List<String> parts = new ArrayList<>();
    if (f.feeder() && f.windowDeaths() > 0) {
      parts.add(deaths(f));
    }
    String income = income(f);
    if (income != null) {
      parts.add(income);
    }
    if (f.totalFights() > 0) {
      parts.add(fights(f));
    }
    String text = String.join("; ", parts);
    return text.length() <= MAX_LENGTH
        ? text
        : text.substring(0, MAX_LENGTH - ELLIPSIS.length()).stripTrailing() + ELLIPSIS;
  }

  private static String deaths(Facts f) {
    String text = "he died " + times(f.windowDeaths()) + " in that stretch";
    String reason = f.topDeathReason() == null ? null : DEATH_REASONS.get(f.topDeathReason());
    if (reason == null || f.topDeathCount() < 1) {
      return text;
    }
    if (f.windowDeaths() == 1) {
      return text + ", " + reason;
    }
    String count =
        f.topDeathCount() >= f.windowDeaths() ? "all" : String.valueOf(f.topDeathCount());
    return text + ", " + count + " of them " + reason;
  }

  private static String income(Facts f) {
    String verb = f.feeder() ? "fell behind" : "rose";
    String source =
        f.main() == null
            ? null
            : "came "
                + (f.main().share() >= MOSTLY_SHARE ? "mostly" : "mainly")
                + " from "
                + f.main().source().phrase();
    if (f.breakMinute() != null) {
      return "his income "
          + verb
          + " from minute "
          + f.breakMinute()
          + (source == null ? "" : " and " + source);
    }
    return source == null ? null : "his income " + source;
  }

  private static String fights(Facts f) {
    String compare =
        f.fightsJoined() < f.medianFights()
            ? "fewer than"
            : f.fightsJoined() > f.medianFights() ? "more than" : "the same as";
    return "he joined %d of %d fights, %s the team median of %s"
        .formatted(f.fightsJoined(), f.totalFights(), compare, number(f.medianFights()));
  }

  private static String times(int n) {
    return n == 1 ? "once" : n + " times";
  }

  private static String number(double value) {
    return value == Math.rint(value)
        ? String.valueOf((long) value)
        : String.format(Locale.ROOT, "%.1f", value);
  }
}
