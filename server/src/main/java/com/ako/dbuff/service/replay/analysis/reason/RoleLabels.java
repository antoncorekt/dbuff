package com.ako.dbuff.service.replay.analysis.reason;

/** The role numbers of {@code player_role} in words, for evidence texts. */
final class RoleLabels {
  private RoleLabels() {}

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
