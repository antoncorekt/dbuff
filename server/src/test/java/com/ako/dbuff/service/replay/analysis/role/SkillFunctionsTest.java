package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillFunctionsTest {
  private static final Map<String, AbilityConstant> ABILITIES = load();

  private final HeroKits kits =
      new HeroKits() {
        @Override
        public Optional<HeroConstant> hero(long heroId) {
          return Optional.empty();
        }

        @Override
        public Optional<AbilityConstant> ability(String name) {
          return Optional.ofNullable(ABILITIES.get(name));
        }
      };

  private final SkillFunctions functions =
      new SkillFunctions(
          kits, new IntentProfileCalculator(new RoleProfileProperties(), RoleOverrides.NONE));

  private static Map<String, AbilityConstant> load() {
    try (InputStream in =
        SkillFunctionsTest.class.getResourceAsStream(
            "/opendota/constants_profile_abilities.json")) {
      return new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .readValue(in, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void aChanneledStunIsMainlyADisable() {
    assertThat(functions.main("shadow_shaman_shackles")).contains(RoleFunction.DISABLE);
  }

  @Test
  void aSkillTheConstantsDoNotKnowHasNoMainFunction() {
    assertThat(functions.main("no_such_skill")).isEmpty();
    assertThat(functions.main(null)).isEmpty();
  }

  @Test
  void passiveSkillsAreToldByTheirBehaviour() {
    assertThat(functions.passive("antimage_mana_break")).isTrue();
    assertThat(functions.passive("shadow_shaman_shackles")).isFalse();
    assertThat(functions.passive("no_such_skill")).isFalse();
  }

  private static final Map<String, String> CASTABILITY =
      Map.of(
          "switch_stance", "{\"behavior\":\"Hidden\",\"cd\":\"0\",\"mc\":\"0\"}",
          "stone_caller", "{\"behavior\":[\"Hidden\",\"No Target\"],\"cd\":\"0\"}",
          "mars_bulwark", "{\"behavior\":\"Passive\"}",
          "life_stealer_consume", "{\"behavior\":\"Unit Target\",\"cd\":\"0\",\"mc\":\"0\"}",
          "jakiro_liquid_ice",
              "{\"behavior\":[\"Unit Target\",\"Autocast\"],\"cd\":\"0\",\"mc\":\"0\"}",
          "clinkz_searing_arrows", "{\"behavior\":[\"Autocast\"],\"mc\":\"10\"}",
          "broodmother_spin_web", "{\"behavior\":\"Point Target\",\"cd\":\"0\",\"mc\":\"40\"}",
          "storm_spirit_ball_lightning",
              "{\"behavior\":\"Point Target\",\"cd\":\"0\",\"mc\":[\"0\",\"10\"]}",
          "sven_storm_bolt",
              "{\"behavior\":\"Unit Target\",\"cd\":[\"14\",\"13\"],\"mc\":[\"100\",\"110\"]}");

  private SkillFunctions castability() {
    ObjectMapper mapper =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    Map<String, AbilityConstant> all = new java.util.HashMap<>();
    CASTABILITY.forEach(
        (name, json) -> {
          try {
            all.put(name, mapper.readValue(json, AbilityConstant.class));
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        });
    HeroKits own =
        new HeroKits() {
          @Override
          public Optional<HeroConstant> hero(long heroId) {
            return Optional.empty();
          }

          @Override
          public Optional<AbilityConstant> ability(String name) {
            return Optional.ofNullable(all.get(name));
          }
        };
    return new SkillFunctions(
        own, new IntentProfileCalculator(new RoleProfileProperties(), RoleOverrides.NONE));
  }

  @Test
  void skillsWithoutAHandCastAreNotCastable() {
    SkillFunctions skills = castability();
    for (String name :
        List.of(
            "switch_stance",
            "stone_caller",
            "mars_bulwark",
            "life_stealer_consume",
            "jakiro_liquid_ice",
            "clinkz_searing_arrows")) {
      assertThat(skills.notCastable(name)).as(name).isTrue();
    }
  }

  @Test
  void aSkillWithAManaCostOrACooldownIsCastable() {
    SkillFunctions skills = castability();
    assertThat(skills.notCastable("broodmother_spin_web")).isFalse();
    assertThat(skills.notCastable("storm_spirit_ball_lightning")).isFalse();
    assertThat(skills.notCastable("sven_storm_bolt")).isFalse();
    assertThat(skills.notCastable("sven_storm_bolt_ad")).isFalse();
    assertThat(skills.notCastable("mars_bulwark_ad")).isTrue();
    assertThat(skills.notCastable("no_such_skill")).isFalse();
    assertThat(skills.notCastable(null)).isFalse();
  }

  @Test
  void anAdSkillWithoutConstantsReadsTheBaseSkill() {
    assertThat(functions.main("shadow_shaman_shackles_ad")).contains(RoleFunction.DISABLE);
    assertThat(functions.passive("antimage_mana_break_ad")).isTrue();
  }
}
