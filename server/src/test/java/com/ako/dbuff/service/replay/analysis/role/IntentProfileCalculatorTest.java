package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.constant.data.AbilityAttribute;
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
      new IntentProfileCalculator(new RoleProfileProperties(), RoleOverrides.NONE);

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
      held.add(new HeldAbility(skills[i], ABILITIES.get(skills[i]), i == skills.length - 1));
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

    FunctionProfile regular =
        calculator.profile(
            hero, List.of(new HeldAbility("shadow_shaman_shackles", shackles, false)));
    FunctionProfile ultimate =
        calculator.profile(
            hero, List.of(new HeldAbility("shadow_shaman_shackles", shackles, true)));

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
        List.of(
            new HeldAbility(
                "shadow_shaman_shackles", ABILITIES.get("shadow_shaman_shackles"), false));

    assertThat(calculator.profile(null, skills).disable()).isPositive();
    assertThat(calculator.profile(null, skills).jungle()).isNull();
    assertThat(calculator.profile(null, List.of())).isEqualTo(FunctionProfile.UNKNOWN);
  }

  @Test
  void aSelfChannelIsNotControlButAChannelOnAnEnemyIs() {
    AbilityConstant teleport =
        AbilityConstant.builder()
            .behavior(List.of("Point Target", "Channeled"))
            .cd(List.of(60.0))
            .attrib(List.of(new AbilityAttribute("channel_time", List.of("3"))))
            .build();

    FunctionProfile profile =
        calculator.profile(HEROES.get("1"), List.of(new HeldAbility("teleport", teleport, false)));

    assertThat(profile.disable()).isZero();
    assertThat(
            calculator
                .profile(
                    HEROES.get("27"),
                    List.of(
                        new HeldAbility(
                            "shadow_shaman_shackles",
                            ABILITIES.get("shadow_shaman_shackles"),
                            false)))
                .disable())
        .isPositive();
  }

  @Test
  void initiationAndDisableHaveTheirOwnScales() {
    RoleProfileProperties properties = new RoleProfileProperties();
    properties.getIntent().setInitiationFull(0.1);
    List<HeldAbility> bolt =
        List.of(new HeldAbility("sven_storm_bolt", ABILITIES.get("sven_storm_bolt"), false));

    FunctionProfile tuned =
        new IntentProfileCalculator(properties, RoleOverrides.NONE).profile(HEROES.get("18"), bolt);
    FunctionProfile normal = calculator.profile(HEROES.get("18"), bolt);

    assertThat(tuned.initiation()).isGreaterThan(normal.initiation());
    assertThat(tuned.disable()).isEqualTo(normal.disable());
  }

  private FunctionProfile holding(String json) {
    try {
      AbilityConstant ability = MAPPER.readValue(json, AbilityConstant.class);
      return calculator.profile(HEROES.get("1"), List.of(new HeldAbility("x", ability, false)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void aRangedStunCountsAsInitiationWhateverItsCooldown() {
    FunctionProfile stun =
        holding(
            """
            {"behavior":"Point Target","target_team":"Enemy","cd":[20],
             "attrib":[{"key":"stun_duration","value":["1.5"]},
                       {"key":"abilitycastrange","value":["1100"]}]}\
            """);

    assertThat(stun.initiation()).isGreaterThanOrEqualTo(0.4);
  }

  @Test
  void aShortRangeStunOrASlowIsNotInitiation() {
    FunctionProfile close =
        holding(
            """
            {"behavior":"Unit Target","target_team":"Enemy","cd":[20],
             "attrib":[{"key":"stun_duration","value":["1.5"]},
                       {"key":"abilitycastrange","value":["150"]}]}\
            """);
    FunctionProfile slow =
        holding(
            """
            {"behavior":"Unit Target","target_team":"Enemy","cd":[20],
             "attrib":[{"key":"slow","value":["50"]},{"key":"abilitycastrange","value":["900"]}]}\
            """);

    assertThat(close.initiation()).isLessThan(0.4);
    assertThat(slow.initiation()).isZero();
  }

  @Test
  void anAreaHealWithNoTargetAndADamageBlockOnAnAllyCountAsSaves() {
    FunctionProfile area =
        holding(
            """
            {"behavior":"No Target","cd":[35],"attrib":[{"key":"heal","value":["4","8"]}]}\
            """);
    FunctionProfile block =
        holding(
            """
            {"behavior":["Unit Target"],"target_team":"Friendly","cd":[20],
             "attrib":[{"key":"heal_per_second","value":["4"]},
                       {"key":"damage_block_base","value":["60"]}]}\
            """);
    FunctionProfile passive =
        holding(
            """
            {"behavior":"Passive","attrib":[{"key":"bonus_hp_regen","value":["7"]}]}\
            """);

    assertThat(area.saveHeal()).isGreaterThanOrEqualTo(0.3);
    assertThat(block.saveHeal()).isGreaterThanOrEqualTo(0.3);
    assertThat(passive.saveHeal()).isZero();
  }

  @Test
  void anAttackSpeedSkillAnAttackModifierAndAPassiveDamageCountAsDamageAndFarm() {
    FunctionProfile none = holding("{\"behavior\":\"Point Target\",\"cd\":[10],\"attrib\":[]}");
    FunctionProfile speed =
        holding(
            """
            {"behavior":"Unit Target","cd":[50],
             "attrib":[{"key":"bonus_attack_speed","value":["350"]}]}\
            """);
    FunctionProfile modifier =
        holding(
            """
            {"behavior":["Unit Target","Autocast","Attack Modifier"],"cd":[4],"attrib":[]}\
            """);
    FunctionProfile passive =
        holding(
            """
            {"behavior":"Passive","attrib":[{"key":"damage","value":["20","110"]}]}\
            """);

    for (FunctionProfile p : List.of(speed, modifier, passive)) {
      assertThat(p.damage()).isGreaterThan(none.damage());
      assertThat(p.farmPriority()).isGreaterThan(none.farmPriority());
    }
  }
}
