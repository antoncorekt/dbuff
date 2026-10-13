package com.ako.dbuff.service.replay.analysis.behaviour;

/** What a winning team took after a fight. */
public enum ObjectiveKind {
  TOWER("a tower"),
  BARRACKS("barracks"),
  ANCIENT("the ancient"),
  ROSHAN("Roshan");

  private final String words;

  ObjectiveKind(String words) {
    this.words = words;
  }

  public String words() {
    return words;
  }

  /** The kind of a stored building kind; null for one that is no objective. */
  static ObjectiveKind ofBuilding(String buildingKind) {
    if (buildingKind == null) {
      return null;
    }
    return switch (buildingKind) {
      case "TOWER" -> TOWER;
      case "BARRACKS_MELEE", "BARRACKS_RANGED" -> BARRACKS;
      case "FORT" -> ANCIENT;
      default -> null;
    };
  }
}
