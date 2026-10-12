package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.AbilityDraftPoolDomain;
import com.ako.dbuff.service.replay.analysis.role.FunctionProfile;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.IntentProfileCalculator;
import com.ako.dbuff.service.replay.analysis.role.IntentProfileCalculator.HeldAbility;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Ability Draft: a skill that scores high in a function on its own (read like an intent profile of
 * that one skill) stayed in the pool until the draft ended, which the losing team might have used.
 * The pool does not say whether a skipped skill is an ultimate, so it counts as a regular one.
 */
@Component
public class StrongSkillLeftContributor extends ModeReasonContributor {
  static final String CODE = "STRONG_SKILL_LEFT";

  private final ReasonProperties properties;
  private final HeroKits kits;
  private final IntentProfileCalculator calculator;

  public StrongSkillLeftContributor(
      ReasonProperties properties, HeroKits kits, IntentProfileCalculator calculator) {
    super(AbilityDraftProfile.MODE_ID, ABILITY_DRAFT);
    this.properties = properties;
    this.kits = kits;
    this.calculator = calculator;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    TeamSide loser = context.loser().orElse(null);
    if (!properties.getStrongSkillLeft().isEnabled() || loser == null) {
      return List.of();
    }
    List<String> strong =
        context.draftPool().stream()
            .filter(p -> p.getPickedBySlot() == null && p.getAbilityName() != null)
            .sorted(Comparator.comparing(AbilityDraftPoolDomain::getPoolIndex))
            .map(this::describeIfStrong)
            .flatMap(Optional::stream)
            .toList();
    if (strong.isEmpty()) {
      return List.of();
    }
    double severity =
        properties.getStrongSkillBase() + properties.getStrongSkillStep() * (strong.size() - 1);
    return List.of(
        Reason.team(
            source(),
            CODE,
            loser,
            severity,
            "strong skills nobody drafted: " + String.join(", ", strong)));
  }

  private Optional<String> describeIfStrong(AbilityDraftPoolDomain skipped) {
    return kits.ability(skipped.getAbilityName())
        .map(
            constant ->
                calculator.profile(
                    null, List.of(new HeldAbility(skipped.getAbilityName(), constant, false))))
        .flatMap(profile -> best(profile))
        .map(best -> skipped.getAbilityName() + " (" + best + ")");
  }

  private Optional<String> best(FunctionProfile profile) {
    return properties.getStrongSkillFunctions().stream()
        .filter(f -> value(profile, f) >= properties.getStrongSkillMinValue())
        .max(Comparator.comparingDouble(f -> value(profile, f)))
        .map(RoleFunction::words);
  }

  private static double value(FunctionProfile profile, RoleFunction function) {
    Double value = profile.of(function);
    return value == null ? 0 : value;
  }
}
