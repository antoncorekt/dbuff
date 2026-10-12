package com.ako.dbuff.service.replay.analysis.building;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What a building's internal name says: whose it is, what kind, which tier and lane. */
public record BuildingName(String team, String kind, Integer tier, String lane) {
  private static final Pattern NAME =
      Pattern.compile(
          "^(?:npc_)?dota_(goodguys|badguys)_(tower(\\d)(?:_(top|mid|bot))?|melee_rax_(top|mid|bot)|range_rax_(top|mid|bot)|fort)$");

  /** Empty for the buildings that do not matter here, such as the ancient's fillers. */
  public static Optional<BuildingName> parse(String name) {
    Matcher m = NAME.matcher(name == null ? "" : name);
    if (!m.matches()) {
      return Optional.empty();
    }
    String team = m.group(1).equals("goodguys") ? "RADIANT" : "DIRE";
    if (m.group(3) != null) {
      return Optional.of(
          new BuildingName(team, "TOWER", Integer.parseInt(m.group(3)), lane(m.group(4))));
    }
    if (m.group(5) != null) {
      return Optional.of(new BuildingName(team, "BARRACKS_MELEE", null, lane(m.group(5))));
    }
    if (m.group(6) != null) {
      return Optional.of(new BuildingName(team, "BARRACKS_RANGED", null, lane(m.group(6))));
    }
    return Optional.of(new BuildingName(team, "FORT", null, null));
  }

  private static String lane(String suffix) {
    return switch (String.valueOf(suffix)) {
      case "top" -> "TOP";
      case "mid" -> "MID";
      case "bot" -> "BOTTOM";
      default -> null;
    };
  }
}
