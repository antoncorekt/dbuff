package com.ako.dbuff.service.replay.analysis.rune;

/**
 * The rune codes of OpenDota's {@code runes_log}, checked on four real games: each power rune's
 * code matched the {@code modifier_rune_*} effect the combat log shows on that hero at that second
 * (0 double damage, 1 haste, 3 invisibility, 4 regeneration, 6 arcane, 9 shield), 2 (illusion) has
 * no modifier, 7 occurs only between 2:00 and 5:00 (water), 8 at about 7:00, 14:00, 21:00 and 28:00
 * and pays the wisdom experience, 5 pays the bounty gold. A code not listed reads as {@link
 * #UNKNOWN}.
 */
public enum RuneType {
  DOUBLE_DAMAGE(0, "double damage", true, "modifier_rune_doubledamage"),
  HASTE(1, "haste", true, "modifier_rune_haste"),
  ILLUSION(2, "illusion", false, null),
  INVISIBILITY(3, "invisibility", true, "modifier_rune_invis"),
  REGENERATION(4, "regeneration", false, null),
  BOUNTY(5, "bounty", false, null),
  ARCANE(6, "arcane", true, "modifier_rune_arcane"),
  WATER(7, "water", false, null),
  WISDOM(8, "wisdom", false, null),
  SHIELD(9, "shield", true, "modifier_rune_shield"),
  UNKNOWN(-1, "unknown", false, null);

  private final int code;
  private final String words;
  private final boolean fightEffect;
  private final String modifier;

  RuneType(int code, String words, boolean fightEffect, String modifier) {
    this.code = code;
    this.words = words;
    this.fightEffect = fightEffect;
    this.modifier = modifier;
  }

  public static RuneType of(Integer code) {
    for (RuneType type : values()) {
      if (code != null && type.code == code) {
        return type;
      }
    }
    return UNKNOWN;
  }

  public int code() {
    return code;
  }

  /** The rune as it reads in a sentence: {@code double damage}. */
  public String words() {
    return words;
  }

  /** Whether holding it changes how a fight goes: a damage, speed, spell or protection effect. */
  public boolean isFightEffect() {
    return fightEffect;
  }

  /** The modifier the combat log shows on the hero while the effect lasts; null for none. */
  public String modifier() {
    return modifier;
  }
}
