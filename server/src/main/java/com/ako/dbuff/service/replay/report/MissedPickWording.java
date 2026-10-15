package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.MissedPickDomain;
import java.util.Locale;

/** The words of a missed pick, shared by the report section and the review documents. */
public final class MissedPickWording {
  private MissedPickWording() {}

  /** One sentence pair: what the enemy took and why it was better, then that it was still free. */
  public static String line(MissedPickDomain row, AbilityLabels labels) {
    String candidate = labels.label(row.getCandidateAbility());
    String taken = labels.label(row.getTakenAbility());
    return "The enemy took %s at pick %d; %s. It was still free when you took %s at pick %d."
        .formatted(candidate, row.getCandidateTurn(), why(row, taken), taken, row.getTakenTurn());
  }

  /** Damage as people read it: 840, 1.2k. */
  public static String damage(long value) {
    return value < 1000
        ? Long.toString(value)
        : String.format(Locale.ROOT, "%.1fk", value / 1000.0);
  }

  private static String why(MissedPickDomain row, String taken) {
    String fights =
        "was in %d of their %d won %s"
            .formatted(
                row.getCandidateWonFights(),
                row.getEnemyWonFights(),
                row.getEnemyWonFights() == 1 ? "fight" : "fights");
    if (row.getRatio() == null && row.getCandidateDamage() == 0) {
      return "neither did damage, but it %s and your %s was in %d"
          .formatted(fights, taken, row.getTakenWonFights());
    }
    String damage =
        row.getRatio() == null
            ? "it did %s damage where your %s did none, and it"
                .formatted(damage(row.getCandidateDamage()), taken)
            : "it did %d%% more damage than your %s"
                .formatted(Math.round((row.getRatio() - 1) * 100), taken);
    return damage + (row.getRatio() == null ? " " : " and ") + fights;
  }
}
