package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import java.util.Map;

/** Composition tokens and profile functions as a person would write them. */
final class CompositionWords {
  private static final String CARRIES_PREFIX = "CARRIES_";
  private static final String SUPPORTS_PREFIX = "SUPPORTS_";

  private static final Map<String, String> FIXED =
      Map.of(
          "NO_INITIATOR", "no initiator",
          "NO_DISABLE", "no disable",
          "NO_WAVECLEAR", "no wave clear",
          "NO_FRONTLINE", "no frontline",
          "NO_SAVE_HEAL", "no save or heal",
          "NO_VISION_DUTY", "nobody on vision duty",
          "NO_FARM_ENGINE", "nobody who wants the farm",
          "TOP_HEAVY", "everyone wants the farm",
          "INTENT_NOT_PLAYED", "played other roles than the draft meant");

  private static final Map<RoleFunction, String> FUNCTIONS =
      Map.of(
          RoleFunction.FARM_PRIORITY, "farm priority",
          RoleFunction.JUNGLE, "jungle",
          RoleFunction.ROAMING, "roaming",
          RoleFunction.VISION_DUTY, "vision duty",
          RoleFunction.INITIATION, "initiation",
          RoleFunction.DISABLE, "disable",
          RoleFunction.SAVE_HEAL, "save and heal",
          RoleFunction.DAMAGE, "damage",
          RoleFunction.DURABILITY, "durability");

  private CompositionWords() {}

  static String token(String token) {
    if (token.startsWith(CARRIES_PREFIX)) {
      return count(token.substring(CARRIES_PREFIX.length()), "carry", "carries");
    }
    if (token.startsWith(SUPPORTS_PREFIX)) {
      return count(token.substring(SUPPORTS_PREFIX.length()), "support", "supports");
    }
    return FIXED.getOrDefault(token, token);
  }

  static String function(RoleFunction function) {
    return FUNCTIONS.get(function);
  }

  private static String count(String number, String one, String many) {
    return switch (number) {
      case "0" -> "no " + one;
      case "1" -> "1 " + one;
      default -> number + " " + many;
    };
  }
}
