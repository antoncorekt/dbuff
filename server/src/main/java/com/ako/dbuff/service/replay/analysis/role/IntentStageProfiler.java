package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.role.IntentProfileCalculator.HeldAbility;
import java.util.List;
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
    List<HeldAbility> held =
        player.abilities().stream()
            .flatMap(
                a -> kits.ability(a.name()).map(c -> new HeldAbility(c, a.ultimate())).stream())
            .toList();
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
}
