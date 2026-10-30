package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.role.IntentProfileCalculator.HeldAbility;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentProfileOverridesTest {

  private static final HeroConstant HERO = HeroConstant.builder().id("2").build();

  private static RoleOverrides overrides(
      Map<String, Map<RoleFunction, Double>> abilities,
      Map<Long, Map<RoleFunction, Double>> heroes) {
    return new RoleOverrides() {
      @Override
      public Map<RoleFunction, Double> forAbility(String name) {
        return abilities.getOrDefault(name, Map.of());
      }

      @Override
      public Map<RoleFunction, Double> forHero(long heroId) {
        return heroes.getOrDefault(heroId, Map.of());
      }
    };
  }

  private static IntentProfileCalculator calculator(RoleOverrides overrides) {
    return new IntentProfileCalculator(new RoleProfileProperties(), overrides);
  }

  @Test
  void anOverrideAddsToWhatTheConstantsSayAboutASkillWithNoFlags() {
    RoleOverrides call =
        overrides(
            Map.of(
                "axe_berserkers_call",
                Map.of(RoleFunction.DISABLE, 0.5, RoleFunction.INITIATION, 0.6)),
            Map.of());
    List<HeldAbility> held =
        List.of(new HeldAbility("axe_berserkers_call", new AbilityConstant(), false));

    FunctionProfile plain = calculator(RoleOverrides.NONE).profile(HERO, held);
    FunctionProfile overridden = calculator(call).profile(HERO, held);

    assertThat(plain.disable()).isZero();
    assertThat(overridden.disable()).isEqualTo(0.5);
    assertThat(overridden.initiation()).isEqualTo(0.6);
    assertThat(overridden.damage()).isEqualTo(plain.damage());
  }

  @Test
  void anUltimateCountsItsOverrideMoreAndTheSumIsCutAtOne() {
    RoleOverrides save = overrides(Map.of("s", Map.of(RoleFunction.SAVE_HEAL, 0.5)), Map.of());
    HeldAbility regular = new HeldAbility("s", new AbilityConstant(), false);
    HeldAbility ultimate = new HeldAbility("s", new AbilityConstant(), true);

    assertThat(calculator(save).profile(HERO, List.of(regular)).saveHeal()).isEqualTo(0.5);
    assertThat(calculator(save).profile(HERO, List.of(ultimate)).saveHeal()).isEqualTo(0.75);
    assertThat(calculator(save).profile(HERO, List.of(regular, ultimate)).saveHeal()).isEqualTo(1);
  }

  @Test
  void aFunctionTheKitCannotTellBecomesKnownThroughAnOverride() {
    RoleOverrides vision = overrides(Map.of("w", Map.of(RoleFunction.VISION_DUTY, 0.4)), Map.of());
    List<HeldAbility> held = List.of(new HeldAbility("w", new AbilityConstant(), false));

    assertThat(calculator(RoleOverrides.NONE).profile(HERO, held).visionDuty()).isNull();
    assertThat(calculator(vision).profile(HERO, held).visionDuty()).isEqualTo(0.4);
  }

  @Test
  void aHeroOverrideApplies() {
    RoleOverrides tough = overrides(Map.of(), Map.of(2L, Map.of(RoleFunction.DURABILITY, 0.2)));

    double plain = calculator(RoleOverrides.NONE).profile(HERO, List.of()).durability();

    assertThat(calculator(tough).profile(HERO, List.of()).durability()).isEqualTo(plain + 0.2);
  }

  @Test
  void aSkillWithoutANameIsNeverOverridden() {
    RoleOverrides any = overrides(Map.of("x", Map.of(RoleFunction.DISABLE, 1.0)), Map.of());

    assertThat(
            calculator(any)
                .profile(HERO, List.of(new HeldAbility(null, new AbilityConstant(), false)))
                .disable())
        .isZero();
  }

  @Test
  void theCheckedInFileMakesMeepoEarthbindADisable() {
    IntentProfileCalculator real = calculator(new RoleOverrideFile("replay/role-overrides.json"));

    FunctionProfile profile =
        real.profile(
            HERO, List.of(new HeldAbility("meepo_earthbind", new AbilityConstant(), false)));

    assertThat(profile.disable()).isEqualTo(0.4);
  }

  @Test
  void theCheckedInFileGivesLionVoodooADisable() {
    IntentProfileCalculator real = calculator(new RoleOverrideFile("replay/role-overrides.json"));

    FunctionProfile profile =
        real.profile(HERO, List.of(new HeldAbility("lion_voodoo", new AbilityConstant(), false)));

    assertThat(profile.disable()).isGreaterThanOrEqualTo(0.5);
  }
}
