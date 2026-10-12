package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.role.IntentProfileCalculator.HeldAbility;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** The INTENT profile of each player: his hero and drafted skills looked up in the constants. */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentStageProfiler implements StageProfiler {
  private final HeroKits kits;
  private final IntentProfileCalculator calculator;
  private final RoleOverrides overrides;

  @Override
  public RoleStage stage() {
    return RoleStage.INTENT;
  }

  @Override
  public List<FunctionProfile> profile(List<RolePlayer> team) {
    return team.stream().map(this::profile).toList();
  }

  private FunctionProfile profile(RolePlayer player) {
    HeroConstant hero = player.heroId() == null ? null : kits.hero(player.heroId()).orElse(null);
    List<HeldAbility> held = player.abilities().stream().flatMap(a -> held(a).stream()).toList();
    if (hero == null || held.size() < player.abilities().size()) {
      log.info(
          "Intent profile of slot {} is partial: hero {} {}, {} of {} skills found in the"
              + " constants",
          player.slot(),
          player.heroId(),
          hero == null ? "missing" : "found",
          held.size(),
          player.abilities().size());
    }
    return calculator.profile(hero, held);
  }

  /** A skill the constants lack still counts when an override tells what it does. */
  private Optional<HeldAbility> held(DraftedAbility drafted) {
    Optional<AbilityConstant> constant = kits.ability(drafted.name());
    if (constant.isEmpty() && overrides.forAbility(drafted.name()).isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new HeldAbility(
            drafted.name(), constant.orElseGet(AbilityConstant::new), drafted.ultimate()));
  }
}
