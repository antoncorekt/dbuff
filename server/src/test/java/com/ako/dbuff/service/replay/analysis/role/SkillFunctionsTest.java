package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
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

  @Test
  void anAdSkillWithoutConstantsReadsTheBaseSkill() {
    assertThat(functions.main("shadow_shaman_shackles_ad")).contains(RoleFunction.DISABLE);
    assertThat(functions.passive("antimage_mana_break_ad")).isTrue();
  }
}
