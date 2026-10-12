package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.role.IntentProfileCalculator.HeldAbility;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentProfileCalculatorTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final Map<String, HeroConstant> HEROES =
      load("constants_profile_heroes.json", new TypeReference<>() {});
  private static final Map<String, AbilityConstant> ABILITIES =
      load("constants_profile_abilities.json", new TypeReference<>() {});

  private final IntentProfileCalculator calculator =
      new IntentProfileCalculator(new RoleProfileProperties());

  private static <T> T load(String resource, TypeReference<T> type) {
    try (InputStream in =
        IntentProfileCalculatorTest.class.getResourceAsStream("/opendota/" + resource)) {
      return MAPPER.readValue(in, type);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** The kit as the hero has it in the game: the last listed skill is the ultimate. */
  private FunctionProfile kit(String heroId, String... skills) {
    List<HeldAbility> held = new ArrayList<>();
    for (int i = 0; i < skills.length; i++) {
      held.add(new HeldAbility(ABILITIES.get(skills[i]), i == skills.length - 1));
    }
    return calculator.profile(HEROES.get(heroId), held);
  }

  private FunctionProfile antiMage() {
    return kit(
        "1",
        "antimage_mana_break",
        "antimage_blink",
        "antimage_counterspell",
        "antimage_mana_void");
  }

  private FunctionProfile crystalMaiden() {
    return kit(
        "5",
        "crystal_maiden_crystal_nova",
        "crystal_maiden_frostbite",
        "crystal_maiden_brilliance_aura",
        "crystal_maiden_freezing_field");
  }

  private FunctionProfile axe() {
    return kit(
        "2", "axe_berserkers_call", "axe_battle_hunger", "axe_counter_helix", "axe_culling_blade");
  }

  private FunctionProfile shadowShaman() {
    return kit(
        "27",
        "shadow_shaman_ether_shock",
        "shadow_shaman_voodoo",
        "shadow_shaman_shackles",
        "shadow_shaman_mass_serpent_ward");
  }

  private FunctionProfile omniknight() {
    return kit(
        "57",
        "omniknight_purification",
        "omniknight_martyr",
        "omniknight_hammer_of_purity",
        "omniknight_guardian_angel");
  }

  private FunctionProfile sven() {
    return kit("18", "sven_storm_bolt", "sven_great_cleave", "sven_warcry", "sven_gods_strength");
  }

  @Test
  void aCarryKitWithABlinkFarmsAndCannotDisable() {
    FunctionProfile am = antiMage();

    assertThat(am.farmPriority()).isGreaterThanOrEqualTo(0.5);
    assertThat(am.initiation()).isPositive();
    assertThat(am.roaming()).isGreaterThan(crystalMaiden().roaming());
    assertThat(am.disable()).isZero();
    assertThat(am.farmPriority()).isGreaterThan(crystalMaiden().farmPriority());
  }

  @Test
  void aSupportKitIsReadAsControlAndDamageNotFarm() {
    FunctionProfile cm = crystalMaiden();

    assertThat(cm.disable()).isGreaterThanOrEqualTo(0.4);
    assertThat(cm.damage()).isGreaterThan(antiMage().damage());
    assertThat(cm.farmPriority()).isLessThan(0.3);
  }

  @Test
  void theHeroWithTheStrongestControlHasTheHighestDisable() {
    double shaman = shadowShaman().disable();

    assertThat(shaman)
        .isGreaterThan(crystalMaiden().disable())
        .isGreaterThan(sven().disable())
        .isGreaterThan(antiMage().disable());
  }

  @Test
  void anAreaStunCountsForInitiationAndAnAllyHealForSaves() {
    assertThat(sven().initiation()).isPositive();
    assertThat(sven().disable()).isPositive();
    assertThat(omniknight().saveHeal()).isGreaterThan(0.5).isGreaterThan(shadowShaman().saveHeal());
  }

  @Test
  void aToughHeroIsMoreDurable() {
    assertThat(axe().durability()).isGreaterThan(crystalMaiden().durability());
    assertThat(sven().durability()).isGreaterThan(antiMage().durability());
  }

  @Test
  void anUltimateCountsMoreThanARegularSkill() {
    HeroConstant hero = HEROES.get("27");
    AbilityConstant shackles = ABILITIES.get("shadow_shaman_shackles");

    FunctionProfile regular = calculator.profile(hero, List.of(new HeldAbility(shackles, false)));
    FunctionProfile ultimate = calculator.profile(hero, List.of(new HeldAbility(shackles, true)));

    assertThat(ultimate.disable()).isGreaterThan(regular.disable());
  }

  @Test
  void theJunglerTagGivesTheJungleFunction() {
    HeroConstant jungler = HeroConstant.builder().roles(List.of("Jungler")).build();

    assertThat(calculator.profile(jungler, List.of()).jungle()).isEqualTo(1.0);
    assertThat(antiMage().jungle()).isZero();
  }

  @Test
  void functionsTheKitCannotTellStayUnknownAndAllValuesAreInRange() {
    for (FunctionProfile p :
        List.of(antiMage(), crystalMaiden(), axe(), shadowShaman(), omniknight(), sven())) {
      assertThat(p.lane()).isNull();
      assertThat(p.visionDuty()).isNull();
      for (RoleFunction f : RoleFunction.values()) {
        if (f != RoleFunction.VISION_DUTY) {
          assertThat(p.of(f)).as(f.name()).isBetween(0.0, 1.0);
        }
      }
    }
  }

  @Test
  void aMissingHeroStillProfilesTheSkillsAndNothingAtAllIsUnknown() {
    List<HeldAbility> skills =
        List.of(new HeldAbility(ABILITIES.get("shadow_shaman_shackles"), false));

    assertThat(calculator.profile(null, skills).disable()).isPositive();
    assertThat(calculator.profile(null, skills).jungle()).isNull();
    assertThat(calculator.profile(null, List.of())).isEqualTo(FunctionProfile.UNKNOWN);
  }
}
