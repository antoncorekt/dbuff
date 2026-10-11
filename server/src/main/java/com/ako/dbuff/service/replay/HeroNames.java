package com.ako.dbuff.service.replay;

/** Replay hero names are CamelCase entity names; OpenDota's and the combat log's are snake_case. */
public final class HeroNames {
  private static final String HERO_PREFIX = "npc_dota_hero_";

  private HeroNames() {}

  public static boolean matches(String entityName, String openDotaName) {
    if (entityName == null || openDotaName == null) {
      return false;
    }
    String stored =
        openDotaName.startsWith(HERO_PREFIX)
            ? openDotaName.substring(HERO_PREFIX.length())
            : openDotaName;
    String snake = entityName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    return snake.equals(stored) || snake.replace("_", "").equals(stored.replace("_", ""));
  }
}
