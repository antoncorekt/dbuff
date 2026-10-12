package com.ako.dbuff.service.replay.analysis.fightloss;

import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.service.replay.analysis.fightloss.ReasonRanking.Ranking;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReasonRankingTest {
  private static final double ONE_HOUR = 3600;

  private static List<FightReasonDomain> rank(List<FightReasonDomain> reasons, double start) {
    return ranking(reasons, start).ranked().stream().map(ReasonRanking.Entry::reason).toList();
  }

  private static Ranking ranking(List<FightReasonDomain> reasons, double start) {
    return ReasonRanking.rank(reasons, start, new FightLossProperties());
  }

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
        rank(
            List.of(
                reason("NO_INITIATOR", "TEAM", null, 0.9),
                reason("NO_TP", "PLAYER", 1L, 0.35),
                reason("TOWER", "PLAYER", 2L, 0.5)),
            ONE_HOUR);

    assertThat(ranked)
        .extracting(FightReasonDomain::getCode)
        .containsExactly("TOWER", "NO_TP", "NO_INITIATOR");
  }

  @Test
  void whenNoPlayerReasonReachesTheMinimumEverythingRanksBySeverityAlone() {
    List<FightReasonDomain> ranked =
        rank(
            List.of(
                reason("NO_TP", "PLAYER", 1L, 0.2),
                reason("NO_INITIATOR", "TEAM", null, 0.5),
                reason("TOWER", "PLAYER", 2L, 0.1)),
            ONE_HOUR);

    assertThat(ranked)
        .extracting(FightReasonDomain::getCode)
        .containsExactly("NO_INITIATOR", "NO_TP", "TOWER");
  }

  @Test
  void equalSeverityIsOrderedBySubjectThenCode_andNothingIsDropped() {
    List<FightReasonDomain> ranked =
        rank(
            List.of(
                reason("B", "PLAYER", 3L, 0.5),
                reason("A", "PLAYER", 3L, 0.5),
                reason("C", "PLAYER", 1L, 0.5)),
            ONE_HOUR);

    assertThat(ranked).extracting(FightReasonDomain::getCode).containsExactly("C", "A", "B");
  }

  @Test
  void oneHeroOneAbsenceStory_theStrongestIsTheEntryAndTheOthersAreItsAlso() {
    Ranking ranking =
        ranking(
            List.of(
                reason("TP_ON_COOLDOWN", "PLAYER", 1L, 0.5),
                reason("NO_TP", "PLAYER", 1L, 0.7),
                reason("SUPPORT_ABSENT", "PLAYER", 1L, 0.6),
                reason("NO_TP", "PLAYER", 2L, 0.4),
                reason("TOWER", "PLAYER", 1L, 0.45)),
            ONE_HOUR);

    assertThat(ranking.ranked())
        .extracting(e -> e.reason().getCode() + e.reason().getSubjectSlot() + e.also())
        .containsExactly("NO_TP1[SUPPORT_ABSENT, TP_ON_COOLDOWN]", "TOWER1[]", "NO_TP2[]");
  }

  @Test
  void draftLevelReasonsRankAfterEveryOtherWhateverTheirSeverity() {
    List<FightReasonDomain> ranked =
        rank(
            List.of(
                reason("WEAK_COMBINATION", "TEAM", null, 0.95),
                reason("INTENT_NOT_PLAYED", "PLAYER", 1L, 0.9),
                reason("NO_INITIATOR", "TEAM", null, 0.1),
                reason("TOWER", "PLAYER", 2L, 0.05)),
            ONE_HOUR);

    assertThat(ranked)
        .extracting(FightReasonDomain::getCode)
        .containsExactly("NO_INITIATOR", "TOWER", "WEAK_COMBINATION", "INTENT_NOT_PLAYED");
  }

  @Test
  void cooldownReasonsAreNotRankedInAnEarlyFightButStayListed() {
    List<FightReasonDomain> reasons =
        List.of(
            reason("ABILITY_ON_COOLDOWN", "PLAYER", 1L, 0.9),
            reason("ITEM_ON_COOLDOWN", "PLAYER", 1L, 0.8),
            reason("TOWER", "PLAYER", 2L, 0.1));

    Ranking early = ranking(reasons, 299);
    Ranking later = ranking(reasons, 300);

    assertThat(early.ranked()).extracting(e -> e.reason().getCode()).containsExactly("TOWER");
    assertThat(early.notRanked())
        .extracting(FightReasonDomain::getCode)
        .containsExactly("ABILITY_ON_COOLDOWN", "ITEM_ON_COOLDOWN");
    assertThat(later.ranked()).hasSize(3);
    assertThat(later.notRanked()).isEmpty();
  }

  @Test
  void theThreeRulesCombine() {
    Ranking ranking =
        ranking(
            List.of(
                reason("STRONG_SKILL_LEFT", "PLAYER", 1L, 0.9),
                reason("ABILITY_ON_COOLDOWN", "PLAYER", 2L, 0.9),
                reason("NO_TP", "PLAYER", 3L, 0.3),
                reason("HAD_TP_NOT_USED", "PLAYER", 3L, 0.5)),
            100);

    assertThat(ranking.ranked())
        .extracting(e -> e.reason().getCode() + e.also())
        .containsExactly("HAD_TP_NOT_USED[NO_TP]", "STRONG_SKILL_LEFT[]");
    assertThat(ranking.notRanked())
        .extracting(FightReasonDomain::getCode)
        .containsExactly("ABILITY_ON_COOLDOWN");
  }

  @Test
  void equalReasonsAreOrderedTheSameWhateverTheInputOrder() {
    List<FightReasonDomain> reasons =
        new java.util.ArrayList<>(
            List.of(
                reason("NO_DISABLE", "TEAM", null, 0.5),
                reason("NO_INITIATOR", "TEAM", null, 0.5),
                reason("NO_SAVE", "TEAM", null, 0.5),
                reason("NO_DISABLE", "TEAM", null, 0.5)));
    List<String> first = rank(reasons, ONE_HOUR).stream().map(FightReasonDomain::getCode).toList();

    for (int i = 0; i < 5; i++) {
      java.util.Collections.shuffle(reasons, new java.util.Random(i));
      assertThat(rank(reasons, ONE_HOUR).stream().map(FightReasonDomain::getCode).toList())
          .isEqualTo(first);
    }
    assertThat(first).containsExactly("NO_DISABLE", "NO_DISABLE", "NO_INITIATOR", "NO_SAVE");
  }

  @Test
  void anUnrankedEarlyCooldownDoesNotMakeAPlayerLead() {
    Ranking ranking =
        ranking(
            List.of(
                reason("ABILITY_ON_COOLDOWN", "PLAYER", 1L, 0.9),
                reason("NO_INITIATOR", "TEAM", null, 0.8),
                reason("TOWER", "PLAYER", 2L, 0.1)),
            100);

    assertThat(ranking.ranked())
        .extracting(e -> e.reason().getCode())
        .containsExactly("NO_INITIATOR", "TOWER");
  }
}
