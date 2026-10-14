package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConstantAbilityLabelsTest {

  private final ConstantsManagers constants = mock(ConstantsManagers.class);
  private final ConstantAbilityLabels labels = new ConstantAbilityLabels(constants);

  ConstantAbilityLabelsTest() {
    when(constants.getAllAbilityConstants())
        .thenReturn(
            Map.of(
                "terrorblade_metamorphosis",
                AbilityConstant.builder().dname("Metamorphosis").build(),
                "blank_name",
                AbilityConstant.builder().dname(" ").build()));
    when(constants.getHeroConstantMap())
        .thenReturn(
            Map.of(
                "1", HeroConstant.builder().name("npc_dota_hero_sven").build(),
                "2", HeroConstant.builder().name("npc_dota_hero_death_prophet").build(),
                "3", HeroConstant.builder().name("npc_dota_hero_void_spirit").build()));
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
  void label_leavesAnItemAlone() {
    assertThat(labels.label("item_arcane_boots")).isEqualTo("Arcane Boots");
  }

  @Test
  void label_keepsTheOldTextForAnUnknownAbility() {
    assertThat(labels.label("mystery_spell")).isEqualTo("Mystery Spell");
    assertThat(labels.label("blank_name")).isEqualTo("Blank Name");
    assertThat(labels.label("sven")).isEqualTo("Sven");
  }
}
