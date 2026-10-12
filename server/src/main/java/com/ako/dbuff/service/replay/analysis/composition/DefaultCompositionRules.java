package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.service.replay.analysis.role.FunctionProfile;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The first hypothesis of the golden model for All Pick and Ability Draft: a team wants one
 * carry-like farm engine, an initiator, a disable, wave clear, a frontline, saves and (after the
 * match) vision. A finding that depends on a function some player's profile does not know is not
 * made from that gap; the function is reported unknown instead.
 */
@Component
@RequiredArgsConstructor
public class DefaultCompositionRules implements CompositionRules {
  private static final Set<Long> MODES =
      Set.of(AllPickProfile.MODE_ID, AbilityDraftProfile.MODE_ID);

  private enum Presence {
    PRESENT,
    ABSENT,
    UNKNOWN
  }

  private final CompositionProperties properties;

  @Override
  public boolean supports(GameModeProfile mode) {
    return MODES.contains(mode.gameModeId());
  }

  @Override
  public CompositionVerdict evaluate(CompositionStage stage, List<TeamMember> team) {
    List<String> tokens = new ArrayList<>();
    Set<RoleFunction> unknown = new LinkedHashSet<>();
    farm(values(team, FunctionProfile::farmPriority), tokens, unknown);
    for (CompositionToken token : CompositionToken.values()) {
      if (token.function() == null || (token.finalOnly() && stage != CompositionStage.FINAL)) {
        continue;
      }
      List<Double> values = values(team, p -> p.of(token.function()));
      switch (presence(values, properties.getMinimum().get(token.function()))) {
        case ABSENT -> tokens.add(token.name());
        case UNKNOWN -> unknown.add(token.function());
        case PRESENT -> {}
      }
    }
    if (stage == CompositionStage.FINAL
        && changedRoles(team) >= properties.getIntentNotPlayedPlayers()) {
      tokens.add(CompositionToken.INTENT_NOT_PLAYED.name());
    }
    return new CompositionVerdict(List.copyOf(tokens), List.copyOf(unknown));
  }

  /** Everything judged from farm priority; with any player's value unknown, none of it is. */
  private void farm(List<Double> farm, List<String> tokens, Set<RoleFunction> unknown) {
    if (hasUnknown(farm)) {
      unknown.add(RoleFunction.FARM_PRIORITY);
      return;
    }
    long carries = farm.stream().filter(f -> f >= properties.getCarryFarm()).count();
    long supports = farm.stream().filter(f -> f <= properties.getSupportFarm()).count();
    if (carries == 0 || carries >= properties.getCarriesNotedFrom()) {
      tokens.add("CARRIES_" + carries);
    }
    if (supports == 0 || supports >= properties.getSupportsNotedFrom()) {
      tokens.add("SUPPORTS_" + supports);
    }
    if (presence(farm, properties.getFarmEngine()) == Presence.ABSENT) {
      tokens.add(CompositionToken.NO_FARM_ENGINE.name());
    }
    if (farm.stream().allMatch(f -> f >= properties.getTopHeavyFarm())) {
      tokens.add(CompositionToken.TOP_HEAVY.name());
    }
  }

  private static boolean hasUnknown(List<Double> values) {
    return values.stream().anyMatch(Objects::isNull);
  }

  private static long changedRoles(List<TeamMember> team) {
    return team.stream()
        .filter(m -> m.intentRole() != null && !m.intentRole().equals(m.role()))
        .count();
  }

  private static List<Double> values(
      List<TeamMember> team, Function<FunctionProfile, Double> reader) {
    return team.stream().map(m -> reader.apply(m.profile())).toList();
  }

  private static Presence presence(List<Double> values, double minimum) {
    if (values.stream().filter(Objects::nonNull).anyMatch(v -> v >= minimum)) {
      return Presence.PRESENT;
    }
    return hasUnknown(values) ? Presence.UNKNOWN : Presence.ABSENT;
  }
}
