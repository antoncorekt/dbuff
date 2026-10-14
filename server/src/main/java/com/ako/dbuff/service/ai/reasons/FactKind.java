package com.ako.dbuff.service.ai.reasons;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a fact is about. The weight orders the facts when too many are given (the highest is kept
 * longest), the label is how the report names the evidence of a reason.
 */
public enum FactKind {
  RESULT("RESULT", "result", 100),
  FIGHT("F", "important fights", 90),
  LANE("LANE", "lanes", 80),
  OBJECTIVE("OBJ", "objectives", 70),
  SWING("SWING", "swings", 70),
  ENDGAME("ENDGAME", "end game", 65),
  DRAFT("DRAFT", "draft", 55),
  STATS("STATS", "player stats", 55),
  VISION("VISION", "vision", 50),
  ITEM("ITEM", "items", 50),
  RUNES("RUNES", "runes and opening", 45),
  OUTLIER("OUTLIER", "outliers", 45),
  ROLE("ROLE", "roles", 40),
  ROTATION("ROTATION", "rotations", 40),
  SMOKE("SMOKE", "smoke", 35);

  private final String prefix;
  private final Pattern idPattern;
  private final String label;
  private final int weight;

  FactKind(String prefix, String label, int weight) {
    this.prefix = prefix;
    this.idPattern =
        prefix.equals("F")
            ? Pattern.compile("F\\d+")
            : Pattern.compile(Pattern.quote(prefix) + "(_[A-Z0-9_]+)?");
    this.label = label;
    this.weight = weight;
  }

  public String label() {
    return label;
  }

  public int weight() {
    return weight;
  }

  /** The id of the fact: the bare prefix when there is no suffix, else {@code PREFIX_suffix}. */
  public String id(String suffix) {
    return suffix == null || suffix.isEmpty() ? prefix : prefix + "_" + suffix;
  }

  /** The id of the numbered fact, {@code F3} for a fight and {@code LANE_3} for the others. */
  public String id(int number) {
    return this == FIGHT ? prefix + number : id(Integer.toString(number));
  }

  public static Optional<FactKind> ofId(String id) {
    if (id == null) {
      return Optional.empty();
    }
    for (FactKind kind : values()) {
      if (kind.idPattern.matcher(id).matches()) {
        return Optional.of(kind);
      }
    }
    return Optional.empty();
  }
}
