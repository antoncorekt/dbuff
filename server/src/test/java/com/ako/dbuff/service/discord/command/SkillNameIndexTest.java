package com.ako.dbuff.service.discord.command;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.HeroesAbilityConstant;
import com.ako.dbuff.service.replay.report.AbilityLabels;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class SkillNameIndexTest {
  private final AtomicInteger labelCalls = new AtomicInteger();
  private ConstantsManagers constants;
  private SkillNameIndex index;

  @BeforeEach
  void setUp() {
    constants = Mockito.mock(ConstantsManagers.class);
    Map<String, AbilityIdsConstant> abilities = new HashMap<>();
    abilities.put("1", AbilityIdsConstant.builder().id(1L).name("sven_storm_bolt").build());
    abilities.put("2", AbilityIdsConstant.builder().id(2L).name("sven_great_cleave").build());
    abilities.put(
        "3", AbilityIdsConstant.builder().id(3L).name("special_bonus_unique_sven").build());
    abilities.put("4", AbilityIdsConstant.builder().id(4L).name("b_twin").build());
    abilities.put("5", AbilityIdsConstant.builder().id(5L).name("a_twin").build());
    Mockito.when(constants.getAbilityConstantMap()).thenReturn(abilities);
    Map<String, HeroConstant> heroes = new HashMap<>();
    heroes.put("18", new HeroConstant("18", "npc_dota_hero_sven", "Sven"));
    heroes.put("19", new HeroConstant("19", "npc_dota_hero_sven2", "Sven"));
    Mockito.when(constants.getHeroConstantMap()).thenReturn(heroes);
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
    AbilityLabels labels =
        name -> {
          labelCalls.incrementAndGet();
          return switch (name) {
            case "sven_storm_bolt" -> "Storm Bolt";
            case "sven_great_cleave" -> "Great Cleave";
            default -> "Twin";
          };
        };
    index = new SkillNameIndex(constants, labels);
  }

  @Test
  void resolvesTheDisplayNameAndTheInternalNameIgnoringCase() {
    assertThat(index.resolve("storm bolt")).containsExactly(1L);
    assertThat(index.resolve("SVEN_STORM_BOLT")).containsExactly(1L);
    assertThat(index.resolve("nothing")).isEmpty();
    assertThat(index.resolve(" ")).isEmpty();
  }

  @Test
  void skillsSharingALabelAreAllReturnedInInternalNameOrderAndStaySelectable() {
    assertThat(index.resolve("twin")).containsExactly(5L, 4L);
    assertThat(index.resolve("a_twin")).containsExactly(5L);
    assertThat(index.displayToInternal())
        .containsEntry("Twin (a_twin)", "a_twin")
        .containsEntry("Twin (b_twin)", "b_twin")
        .doesNotContainKey("Twin");
    assertThat(index.resolve("Twin (b_twin)")).containsExactly(4L);
    assertThat(index.internalName(5L)).contains("a_twin");
  }

  @Test
  void talentsAreNeitherListedNorResolved() {
    assertThat(index.displayToInternal()).doesNotContainKey("special_bonus_unique_sven");
    assertThat(index.resolve("special_bonus_unique_sven")).isEmpty();
  }

  @Test
  void theTablesAreBuiltOnceUntilTheConstantsMapChanges() {
    index.displayToInternal();
    int afterFirst = labelCalls.get();
    index.displayToInternal();
    index.resolve("storm bolt");
    index.suggest("Storm Bolt!");

    assertThat(labelCalls.get()).isEqualTo(afterFirst);

    Mockito.when(constants.getAbilityConstantMap())
        .thenReturn(
            Map.of("1", AbilityIdsConstant.builder().id(1L).name("sven_storm_bolt").build()));
    assertThat(index.displayToInternal()).containsOnlyKeys("Storm Bolt");
    assertThat(labelCalls.get()).isGreaterThan(afterFirst);
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

  @Test
  void heroesAreFoundByEitherNameInIdOrder() {
    assertThat(index.heroes("sven")).containsExactly(18L, 19L);
    assertThat(index.heroes("npc_dota_hero_sven2")).containsExactly(19L);
    assertThat(index.heroes("nobody")).isEmpty();
  }
}
