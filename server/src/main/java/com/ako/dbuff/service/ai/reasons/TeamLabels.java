package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Locale;

/** How the facts and the card name a team: Radiant or Dire, and "your team" for the tracked one. */
final class TeamLabels {
  private TeamLabels() {}

  static String name(TeamSide side) {
    return side == TeamSide.RADIANT ? "Radiant" : "Dire";
  }

  static String of(TeamSide side, TeamSide yours) {
    return name(side) + (side == yours ? " (your team)" : "");
  }

  static String lower(TeamSide side) {
    return name(side).toLowerCase(Locale.ROOT);
  }
}
