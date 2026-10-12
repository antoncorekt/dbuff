package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A common reason about players of certain final roles, each role with its own severity; the roles
 * a reason applies to are the keys of the severity map.
 */
abstract class FinalRoleReasonContributor implements ReasonContributor {
  private final String code;
  private final Map<Integer, Double> severityByRole;

  FinalRoleReasonContributor(String code, Map<Integer, Double> severityByRole) {
    this.code = code;
    this.severityByRole = severityByRole;
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return true;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    return context.roles().stream()
        .filter(r -> r.getPlayerSlot() != null && r.getRole() != null)
        .filter(r -> severityByRole.containsKey(r.getRole()))
        .map(r -> reason(context, r))
        .filter(Objects::nonNull)
        .toList();
  }

  /** The evidence text when the reason holds for the player; null when it does not. */
  abstract String evidence(FightContext context, PlayerRoleDomain role);

  private Reason reason(FightContext context, PlayerRoleDomain role) {
    String evidence = evidence(context, role);
    return evidence == null
        ? null
        : Reason.player(
            Reason.COMMON,
            code,
            role.getPlayerSlot(),
            severityByRole.get(role.getRole()),
            evidence);
  }
}
