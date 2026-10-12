package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CarryFarmingElsewhereContributorTest {
  private final CarryFarmingElsewhereContributor contributor =
      new CarryFarmingElsewhereContributor(new ReasonProperties());

  private static PlayerRoleDomain role(long slot, int role) {
    return PlayerRoleDomain.builder().playerSlot(slot).role(role).build();
  }

  private static FightPlayerDomain row(long slot, String presence, String activity) {
    return FightPlayerDomain.builder()
        .playerSlot(slot)
        .presence(presence)
        .activityAtStart(activity)
        .build();
  }

  private static FightContext context(List<PlayerRoleDomain> roles, FightPlayerDomain... rows) {
    return new FightContext(
        1, TestModes.ALL_PICK, null, List.of(rows), List.of(), List.of(), roles, List.of());
  }

  @Test
  void anAbsentCarryOrMidFarmingJungleOrLaneIsReportedWithTheRolesSeverity() {
    FightContext context =
        context(
            List.of(role(0, 1), role(1, 2), role(128, 1)),
            row(0, "ABSENT_FARMING", "JUNGLE_FARM"),
            row(1, "ABSENT_FAR", "LANE_FARM"),
            row(128, "ABSENT_FARMING", "JUNGLE_FARM"));

    List<Reason> reasons = contributor.contribute(context);

    assertThat(reasons)
        .extracting(Reason::code, Reason::subjectSlot, Reason::severity)
        .containsExactly(
            tuple("CARRY_FARMING_ELSEWHERE", 0L, 0.8),
            tuple("CARRY_FARMING_ELSEWHERE", 1L, 0.6),
            tuple("CARRY_FARMING_ELSEWHERE", 128L, 0.8));
    assertThat(reasons.get(0).evidence())
        .isEqualTo("carry was farming the jungle when the fight began and stayed out of it");
    assertThat(reasons.get(2).team().name()).isEqualTo("DIRE");
  }

  @Test
  void nothingForAPlayerInTheFightNotFarmingOfAnotherRoleOrWithoutRows() {
    FightContext context =
        context(
            List.of(role(0, 1), role(1, 1), role(2, 3), role(3, 1), role(4, 1)),
            row(0, "ACTIVE", "JUNGLE_FARM"),
            row(1, "ABSENT_FAR", "POSITIONED"),
            row(2, "ABSENT_FARMING", "JUNGLE_FARM"),
            row(3, "ABSENT_FAR", null));

    assertThat(contributor.contribute(context)).isEmpty();
  }

  @Test
  void quietWithoutRoles_andSupportsEveryMode() {
    assertThat(contributor.contribute(context(List.of(), row(0, "ABSENT_FAR", "LANE_FARM"))))
        .isEmpty();
    assertThat(contributor.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(contributor.supports(TestModes.ALL_PICK)).isTrue();
  }
}
