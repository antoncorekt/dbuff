package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A support who was alive and not in the fight; a dead one is explained by his death. */
@Component
@RequiredArgsConstructor
public class SupportAbsentContributor implements ReasonContributor {
  static final String CODE = "SUPPORT_ABSENT";

  private final ReasonProperties properties;

  @Override
  public boolean supports(GameModeProfile mode) {
    return true;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    return context.roles().stream()
        .filter(r -> r.getPlayerSlot() != null && r.getRole() != null)
        .filter(r -> properties.getSupportAbsentSeverity().containsKey(r.getRole()))
        .map(r -> reason(context, r))
        .filter(Objects::nonNull)
        .toList();
  }

  private Reason reason(FightContext context, PlayerRoleDomain role) {
    Presence presence = context.presenceOf(role.getPlayerSlot());
    if (presence == null || !presence.isAbsent() || presence == Presence.ABSENT_DEAD) {
      return null;
    }
    return Reason.player(
        Reason.COMMON,
        CODE,
        role.getPlayerSlot(),
        properties.getSupportAbsentSeverity().get(role.getRole()),
        "%s was not in the fight (%s)"
            .formatted(
                RoleLabels.of(role.getRole()), presence.name().toLowerCase().replace('_', ' ')));
  }
}
