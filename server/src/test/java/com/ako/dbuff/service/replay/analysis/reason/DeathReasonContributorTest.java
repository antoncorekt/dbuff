package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DeathReasonContributorTest {
  private final DeathReasonContributor contributor =
      new DeathReasonContributor(new ReasonProperties());

  private static DeathAnalysisDomain death(long slot, double at, String reason) {
    return DeathAnalysisDomain.builder()
        .playerSlot(slot)
        .deathSeconds(at)
        .killerKind("HERO")
        .killerName("npc_dota_hero_axe")
        .reason(reason)
        .reincarnated(false)
        .build();
  }

  private static FightContext context(DeathAnalysisDomain... deaths) {
    return new FightContext(
        1, TestModes.ABILITY_DRAFT, null, List.of(), List.of(), List.of(deaths));
  }

  @Test
  void aDeathReasonIsMirroredWithItsSeverityAndTheFirstDeathIsMarked() {
    List<Reason> reasons =
        contributor.contribute(
            context(death(0, 605.4, "BKB_READY_UNUSED"), death(128, 640, "OUT_OF_POSITION")));

    assertThat(reasons)
        .extracting(Reason::code, Reason::subjectSlot, Reason::severity)
        .containsExactlyInAnyOrder(
            tuple("BKB_READY_UNUSED", 0L, 0.8),
            tuple("OUT_OF_POSITION", 128L, 0.7),
            tuple("DIED_FIRST", 0L, 0.5));
    assertThat(reasons.getFirst().evidence()).isEqualTo("died at 10:05 to npc_dota_hero_axe");
  }

  @Test
  void aReasonThatExplainsNothingIsLeftOut_andALoneDeathIsNotFirstOfAnything() {
    List<Reason> reasons = contributor.contribute(context(death(0, 100, "OTHER")));

    assertThat(reasons).isEmpty();
  }

  @Test
  void aReincarnationIsNotADeath() {
    DeathAnalysisDomain aegis = death(0, 100, "REINCARNATED");
    aegis.setReincarnated(true);

    List<Reason> reasons = contributor.contribute(context(aegis, death(1, 120, "BURST")));

    assertThat(reasons).extracting(Reason::code).containsExactly("BURST");
  }
}
