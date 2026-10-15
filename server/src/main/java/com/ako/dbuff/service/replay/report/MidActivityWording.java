package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.MidActivityDomain;
import com.ako.dbuff.service.replay.analysis.mid.MidNext;
import com.ako.dbuff.service.replay.analysis.mid.MidVerdict;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The plain sentence about what one mid hero did, from its stored {@code mid_activity} row. */
final class MidActivityWording {
  private MidActivityWording() {}

  /**
   * {@code Puck was active: reached level 6 at 7:10, went to the top lane and made 2 kills; ...}.
   */
  static String sentence(MidActivityDomain row, String hero) {
    MidVerdict verdict = Enums.find(MidVerdict.class, row.getVerdict());
    String head = hero + (verdict == null ? "" : " was " + verdict.name().toLowerCase(Locale.ROOT));
    List<String> parts = new ArrayList<>();
    parts.add(move(row));
    if (row.getFightsTotal() != null && row.getFightsTotal() > 0 && row.getFightsJoined() != null) {
      parts.add("joined %d of %d fights".formatted(row.getFightsJoined(), row.getFightsTotal()));
    }
    String income = income(row);
    if (!income.isEmpty()) {
      parts.add(income);
    }
    return head + ": " + String.join("; ", parts);
  }

  private static String move(MidActivityDomain row) {
    if (row.getLevel6Seconds() == null) {
      return "did not reach level 6";
    }
    String level = "reached level 6 at " + GameTime.clock(row.getLevel6Seconds());
    MidNext next = Enums.find(MidNext.class, row.getNextAction());
    if (next == null) {
      return level;
    }
    return switch (next) {
      case STAYED_MID -> level + " and stayed in the mid lane";
      case ROTATED ->
          level
              + ", went to the "
              + BehaviourWording.place("LANE", row.getNextPlace())
              + kills(row);
      case JUNGLE -> level + ", went to the jungle" + kills(row);
      case FIGHT -> level + ", joined a fight" + kills(row);
    };
  }

  private static String kills(MidActivityDomain row) {
    Integer kills = row.getKillsAfterLeaving();
    if (kills == null) {
      return "";
    }
    return kills == 0 ? " and made no kills" : " and made " + SectionViews.count(kills, "kill");
  }

  private static String income(MidActivityDomain row) {
    boolean enemy = row.getEnemyGoldPerMinute() != null || row.getEnemyXpPerMinute() != null;
    String against = enemy ? "the enemy mid" : "his team's average";
    String gold =
        difference(
            row.getGoldPerMinute(),
            enemy ? row.getEnemyGoldPerMinute() : row.getTeamGoldPerMinute());
    String xp =
        difference(
            row.getXpPerMinute(), enemy ? row.getEnemyXpPerMinute() : row.getTeamXpPerMinute());
    if (gold == null && xp == null) {
      return "";
    }
    if (gold == null) {
      return "his experience per minute was %s %s".formatted(xp, against);
    }
    return xp == null
        ? "his gold per minute was %s %s".formatted(gold, against)
        : "his gold per minute was %s %s and his experience per minute %s it"
            .formatted(gold, against, xp);
  }

  /** {@code 18% above}, {@code 5% below} or {@code level with}; null when it cannot be told. */
  private static String difference(Integer own, Integer other) {
    if (own == null || other == null || other <= 0) {
      return null;
    }
    long percent = Math.round((own - other) * 100.0 / other);
    if (percent == 0) {
      return "level with";
    }
    return Math.abs(percent) + "% " + (percent > 0 ? "above" : "below");
  }
}
