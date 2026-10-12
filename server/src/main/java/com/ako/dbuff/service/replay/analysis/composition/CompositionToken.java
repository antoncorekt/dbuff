package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import java.util.Arrays;
import java.util.Locale;

/**
 * The tokens of a fixed name, with their plain wording. The counts {@code CARRIES_n} and {@code
 * SUPPORTS_n} carry a number and are worded by {@link #words(String)}.
 */
public enum CompositionToken {
  NO_INITIATOR("no initiator", RoleFunction.INITIATION, false),
  NO_DISABLE("no disable", RoleFunction.DISABLE, false),
  /** The profile has no wave clear function; damage stands in for it. */
  NO_WAVECLEAR("no wave clear", RoleFunction.DAMAGE, false),
  /** The profile has no frontline function; durability stands in for it. */
  NO_FRONTLINE("no frontline", RoleFunction.DURABILITY, false),
  NO_SAVE_HEAL("no save or heal", RoleFunction.SAVE_HEAL, false),
  NO_VISION_DUTY("nobody on vision duty", RoleFunction.VISION_DUTY, true),
  NO_FARM_ENGINE("nobody who wants the farm", null, false),
  TOP_HEAVY("everyone wants the farm", null, false),
  INTENT_NOT_PLAYED("played other roles than the draft meant", null, false);

  private static final String CARRIES_PREFIX = "CARRIES_";
  private static final String SUPPORTS_PREFIX = "SUPPORTS_";

  private final String words;
  private final RoleFunction function;
  private final boolean finalOnly;

  CompositionToken(String words, RoleFunction function, boolean finalOnly) {
    this.words = words;
    this.function = function;
    this.finalOnly = finalOnly;
  }

  /** The function whose absence the token says; null for a token that is not about one. */
  public RoleFunction function() {
    return function;
  }

  /** Whether the token only makes sense after the match, as the draft cannot say it. */
  boolean finalOnly() {
    return finalOnly;
  }

  /**
   * Whether the code is one of the composition tokens, a fixed name or a carry or support count.
   */
  public static boolean isToken(String code) {
    if (code == null) {
      return false;
    }
    if (code.startsWith(CARRIES_PREFIX) || code.startsWith(SUPPORTS_PREFIX)) {
      return true;
    }
    return Arrays.stream(values()).anyMatch(known -> known.name().equals(code));
  }

  /** A stored token in plain words; a name this class does not know is written in lower case. */
  public static String words(String token) {
    if (token.startsWith(CARRIES_PREFIX)) {
      return count(token.substring(CARRIES_PREFIX.length()), "carry", "carries");
    }
    if (token.startsWith(SUPPORTS_PREFIX)) {
      return count(token.substring(SUPPORTS_PREFIX.length()), "support", "supports");
    }
    for (CompositionToken known : values()) {
      if (known.name().equals(token)) {
        return known.words;
      }
    }
    return token.toLowerCase(Locale.ROOT).replace('_', ' ');
  }

  private static String count(String number, String one, String many) {
    return switch (number) {
      case "0" -> "no " + one;
      case "1" -> "1 " + one;
      default -> number + " " + many;
    };
  }
}
