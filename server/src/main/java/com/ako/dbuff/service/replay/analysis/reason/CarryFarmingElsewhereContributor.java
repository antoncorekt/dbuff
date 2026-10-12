package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A carry or mid who was farming the jungle or a lane while the fight went on without him. */
@Component
@RequiredArgsConstructor
public class CarryFarmingElsewhereContributor implements ReasonContributor {
  static final String CODE = "CARRY_FARMING_ELSEWHERE";
  private static final Map<Activity, String> FARMING =
      Map.of(Activity.JUNGLE_FARM, "farming the jungle", Activity.LANE_FARM, "farming a lane");

  private final ReasonProperties properties;

  @Override
  public boolean supports(GameModeProfile mode) {
    return true;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    return context.roles().stream()
        .filter(r -> r.getPlayerSlot() != null && r.getRole() != null)
        .filter(r -> properties.getCarryFarmingSeverity().containsKey(r.getRole()))
        .map(r -> reason(context, r))
        .filter(Objects::nonNull)
        .toList();
  }

  private Reason reason(FightContext context, PlayerRoleDomain role) {
    FightPlayerDomain player = context.player(role.getPlayerSlot()).orElse(null);
    if (player == null) {
      return null;
    }
    Presence presence = Enums.find(Presence.class, player.getPresence());
    Activity activity = Enums.find(Activity.class, player.getActivityAtStart());
    if (presence == null
        || !presence.isAbsent()
        || activity == null
        || !FARMING.containsKey(activity)) {
      return null;
    }
    return Reason.player(
        Reason.COMMON,
        CODE,
        role.getPlayerSlot(),
        properties.getCarryFarmingSeverity().get(role.getRole()),
        "%s was %s when the fight began and stayed out of it"
            .formatted(RoleLabels.of(role.getRole()), FARMING.get(activity)));
  }
}
