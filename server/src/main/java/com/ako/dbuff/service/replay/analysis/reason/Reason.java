package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;

/**
 * One thing that may explain how a fight went, in a shape common to every mode.
 *
 * @param code what happened, such as {@code NO_TP}
 * @param subjectSlot the player the reason is about; null for a team reason
 * @param team the subject's team
 * @param severity 0 (hardly matters) to 1 (explains the fight); a hypothesis, see {@link
 *     ReasonProperties}
 * @param evidence a short text with the facts behind it
 * @param source {@link #COMMON}, or the name of the mode that added the reason
 */
public record Reason(
    String code,
    ReasonScope scope,
    Long subjectSlot,
    TeamSide team,
    double severity,
    String evidence,
    String source) {
  public static final String COMMON = "COMMON";

  public Reason {
    severity = Math.max(0, Math.min(1, severity));
    evidence = Evidence.fit(evidence);
  }

  public static Reason player(
      String source, String code, long slot, double severity, String evidence) {
    return new Reason(
        code, ReasonScope.PLAYER, slot, TeamSide.ofSlot(slot), severity, evidence, source);
  }

  public static Reason team(
      String source, String code, TeamSide team, double severity, String evidence) {
    return new Reason(code, ReasonScope.TEAM, null, team, severity, evidence, source);
  }
}
