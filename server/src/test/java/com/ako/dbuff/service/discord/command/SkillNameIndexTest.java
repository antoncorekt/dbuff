package com.ako.dbuff.service.discord.command;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.HeroesAbilityConstant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class SkillNameIndexTest {
  private SkillNameIndex index;

  @BeforeEach
  void setUp() {
    ConstantsManagers constants = Mockito.mock(ConstantsManagers.class);
    Mockito.when(constants.getAbilityConstantMap())
        .thenReturn(
            Map.of(
                "1", AbilityIdsConstant.builder().id(1L).name("sven_storm_bolt").build(),
                "2", AbilityIdsConstant.builder().id(2L).name("sven_great_cleave").build(),
                "3",
                    AbilityIdsConstant.builder().id(3L).name("special_bonus_unique_sven").build()));
    Mockito.when(constants.getHeroConstantMap())
        .thenReturn(Map.of("18", new HeroConstant("18", "npc_dota_hero_sven", "Sven")));
    Mockito.when(constants.getHeroAbilitiesMap())
        .thenReturn(
            Map.of(
                "npc_dota_hero_sven",
                HeroesAbilityConstant.builder()
                    .abilities(
                        List.of(
                            "sven_storm_bolt",
                            "sven_great_cleave",
                            "special_bonus_unique_sven",
                            "unknown_skill"))
                    .build()));
    index =
        new SkillNameIndex(
            constants, name -> name.equals("sven_storm_bolt") ? "Storm Bolt" : "Great Cleave");
  }

  @Test
  void resolvesTheDisplayNameAndTheInternalNameIgnoringCase() {
    assertThat(index.resolve("storm bolt")).contains(1L);
    assertThat(index.resolve("SVEN_STORM_BOLT")).contains(1L);
    assertThat(index.resolve("nothing")).isEmpty();
    assertThat(index.resolve(" ")).isEmpty();
  }

  @Test
  void talentsAreNeitherListedNorResolved() {
    assertThat(index.displayToInternal()).containsOnlyKeys("Storm Bolt", "Great Cleave");
    assertThat(index.resolve("special_bonus_unique_sven")).isEmpty();
  }

  @Test
  void suggestsTheNearestDisplayNameForATypo() {
    assertThat(index.suggest("Storm Bolt!")).contains("Storm Bolt");
    assertThat(index.suggest("completely different words")).isEmpty();
  }

  @Test
  void aHeroHasItsRealSkillsOnly() {
    assertThat(index.skillsOfHero(18)).containsExactly(1L, 2L);
    assertThat(index.skillsOfHero(999)).isEmpty();
  }
}
