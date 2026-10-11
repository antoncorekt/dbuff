package com.ako.dbuff.service.replay.analysis.fight;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrontlineRankerTest {

  private final FrontlineRanker ranker = new FrontlineRanker();

  @Test
  void rank_putsTheHeroNearestTheEnemyFirstAndComparesWithAttackRange() {
    Map<Integer, int[]> positions =
        Map.of(
            0, new int[] {0, 0},
            1, new int[] {500, 0},
            2, new int[] {-500, 0},
            5, new int[] {3000, 0});
    Map<Integer, Integer> ranges = Map.of(0, 150, 1, 600, 2, 150);

    var result = ranker.rank(positions, ranges);

    assertThat(result.get(1).rank()).isEqualTo(1);
    assertThat(result.get(0).rank()).isEqualTo(2);
    assertThat(result.get(2).rank()).isEqualTo(3);
    assertThat(result.get(1).delta()).as("a ranged hero in front is out of place").isEqualTo(-2);
    assertThat(result.get(0).delta()).as("melee heroes share the first two places").isZero();
    assertThat(result.get(2).delta()).isEqualTo(1);
    assertThat(result).as("a lone hero has no line to stand in").doesNotContainKey(5);
  }

  @Test
  void rank_leavesTheDeltaOutWhenARangeIsUnknown() {
    Map<Integer, int[]> positions =
        Map.of(
            0, new int[] {0, 0},
            1, new int[] {500, 0},
            5, new int[] {3000, 0});

    var result = ranker.rank(positions, Map.of(0, 150));

    assertThat(result.get(1).rank()).isEqualTo(1);
    assertThat(result.get(1).delta()).isNull();
  }
}
