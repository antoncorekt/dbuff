package com.ako.dbuff.service.replay;

import java.util.Set;

/**
 * The modifiers worth keeping: the ones that say whether a hero could act or be hurt. A modifier
 * with a stun duration is kept whatever its name.
 */
public final class ModifierWhitelist {
  /*
   * modifier_item_sphere_target is verified (Linken's Sphere, match 9044252857). Not yet seen in a
   * fixture: modifier_item_sphere, modifier_item_aeon_disk_buff, modifier_truesight,
   * modifier_item_gem_of_true_sight.
   */
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
          "modifier_buyback_gold_penalty",
          "modifier_bottle_regeneration",
          "modifier_item_soul_ring_buff",
          "modifier_item_glimmer_cape_fade",
          "modifier_item_forcestaff_active",
          "modifier_item_dustofappearance",
          "modifier_item_lotus_orb_active",
          "modifier_eul_cyclone",
          "modifier_item_aeon_disk_buff",
          "modifier_item_sphere",
          "modifier_item_sphere_target",
          "modifier_truesight",
          "modifier_item_gem_of_true_sight",
          "modifier_pudge_meat_hook",
          "modifier_oracle_false_promise_timer");

  /** The effects of the runes a hero picks up; a few events per pickup. */
  private static final String RUNE_PREFIX = "modifier_rune_";

  private ModifierWhitelist() {}

  public static boolean keeps(String modifierName, double stunSeconds) {
    return stunSeconds > 0 || NAMES.contains(modifierName) || modifierName.startsWith(RUNE_PREFIX);
  }
}
