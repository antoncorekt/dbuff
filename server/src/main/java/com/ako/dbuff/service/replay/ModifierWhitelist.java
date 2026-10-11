package com.ako.dbuff.service.replay;

import java.util.Set;

/**
 * The modifiers worth keeping: the ones that say whether a hero could act or be hurt. A modifier
 * with a stun duration is kept whatever its name.
 */
public final class ModifierWhitelist {
  private static final Set<String> NAMES =
      Set.of(
          "modifier_black_king_bar_immune",
          "modifier_teleporting",
          "modifier_stunned",
          "modifier_silence",
          "modifier_silenced",
          "modifier_hexxed",
          "modifier_rooted",
          "modifier_sleep",
          "modifier_fear",
          "modifier_taunt",
          "modifier_knockback",
          "modifier_invisible",
          "modifier_smoke_of_deceit",
          "modifier_fountain_aura_buff",
          "modifier_invulnerable",
          "modifier_aegis",
          "modifier_buyback_gold_penalty");

  private ModifierWhitelist() {}

  public static boolean keeps(String modifierName, double stunSeconds) {
    return stunSeconds > 0 || NAMES.contains(modifierName);
  }
}
