package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What one skill is for, read from its constants with the rules of the intent profile. */
@Component
@RequiredArgsConstructor
public class SkillFunctions {
  private static final String PASSIVE = "Passive";
  private static final String AD_SUFFIX = "_ad";

  private final HeroKits kits;
  private final IntentProfileCalculator calculator;

  /**
   * The function with the highest value of the skill alone, counted as a regular skill without a
   * hero body; empty when the skill is unknown or no function has a value above 0. A tie goes to
   * the function listed first.
   */
  public Optional<RoleFunction> main(String abilityName) {
    return constant(abilityName)
        .map(c -> calculator.profile(null, java.util.List.of(held(abilityName, c))))
        .flatMap(SkillFunctions::highest);
  }

  /** Whether the skill is a passive one; a skill the constants do not know is not. */
  public boolean passive(String abilityName) {
    return constant(abilityName)
        .map(c -> c.getBehavior() != null && c.getBehavior().contains(PASSIVE))
        .orElse(false);
  }

  private Optional<AbilityConstant> constant(String abilityName) {
    if (abilityName == null) {
      return Optional.empty();
    }
    Optional<AbilityConstant> own = kits.ability(abilityName);
    if (own.isPresent() || !abilityName.endsWith(AD_SUFFIX)) {
      return own;
    }
    return kits.ability(abilityName.substring(0, abilityName.length() - AD_SUFFIX.length()));
  }

  private static IntentProfileCalculator.HeldAbility held(String name, AbilityConstant constant) {
    return new IntentProfileCalculator.HeldAbility(name, constant, false);
  }

  private static Optional<RoleFunction> highest(FunctionProfile profile) {
    RoleFunction best = null;
    double top = 0;
    for (RoleFunction function : RoleFunction.values()) {
      Double value = function.valueIn(profile);
      if (value != null && value > top) {
        best = function;
        top = value;
      }
    }
    return Optional.ofNullable(best);
  }
}
