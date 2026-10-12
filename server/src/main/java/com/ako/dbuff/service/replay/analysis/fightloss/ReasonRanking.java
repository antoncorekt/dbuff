package com.ako.dbuff.service.replay.analysis.fightloss;

import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.service.replay.analysis.reason.ReasonScope;
import java.util.Comparator;
import java.util.List;

/**
 * The order in which the reasons of one fight are told, computed on read so a change of rule needs
 * no re-analysis. By severity, which weighs role and presence only (never hero gold); team reasons
 * come after the player reasons unless no player reason is severe enough to stand on its own.
 */
public final class ReasonRanking {
  private ReasonRanking() {}

  public static List<FightReasonDomain> rank(
      List<FightReasonDomain> reasons, double minPlayerSeverity) {
    boolean playerLeads =
        reasons.stream().anyMatch(r -> isPlayer(r) && severity(r) >= minPlayerSeverity);
    Comparator<FightReasonDomain> bySeverity =
        Comparator.comparingDouble(ReasonRanking::severity)
            .reversed()
            .thenComparing(r -> r.getSubjectSlot() == null ? Long.MAX_VALUE : r.getSubjectSlot())
            .thenComparing(FightReasonDomain::getCode);
    Comparator<FightReasonDomain> order =
        playerLeads
            ? Comparator.comparing((FightReasonDomain r) -> isPlayer(r) ? 0 : 1)
                .thenComparing(bySeverity)
            : bySeverity;
    return reasons.stream().sorted(order).toList();
  }

  private static boolean isPlayer(FightReasonDomain reason) {
    return ReasonScope.PLAYER.name().equals(reason.getScope());
  }

  private static double severity(FightReasonDomain reason) {
    return reason.getSeverity() == null ? 0 : reason.getSeverity();
  }
}
