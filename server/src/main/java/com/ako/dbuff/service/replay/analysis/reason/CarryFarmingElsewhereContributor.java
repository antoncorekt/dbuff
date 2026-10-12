package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import java.util.Map;
import org.springframework.stereotype.Component;

/** A carry or mid who was farming the jungle or a lane while the fight went on without him. */
@Component
public class CarryFarmingElsewhereContributor extends FinalRoleReasonContributor {
  static final String CODE = "CARRY_FARMING_ELSEWHERE";
  private static final Map<Activity, String> FARMING =
      Map.of(Activity.JUNGLE_FARM, "farming the jungle", Activity.LANE_FARM, "farming a lane");

  public CarryFarmingElsewhereContributor(ReasonProperties properties) {
    super(CODE, properties.getCarryFarmingSeverity());
  }

  @Override
  String evidence(FightContext context, PlayerRoleDomain role) {
    FightPlayerDomain player = context.player(role.getPlayerSlot()).orElse(null);
    if (player == null) {
      return null;
    }
    Presence presence = Enums.find(Presence.class, player.getPresence());
    Activity activity = Enums.find(Activity.class, player.getActivityAtStart());
    if (presence == null
        || !presence.isAbsentAlive()
        || activity == null
        || !FARMING.containsKey(activity)) {
      return null;
    }
    return "%s was %s when the fight began and stayed out of it"
        .formatted(RoleLabels.of(role.getRole()), FARMING.get(activity));
  }
}
