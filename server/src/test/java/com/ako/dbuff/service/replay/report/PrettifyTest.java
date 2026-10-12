package com.ako.dbuff.service.replay.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrettifyTest {

  @Test
  void unit_dropsThePrefixesAndCapitalisesTheWords() {
    assertThat(Prettify.unit("npc_dota_hero_faceless_void")).isEqualTo("Faceless Void");
    assertThat(Prettify.unit("npc_dota_badguys_tower1_mid")).isEqualTo("Tower1 Mid");
    assertThat(Prettify.unit("")).isEmpty();
    assertThat(Prettify.unit(null)).isEmpty();
  }

  @Test
  void ability_leavesTheHeroOutOfAnAbilityAndThePrefixOffAnItem() {
    assertThat(Prettify.ability("sven_storm_bolt")).isEqualTo("Sven Storm Bolt");
    assertThat(Prettify.ability("item_black_king_bar")).isEqualTo("Black King Bar");
    assertThat(Prettify.ability("stunned")).isEqualTo("Stunned");
  }
}
