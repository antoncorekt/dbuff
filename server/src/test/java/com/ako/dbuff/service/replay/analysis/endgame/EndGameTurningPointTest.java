package com.ako.dbuff.service.replay.analysis.endgame;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndGameTurningPointTest {
  private static FightDomain fight(int index, double start) {
    return FightDomain.builder().fightIndex(index).startSeconds(start).build();
  }

  private static FightImportanceDomain swing(int index, String winner, long gold) {
    return FightImportanceDomain.builder()
        .fightIndex(index)
        .winningTeam(winner)
        .goldChange(gold)
        .build();
  }

  private static ObjectiveConversionDomain conversion(int index, String winner, String result) {
    return ObjectiveConversionDomain.builder()
        .fightIndex(index)
        .winnerTeam(winner)
        .result(result)
        .objectiveKind("TOWER")
        .objectiveKey("npc_dota_badguys_tower3_top")
        .build();
  }

  private final List<FightDomain> fights =
      List.of(fight(0, 300), fight(1, 1000), fight(2, 1500), fight(3, 2000));

  @Test
  void theFirstConvertedFightAfterThePeakWins() {
    var pick =
        EndGameTurningPoint.pick(
            "RADIANT",
            900,
            fights,
            List.of(swing(1, "RADIANT", 500), swing(2, "RADIANT", 4000), swing(3, "RADIANT", 100)),
            List.of(
                conversion(2, "RADIANT", "CONVERTED"),
                conversion(3, "RADIANT", "CONVERTED"),
                conversion(1, "RADIANT", "MISSED")));

    assertThat(pick).isPresent();
    assertThat(pick.get().kind()).isEqualTo(EndGameTurningPoint.CONVERTED);
    assertThat(pick.get().fight().getFightIndex()).isEqualTo(2);
    assertThat(pick.get().goldChange()).isEqualTo(4000);
  }

  @Test
  void aFightBeforeThePeakOrWonByTheLoserDoesNotCount() {
    var pick =
        EndGameTurningPoint.pick(
            "RADIANT",
            900,
            fights,
            List.of(swing(2, "DIRE", 9000), swing(3, "RADIANT", 700)),
            List.of(conversion(0, "RADIANT", "CONVERTED"), conversion(2, "DIRE", "CONVERTED")));

    assertThat(pick.get().kind()).isEqualTo(EndGameTurningPoint.SWING);
    assertThat(pick.get().fight().getFightIndex()).isEqualTo(3);
  }

  @Test
  void withoutAnObjectiveTheLargestSwingAfterThePeakIsTaken() {
    var pick =
        EndGameTurningPoint.pick(
            "DIRE",
            0,
            fights,
            List.of(swing(0, "DIRE", 300), swing(1, "DIRE", 2500), swing(3, "DIRE", 1200)),
            List.of(conversion(1, "DIRE", "MISSED")));

    assertThat(pick.get().kind()).isEqualTo(EndGameTurningPoint.SWING);
    assertThat(pick.get().fight().getFightIndex()).isEqualTo(1);
    assertThat(pick.get().conversion()).isNull();
    assertThat(pick.get().goldChange()).isEqualTo(2500);
  }

  @Test
  void nothingAfterThePeakGivesNoTurningPoint() {
    assertThat(
            EndGameTurningPoint.pick(
                "RADIANT", 5000, fights, List.of(swing(3, "RADIANT", 900)), List.of()))
        .isEmpty();
  }
}
