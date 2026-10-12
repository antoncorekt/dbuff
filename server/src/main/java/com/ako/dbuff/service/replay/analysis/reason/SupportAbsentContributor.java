package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import org.springframework.stereotype.Component;

/** A support who was alive and not in the fight; a dead one is explained by his death. */
@Component
public class SupportAbsentContributor extends FinalRoleReasonContributor {
  static final String CODE = "SUPPORT_ABSENT";

  public SupportAbsentContributor(ReasonProperties properties) {
    super(CODE, properties.getSupportAbsentSeverity());
  }

  @Override
  String evidence(FightContext context, PlayerRoleDomain role) {
    Presence presence = context.presenceOf(role.getPlayerSlot());
    if (presence == null || !presence.isAbsentAlive()) {
      return null;
    }
    return "%s was not in the fight (%s)"
        .formatted(RoleLabels.of(role.getRole()), presence.name().toLowerCase().replace('_', ' '));
  }
}
