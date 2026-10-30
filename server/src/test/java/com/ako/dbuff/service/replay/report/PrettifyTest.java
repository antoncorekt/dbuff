package com.ako.dbuff.service.replay.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrettifyTest {

  @Test
  void unit_dropsThePrefixesAndCapitalisesTheWords() {
    assertThat(Prettify.unit("npc_dota_hero_faceless_void")).isEqualTo("Faceless Void");
    assertThat(Prettify.unit("npc_dota_badguys_tower1_mid"))
        .isEqualTo("Dire tier 1 tower (middle)");
    assertThat(Prettify.unit("npc_dota_badguys_tower1_bot"))
        .isEqualTo("Dire tier 1 tower (bottom)");
    assertThat(Prettify.unit("npc_dota_goodguys_tower3_mid"))
        .isEqualTo("Radiant tier 3 tower (middle)");
    assertThat(Prettify.unit("npc_dota_goodguys_tower2_top"))
        .isEqualTo("Radiant tier 2 tower (top)");
    assertThat(Prettify.unit("npc_dota_goodguys_tower4")).isEqualTo("Radiant tier 4 tower");
    assertThat(Prettify.unit("creep_badguys_melee")).isEqualTo("Dire melee creep");
    assertThat(Prettify.unit("npc_dota_creep_goodguys_ranged")).isEqualTo("Radiant ranged creep");
    assertThat(Prettify.unit("npc_dota_hero_windrunner")).isEqualTo("Windranger");
    assertThat(Prettify.unit("Rattletrap")).isEqualTo("Clockwerk");
    assertThat(Prettify.unit("npc_dota_hero_vengefulspirit")).isEqualTo("Vengeful Spirit");
    assertThat(Prettify.unit("npc_dota_hero_queenofpain")).isEqualTo("Queen of Pain");
    assertThat(Prettify.unit("npc_dota_hero_brand_new_hero")).isEqualTo("Brand New Hero");
    assertThat(Prettify.unit("")).isEmpty();
    assertThat(Prettify.unit(null)).isEmpty();
  }

  @Test
  void ability_leavesTheHeroOutOfAnAbilityAndThePrefixOffAnItem() {
    assertThat(Prettify.ability("sven_storm_bolt")).isEqualTo("Sven Storm Bolt");
    assertThat(Prettify.ability("item_black_king_bar")).isEqualTo("Black King Bar");
    assertThat(Prettify.ability("stunned")).isEqualTo("Stunned");
  }

  @Test
  void entity_splitsACamelCaseName() {
    assertThat(Prettify.entity("PhantomAssassin")).isEqualTo("Phantom Assassin");
    assertThat(Prettify.entity("Sniper")).isEqualTo("Sniper");
    assertThat(Prettify.entity(null)).isEmpty();
  }
}
