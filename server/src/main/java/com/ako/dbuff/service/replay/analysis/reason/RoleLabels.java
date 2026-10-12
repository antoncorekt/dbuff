package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Locale;

/** The role numbers of {@code player_role} and the teams in words, for evidence texts. */
final class RoleLabels {
  private RoleLabels() {}

  static String team(TeamSide team) {
    return team.name().charAt(0) + team.name().substring(1).toLowerCase(Locale.ROOT);
  }

  static String of(int role) {
    return switch (role) {
      case 1 -> "carry";
      case 2 -> "mid";
      case 3 -> "offlane";
      case 4 -> "soft support";
      case 5 -> "hard support";
      default -> "role " + role;
    };
  }
}
