package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightReasonDomain;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns the reasons found for one fight into the rows of {@code fight_reason}. */
final class FightReasonRows {
  private FightReasonRows() {}

  /** Where two reasons have the same scope, team, subject and code, the more severe one is kept. */
  static List<FightReasonDomain> strongestPerSubject(
      long matchId, int fightIndex, List<Reason> reasons, int version) {
    Map<String, Reason> byKey = new LinkedHashMap<>();
    for (Reason reason : reasons) {
      byKey.merge(
          reason.scope() + "/" + reason.team() + "/" + reason.subjectSlot() + "/" + reason.code(),
          reason,
          (a, b) -> a.severity() >= b.severity() ? a : b);
    }
    return byKey.values().stream().map(r -> row(matchId, fightIndex, r, version)).toList();
  }

  private static FightReasonDomain row(long matchId, int fightIndex, Reason reason, int version) {
    return FightReasonDomain.builder()
        .matchId(matchId)
        .fightIndex(fightIndex)
        .subjectSlot(reason.subjectSlot())
        .team(reason.team().name())
        .scope(reason.scope().name())
        .code(reason.code())
        .severity(reason.severity())
        .evidence(reason.evidence())
        .source(reason.source())
        .analysisVersion(version)
        .build();
  }
}
