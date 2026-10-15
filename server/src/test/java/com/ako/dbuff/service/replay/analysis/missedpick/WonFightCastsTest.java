package com.ako.dbuff.service.replay.analysis.missedpick;

import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WonFightCastsTest {
  private static FightImportanceDomain fight(int index, String winner) {
    return FightImportanceDomain.builder()
        .fightIndex(index)
        .winningTeam(winner)
        .important(true)
        .build();
  }

  @Test
  void theWinnerIsReadWithoutCaseAndAnUnknownOneSkipsTheFight() {
    WonFightCasts casts =
        new WonFightCasts(
            List.of(fight(0, "dire"), fight(1, " RADIANT "), fight(2, "DRAW"), fight(3, null)),
            List.of());

    assertThat(casts.teamWon(TeamSide.DIRE)).isEqualTo(1);
    assertThat(casts.teamWon(TeamSide.RADIANT)).isEqualTo(1);
  }
}
