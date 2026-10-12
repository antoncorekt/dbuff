package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SupportAbsentContributorTest {
  private final SupportAbsentContributor contributor =
      new SupportAbsentContributor(new ReasonProperties());

  private static PlayerRoleDomain role(long slot, int role) {
    return PlayerRoleDomain.builder().playerSlot(slot).role(role).build();
  }

  private static FightPlayerDomain row(long slot, String presence) {
    return FightPlayerDomain.builder().playerSlot(slot).presence(presence).build();
  }

  private static FightContext context(List<PlayerRoleDomain> roles, FightPlayerDomain... rows) {
    return new FightContext(
        1, TestModes.ALL_PICK, null, List.of(rows), List.of(), List.of(), roles, List.of());
  }

  @Test
  void eachAbsentSupportGetsAReasonWithRoleAwareEvidence() {
    FightContext context =
        context(
            List.of(role(3, 4), role(4, 5), role(131, 4)),
            row(3, "ABSENT_FAR"),
            row(4, "ABSENT_TELEPORTING"),
            row(131, "ABSENT_BASE"));

    List<Reason> reasons = contributor.contribute(context);

    assertThat(reasons)
        .extracting(Reason::code, Reason::subjectSlot, Reason::severity)
        .containsExactly(
            tuple("SUPPORT_ABSENT", 3L, 0.4),
            tuple("SUPPORT_ABSENT", 4L, 0.3),
            tuple("SUPPORT_ABSENT", 131L, 0.4));
    assertThat(reasons.get(0).evidence())
        .isEqualTo("soft support was not in the fight (absent far)");
    assertThat(reasons.get(1).evidence())
        .isEqualTo("hard support was not in the fight (absent teleporting)");
  }

  @Test
  void aSupportInTheFightDeadWithoutARowOrOfAnotherRoleIsLeftOut() {
    FightContext context =
        context(
            List.of(role(0, 4), role(1, 5), role(2, 4), role(3, 5), role(4, 1)),
            row(0, "ACTIVE"),
            row(1, "PRESENT_PASSIVE"),
            row(2, "ABSENT_DEAD"),
            row(4, "ABSENT_FAR"));

    assertThat(contributor.contribute(context)).isEmpty();
  }

  @Test
  void quietWithoutRoles_andSupportsEveryMode() {
    assertThat(contributor.contribute(context(List.of(), row(3, "ABSENT_FAR")))).isEmpty();
    assertThat(contributor.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(contributor.supports(TestModes.ALL_PICK)).isTrue();
  }
}
