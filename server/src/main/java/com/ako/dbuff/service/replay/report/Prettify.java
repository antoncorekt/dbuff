package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Turns the game's internal names into something a person would write. */
public final class Prettify {
  private static final String HERO_PREFIX = "npc_dota_hero_";

  private static final Pattern TOWER =
      Pattern.compile("^npc_dota_(goodguys|badguys)_tower(\\d)(?:_(top|mid|bot))?$");
  private static final Pattern CREEP =
      Pattern.compile(
          "^(?:npc_dota_)?(?:creep_)?(goodguys|badguys)_(?:creep_)?(melee|ranged|siege|flagbearer)$");
  private static final Map<String, String> LANES =
      Map.of("top", "top", "mid", "middle", "bot", "bottom");
  private static final Map<String, String> HERO_NAMES = loadHeroNames();

  private Prettify() {}

  /**
   * {@code npc_dota_hero_windrunner} to {@code Windranger}, {@code npc_dota_badguys_tower1_bot} to
   * {@code Dire tier 1 tower (bottom)}, {@code creep_goodguys_melee} to {@code Radiant melee
   * creep}; other names are capitalised word by word.
   */
  public static String unit(String internal) {
    if (internal == null || internal.isBlank()) {
      return "";
    }
    String tower = tower(internal);
    if (tower != null) {
      return tower;
    }
    Matcher creep = CREEP.matcher(internal);
    if (creep.matches()) {
      return side(creep.group(1)) + " " + creep.group(2) + " creep";
    }
    String name =
        internal.startsWith(HERO_PREFIX) ? internal.substring(HERO_PREFIX.length()) : internal;
    String hero = HERO_NAMES.get(name.toLowerCase(Locale.ROOT));
    if (hero != null) {
      return hero;
    }
    name = name.replaceFirst("^npc_dota_(goodguys_|badguys_)?(creep_)?", "");
    return words(name);
  }

  private static String tower(String internal) {
    Matcher m = TOWER.matcher(internal);
    if (!m.matches()) {
      return null;
    }
    String lane = m.group(3) == null ? "" : " (" + LANES.getOrDefault(m.group(3), m.group(3)) + ")";
    return side(m.group(1)) + " tier " + m.group(2) + " tower" + lane;
  }

  private static String side(String guys) {
    return "goodguys".equals(guys) ? "Radiant" : "Dire";
  }

  private static Map<String, String> loadHeroNames() {
    try (InputStream in = Prettify.class.getResourceAsStream("/replay/hero-unit-names.json")) {
      return in == null
          ? Map.of()
          : new ObjectMapper().readValue(in, new TypeReference<Map<String, String>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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
