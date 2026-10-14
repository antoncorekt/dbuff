package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.GameStateSegmentDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter.State;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectiveFightsTest {
  private static FightImportanceDomain importance(int index, String winner, boolean important) {
    return FightImportanceDomain.builder()
        .fightIndex(index)
        .winningTeam(winner)
        .important(important)
        .build();
  }

  private static FightDomain fight(int index, Double end) {
    return FightDomain.builder().fightIndex(index).startSeconds(100.0).endSeconds(end).build();
  }

  private static GameStateSegmentDomain segment(String team, int from, int to, String state) {
    return GameStateSegmentDomain.builder()
        .team(team)
        .startSeconds(from)
        .endSeconds(to)
        .state(state)
        .build();
  }

  @Test
  void of_keepsOnlyImportantFightsWithAKnownWinnerAndAStoredFight() {
    List<ObjectiveFight> fights =
        ObjectiveFights.of(
            List.of(
                importance(2, "DIRE", true),
                importance(1, "RADIANT", true),
                importance(3, "RADIANT", false),
                importance(4, null, true),
                importance(5, "RADIANT", true)),
            List.of(fight(1, 130.0), fight(2, null), fight(3, 130.0), fight(4, 130.0)),
            List.of());

    assertThat(fights).extracting(ObjectiveFight::index).containsExactly(1, 2);
    assertThat(fights.get(0).endSeconds()).isEqualTo(130);
    assertThat(fights.get(1).endSeconds()).isEqualTo(100);
    assertThat(fights.get(1).winner()).isEqualTo(TeamSide.DIRE);
  }

  @Test
  void of_theStateBeforeIsTheWinnersSegmentAtTheStartOfTheFight() {
    List<ObjectiveFight> fights =
        ObjectiveFights.of(
            List.of(importance(1, "RADIANT", true)),
            List.of(fight(1, 130.0)),
            List.of(
                segment("RADIANT", 0, 60, "EVEN"),
                segment("RADIANT", 60, 300, "BEHIND"),
                segment("DIRE", 60, 300, "AHEAD")));

    assertThat(fights.getFirst().stateBefore()).isEqualTo(State.BEHIND);
  }

  @Test
  void of_noSegmentAtTheStartMeansNoState() {
    assertThat(
            ObjectiveFights.of(
                    List.of(importance(1, "RADIANT", true)),
                    List.of(fight(1, 130.0)),
                    List.of(segment("RADIANT", 200, 300, "AHEAD")))
                .getFirst()
                .stateBefore())
        .isNull();
  }

  @Test
  void of_noImportanceStoredMeansNoFights() {
    assertThat(ObjectiveFights.of(List.of(), List.of(fight(1, 130.0)), List.of())).isEmpty();
  }
}
