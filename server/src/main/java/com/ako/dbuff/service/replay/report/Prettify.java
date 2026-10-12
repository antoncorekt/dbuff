package com.ako.dbuff.service.replay.report;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Turns the game's internal names into something a person would write. */
final class Prettify {
  private static final String HERO_PREFIX = "npc_dota_hero_";
  private static final int ABILITY_PARTS_WITH_HERO = 3;

  private Prettify() {}

  /** {@code npc_dota_hero_faceless_void} to {@code Faceless Void}; other names get the same. */
  static String unit(String internal) {
    if (internal == null || internal.isBlank()) {
      return "";
    }
    String name =
        internal.startsWith(HERO_PREFIX) ? internal.substring(HERO_PREFIX.length()) : internal;
    name = name.replaceFirst("^npc_dota_(goodguys_|badguys_)?(creep_)?", "");
    return words(name);
  }

  /**
   * {@code sven_storm_bolt} to {@code Storm Bolt}, {@code item_black_king_bar} to {@code Black King
   * Bar}: an ability's name starts with its hero, which is left out.
   */
  static String ability(String internal) {
    if (internal.startsWith("item_")) {
      return words(internal.substring("item_".length()));
    }
    String[] parts = internal.split("_");
    return parts.length >= ABILITY_PARTS_WITH_HERO
        ? words(String.join("_", Arrays.copyOfRange(parts, 1, parts.length)))
        : words(internal);
  }

  private static String words(String snake) {
    return Arrays.stream(snake.split("_"))
        .filter(w -> !w.isEmpty())
        .map(w -> w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1))
        .collect(Collectors.joining(" "));
  }
}
