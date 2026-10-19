package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Turns the game's internal names into something a person would write. */
public final class Prettify {
  private static final String HERO_PREFIX = "npc_dota_hero_";

  private Prettify() {}

  /** {@code npc_dota_hero_faceless_void} to {@code Faceless Void}; other names get the same. */
  public static String unit(String internal) {
    if (internal == null || internal.isBlank()) {
      return "";
    }
    String name =
        internal.startsWith(HERO_PREFIX) ? internal.substring(HERO_PREFIX.length()) : internal;
    name = name.replaceFirst("^npc_dota_(goodguys_|badguys_)?(creep_)?", "");
    return words(name);
  }

  /** {@code RADIANT} to {@code Radiant}, {@code DIRE} to {@code Dire}; anything else is unknown. */
  static String team(String stored) {
    return switch (stored == null ? "" : stored) {
      case "RADIANT" -> "Radiant";
      case "DIRE" -> "Dire";
      default -> "An unknown team";
    };
  }

  /** {@code Radiant}, or {@code Radiant (your team)} for the team being reported to. */
  static String teamOf(String stored, TeamSide yours) {
    TeamSide side = Enums.find(TeamSide.class, stored);
    return team(stored) + (side != null && side == yours ? " (your team)" : "");
  }

  /** A replay entity name: {@code PhantomAssassin} to {@code Phantom Assassin}. */
  static String entity(String camelCase) {
    return camelCase == null ? "" : camelCase.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
  }

  /**
   * {@code item_black_king_bar} to {@code Black King Bar}, {@code sven_storm_bolt} to {@code Sven
   * Storm Bolt}.
   */
  public static String ability(String internal) {
    return words(internal.startsWith("item_") ? internal.substring("item_".length()) : internal);
  }

  private static String words(String snake) {
    return Arrays.stream(snake.split("_"))
        .filter(w -> !w.isEmpty())
        .map(w -> w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1))
        .collect(Collectors.joining(" "));
  }
}
