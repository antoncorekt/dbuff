package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.discord.icon.TestIcons;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConstantAbilityLabelsTest {

  private final ConstantsManagers constants = mock(ConstantsManagers.class);
  private final ConstantAbilityLabels labels =
      new ConstantAbilityLabels(constants, TestIcons.off());

  ConstantAbilityLabelsTest() {
    when(constants.getAllAbilityConstants())
        .thenReturn(
            Map.of(
                "terrorblade_metamorphosis",
                AbilityConstant.builder().dname("Metamorphosis").build(),
                "blank_name",
                AbilityConstant.builder().dname(" ").build()));
    when(constants.getItemConstantMap())
        .thenReturn(
            Map.of("black_king_bar", ItemConstant.builder().dname("Black King Bar").build()));
    Map<String, HeroConstant> heroes = new HashMap<>();
    heroes.put("1", HeroConstant.builder().name("npc_dota_hero_sven").build());
    heroes.put("2", HeroConstant.builder().name("npc_dota_hero_death_prophet").build());
    heroes.put("3", HeroConstant.builder().name("npc_dota_hero_void_spirit").build());
    heroes.put("4", HeroConstant.builder().name("npc_dota_hero_void").build());
    heroes.put("5", null);
    heroes.put("6", HeroConstant.builder().build());
    when(constants.getHeroConstantMap()).thenReturn(heroes);
  }

  @Test
  void label_usesTheDisplayNameWhenKnown() {
    assertThat(labels.label("terrorblade_metamorphosis")).isEqualTo("Metamorphosis");
  }

  @Test
  void label_dropsALeadingHeroTokenWithoutADisplayName() {
    assertThat(labels.label("sven_gods_strength")).isEqualTo("Gods Strength");
    assertThat(labels.label("death_prophet_exorcism")).isEqualTo("Exorcism");
    assertThat(labels.label("void_spirit_dissimilate")).isEqualTo("Dissimilate");
  }

  @Test
  void label_takesTheLongestMatchingHeroToken() {
    assertThat(labels.label("void_spirit_aether_remnant")).isEqualTo("Aether Remnant");
    assertThat(labels.label("void_chronosphere")).isEqualTo("Chronosphere");
  }

  @Test
  void label_usesAnItemDisplayNameAndFallsBackToTheNamePrettified() {
    assertThat(labels.label("item_black_king_bar")).isEqualTo("Black King Bar");
    assertThat(labels.label("item_arcane_boots")).isEqualTo("Arcane Boots");
    assertThat(labels.label("item_sven_cape")).isEqualTo("Sven Cape");
  }

  @Test
  void label_prefersTheItemDisplayNameOverThePrettifiedKey() {
    when(constants.getItemConstantMap())
        .thenReturn(Map.of("black_king_bar", ItemConstant.builder().dname("BKB").build()));

    assertThat(labels.label("item_black_king_bar")).isEqualTo("BKB");
  }

  @Test
  void label_keepsTheOldTextForAnUnknownAbility() {
    assertThat(labels.label("mystery_spell")).isEqualTo("Mystery Spell");
    assertThat(labels.label("blank_name")).isEqualTo("Blank Name");
    assertThat(labels.label("sven")).isEqualTo("Sven");
  }

  @Test
  void label_doesNotFailOnAMissingName() {
    assertThat(labels.label(null)).isEmpty();
    assertThat(labels.label(" ")).isEmpty();
  }

  @Test
  void label_doesNotFailOnAbsentConstants() {
    when(constants.getAllAbilityConstants()).thenReturn(null);
    when(constants.getItemConstantMap()).thenReturn(null);
    when(constants.getHeroConstantMap()).thenReturn(null);

    assertThat(labels.label("sven_gods_strength")).isEqualTo("Sven Gods Strength");
    assertThat(labels.label("item_blink")).isEqualTo("Blink");
  }

  @Test
  void label_doesNotFailWhenTheConstantsCannotBeRead() {
    when(constants.getAllAbilityConstants()).thenThrow(new IllegalStateException("down"));
    when(constants.getItemConstantMap()).thenThrow(new IllegalStateException("down"));
    when(constants.getHeroConstantMap()).thenThrow(new IllegalStateException("down"));

    assertThat(labels.label("sven_gods_strength")).isEqualTo("Sven Gods Strength");
  }

  @Test
  void label_followsNewConstants() {
    assertThat(labels.label("mystery_spell")).isEqualTo("Mystery Spell");
    when(constants.getAllAbilityConstants())
        .thenReturn(Map.of("mystery_spell", AbilityConstant.builder().dname("Mystery").build()));

    assertThat(labels.label("mystery_spell")).isEqualTo("Mystery");
  }
}
