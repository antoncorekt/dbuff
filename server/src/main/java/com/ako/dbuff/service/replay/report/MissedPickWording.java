package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import java.util.Locale;

/** The words of a missed pick, shared by the report section and the review documents. */
public final class MissedPickWording {
  private static final String FIGHTS = "FIGHTS";

  private MissedPickWording() {}

  /** One sentence pair: what the enemy took and why it was better, then that it was still free. */
  public static String line(MissedPickDomain row, AbilityLabels labels) {
    String candidate = labels.label(row.getCandidateAbility());
    String taken = labels.label(row.getTakenAbility());
    return "The enemy took %s at pick %d; %s%s. It was still free when you took %s at pick %d."
        .formatted(
            candidate,
            row.getCandidateTurn(),
            why(row, taken),
            patchContext(row),
            taken,
            row.getTakenTurn());
  }

  /** Damage as people read it: 840, 1.2k, cut down to a tenth of a thousand, never rounded up. */
  public static String damage(long value) {
    return value < 1000
        ? Long.toString(value)
        : String.format(Locale.ROOT, "%.1fk", Math.floor(value / 100.0) / 10);
  }

  private static String patchContext(MissedPickDomain row) {
    Integer games = row.getCandidatePatchGames();
    if (games == null) {
      return "";
    }
    if (games == 0 || row.getCandidatePatchAverage() == null) {
      return " (no other games of it on this patch)";
    }
    return " (its patch average is %s in %d %s%s)"
        .formatted(
            damage(Math.round(row.getCandidatePatchAverage())),
            games,
            games == 1 ? "game" : "games",
            Boolean.TRUE.equals(row.getCarried()) ? ", probably carried by a fed hero" : "");
  }

  private static String function(String basis) {
    String prefix = FIGHTS + ":";
    if (basis == null || !basis.startsWith(prefix)) {
      return null;
    }
    try {
      return RoleFunction.valueOf(basis.substring(prefix.length())).words();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String why(MissedPickDomain row, String taken) {
    String fights =
        "was in %d of their %d won %s"
            .formatted(
                row.getCandidateWonFights(),
                row.getEnemyWonFights(),
                row.getEnemyWonFights() == 1 ? "fight" : "fights");
    String function = function(row.getBasis());
    if (function != null) {
      return "both are %s skills; it %s, yours in %d"
          .formatted(function, fights, row.getTakenWonFights());
    }
    if (FIGHTS.equals(row.getBasis())) {
      return "neither did damage, but it %s and your %s was in %d"
          .formatted(fights, taken, row.getTakenWonFights());
    }
    return "it did %d%% more damage than your %s and %s"
        .formatted((long) Math.floor((row.getRatio() - 1) * 100 + 1e-9), taken, fights);
  }
}
