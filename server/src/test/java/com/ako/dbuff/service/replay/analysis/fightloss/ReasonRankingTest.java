package com.ako.dbuff.service.replay.analysis.fightloss;

import com.ako.dbuff.dao.model.FightReasonDomain;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReasonRankingTest {
  private static FightReasonDomain reason(String code, String scope, Long slot, double severity) {
    return FightReasonDomain.builder()
        .code(code)
        .scope(scope)
        .subjectSlot(slot)
        .severity(severity)
        .build();
  }

  @Test
  void aTeamReasonRanksBelowEveryPlayerReasonWhenAPlayerReasonIsSevereEnough() {
    List<FightReasonDomain> ranked =
        ReasonRanking.rank(
            List.of(
                reason("NO_INITIATOR", "TEAM", null, 0.9),
                reason("NO_TP", "PLAYER", 1L, 0.35),
                reason("TOWER", "PLAYER", 2L, 0.5)),
            0.3);

    assertThat(ranked)
        .extracting(FightReasonDomain::getCode)
        .containsExactly("TOWER", "NO_TP", "NO_INITIATOR");
  }

  @Test
  void whenNoPlayerReasonReachesTheMinimumEverythingRanksBySeverityAlone() {
    List<FightReasonDomain> ranked =
        ReasonRanking.rank(
            List.of(
                reason("NO_TP", "PLAYER", 1L, 0.2),
                reason("NO_INITIATOR", "TEAM", null, 0.5),
                reason("TOWER", "PLAYER", 2L, 0.1)),
            0.3);

    assertThat(ranked)
        .extracting(FightReasonDomain::getCode)
        .containsExactly("NO_INITIATOR", "NO_TP", "TOWER");
  }

  @Test
  void equalSeverityIsOrderedBySubjectThenCode_andNothingIsDropped() {
    List<FightReasonDomain> ranked =
        ReasonRanking.rank(
            List.of(
                reason("B", "PLAYER", 3L, 0.5),
                reason("A", "PLAYER", 3L, 0.5),
                reason("C", "PLAYER", 1L, 0.5)),
            0.3);

    assertThat(ranked).extracting(FightReasonDomain::getCode).containsExactly("C", "A", "B");
  }
}
