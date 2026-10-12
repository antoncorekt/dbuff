package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AbsenceReasonContributorTest {
  private final AbsenceReasonContributor contributor =
      new AbsenceReasonContributor(new ReasonProperties());

  private static FightPlayerDomain player(long slot, String absence, Integer tpLeft) {
    return FightPlayerDomain.builder()
        .playerSlot(slot)
        .presence("ABSENT_FAR")
        .absenceReason(absence)
        .teleportCooldownLeft(tpLeft)
        .build();
  }

  @Test
  void eachAbsenceReasonBecomesAPlayerReasonWithItsOwnSeverity() {
    FightContext context =
        new FightContext(
            1,
            TestModes.ALL_PICK,
            null,
            List.of(
                player(0, "NO_TP", null),
                player(1, "TP_ON_COOLDOWN", 40),
                player(128, "HAD_TP_NOT_USED", 0),
                player(2, null, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            List.of());

    List<Reason> reasons = contributor.contribute(context);

    assertThat(reasons)
        .extracting(Reason::code, Reason::subjectSlot, Reason::severity)
        .containsExactly(
            tuple("NO_TP", 0L, 0.7),
            tuple("TP_ON_COOLDOWN", 1L, 0.5),
            tuple("HAD_TP_NOT_USED", 128L, 0.8));
    assertThat(reasons.get(1).evidence()).isEqualTo("ABSENT_FAR, teleport ready in 40 s");
    assertThat(reasons.get(2).team().name()).isEqualTo("DIRE");
    assertThat(reasons).allSatisfy(r -> assertThat(r.source()).isEqualTo(Reason.COMMON));
  }

  @Test
  void supportsEveryMode() {
    assertThat(contributor.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(contributor.supports(TestModes.ALL_PICK)).isTrue();
  }

  @Test
  void aRowWithoutPresenceOrSlotOrWithAnUnknownReasonIsSkipped() {
    FightPlayerDomain noPresence = player(0, "NO_TP", null);
    noPresence.setPresence(null);
    FightPlayerDomain noSlot = player(1, "NO_TP", null);
    noSlot.setPlayerSlot(null);
    FightContext context =
        new FightContext(
            1,
            TestModes.ALL_PICK,
            null,
            List.of(noPresence, noSlot, player(2, "TELEPORT_NEW", null), player(3, "NO_TP", null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            List.of());

    assertThat(contributor.contribute(context)).extracting(Reason::subjectSlot).containsExactly(3L);
  }
}
