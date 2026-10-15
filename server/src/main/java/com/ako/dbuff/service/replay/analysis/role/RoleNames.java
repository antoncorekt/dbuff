package com.ako.dbuff.service.replay.analysis.role;

import java.util.Map;

/** The five roles as a person would say them. */
public final class RoleNames {
  private static final Map<Integer, String> NAMES =
      Map.of(
          1, "carry",
          2, "mid",
          3, "offlane",
          4, "roaming support",
          5, "hard support");

  private RoleNames() {}

  /** The role in plain words; null for a number that is not a role. */
  public static String words(int role) {
    return NAMES.get(role);
  }
}
