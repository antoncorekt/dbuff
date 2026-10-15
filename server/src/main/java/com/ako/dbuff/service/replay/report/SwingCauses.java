package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.spike.SpikeCause;
import java.util.Map;

/** What a swing's primary cause is called in a report. */
final class SwingCauses {
  private static final Map<SpikeCause, String> PHRASES =
      Map.of(
          SpikeCause.FIGHT_WIN, "a won fight",
          SpikeCause.PICKOFF, "picked-off heroes",
          SpikeCause.OBJECTIVE, "buildings",
          SpikeCause.ROSHAN_AEGIS, "Roshan",
          SpikeCause.BUYBACK_SWING, "buybacks",
          SpikeCause.CARRY_FARM, "farming",
          SpikeCause.COMEBACK_GOLD, "comeback gold");

  private SwingCauses() {}

  /** The phrase of a stored cause, empty when it has none or is not a known cause. */
  static String phrase(String stored) {
    try {
      return PHRASES.getOrDefault(SpikeCause.valueOf(stored), "");
    } catch (IllegalArgumentException | NullPointerException e) {
      return "";
    }
  }
}
