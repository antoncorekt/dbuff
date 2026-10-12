package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.service.replay.analysis.role.FunctionProfile;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.ako.dbuff.service.replay.analysis.role.RoleStage;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Ability Draft: the best player of the losing team in each of the configured functions (such as
 * initiation, disable and damage), by the INTENT profiles of the drafted skills, stayed low in
 * several of them at once, so the skills did not make a working combination.
 */
@Component
public class WeakCombinationContributor extends ModeReasonContributor {
  static final String CODE = "WEAK_COMBINATION";

  private final ReasonProperties properties;

  public WeakCombinationContributor(ReasonProperties properties) {
    super(AbilityDraftProfile.MODE_ID, ABILITY_DRAFT);
    this.properties = properties;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    TeamSide loser = context.loser().orElse(null);
    if (loser == null) {
      return List.of();
    }
    List<PlayerRoleProfileDomain> team =
        context.profiles().stream()
            .filter(p -> RoleStage.INTENT.name().equals(p.getStage()))
            .filter(p -> p.getPlayerSlot() != null && TeamSide.ofSlot(p.getPlayerSlot()) == loser)
            .toList();
    Map<RoleFunction, Double> low =
        properties.getWeakCombinationFunctions().stream()
            .sorted()
            .map(f -> Map.entry(f, best(team, f)))
            .filter(
                e ->
                    e.getValue().isPresent()
                        && e.getValue().getAsDouble() < properties.getWeakCombinationLow())
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().getAsDouble(),
                    (a, b) -> a,
                    java.util.LinkedHashMap::new));
    if (low.size() < properties.getWeakCombinationMinGaps()) {
      return List.of();
    }
    double severity =
        properties.getWeakCombinationBase()
            + properties.getWeakCombinationStep()
                * (low.size() - properties.getWeakCombinationMinGaps());
    String peaks =
        low.entrySet().stream()
            .map(e -> String.format(Locale.ROOT, "%s %.2f", e.getKey().words(), e.getValue()))
            .collect(Collectors.joining(", "));
    return List.of(
        Reason.team(
            source(),
            CODE,
            loser,
            severity,
            "%s drafted skills whose best player reaches only: %s"
                .formatted(RoleLabels.team(loser), peaks)));
  }

  /** Empty when no player of the team has the function known. */
  private static OptionalDouble best(List<PlayerRoleProfileDomain> team, RoleFunction function) {
    return team.stream()
        .map(p -> FunctionProfile.stored(p).of(function))
        .filter(v -> v != null)
        .mapToDouble(Double::doubleValue)
        .max();
  }
}
