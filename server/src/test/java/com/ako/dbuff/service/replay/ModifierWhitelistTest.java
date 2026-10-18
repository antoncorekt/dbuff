package com.ako.dbuff.service.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ModifierWhitelistTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "modifier_item_glimmer_cape_fade",
        "modifier_item_forcestaff_active",
        "modifier_item_dustofappearance",
        "modifier_truesight",
        "modifier_bottle_regeneration",
        "modifier_item_soul_ring_buff",
        "modifier_item_aeon_disk_buff",
        "modifier_item_sphere",
        "modifier_item_lotus_orb_active",
        "modifier_eul_cyclone",
        "modifier_stunned",
        "modifier_rune_haste"
      })
  void keeps_theItemEffectsAndTheExistingOnes(String name) {
    assertThat(ModifierWhitelist.keeps(name, 0)).isTrue();
  }

  @Test
  void keeps_dropsAnOtherModifierWithoutAStun_andKeepsAStunWhateverItsName() {
    assertThat(ModifierWhitelist.keeps("modifier_item_radiance", 0)).isFalse();
    assertThat(ModifierWhitelist.keeps("modifier_something_else", 1.5)).isTrue();
  }
}
