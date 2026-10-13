package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.TeamClusters.Point;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.MID;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.ZONES;
import static org.assertj.core.api.Assertions.assertThat;

class TeamClustersTest {
  private final TeamClusters clusters = new TeamClusters(2500, 2, ZONES);

  @Test
  void find_takesAllHeroesWithinTheDistanceOfEachOther() {
    Optional<TeamCluster> found =
        clusters.find(Map.of(0L, new Point(0, 0), 1L, new Point(1000, 0), 2L, new Point(2000, 0)));

    assertThat(found).isPresent();
    assertThat(found.get().members()).containsExactlyInAnyOrder(0L, 1L, 2L);
    assertThat(found.get().x()).isEqualTo(1000.0);
    assertThat(found.get().zone()).isEqualTo(MID);
  }

  @Test
  void find_aChainOfNeighboursIsNotOneGroup() {
    Optional<TeamCluster> found =
        clusters.find(Map.of(0L, new Point(0, 0), 1L, new Point(2000, 0), 2L, new Point(4000, 0)));

    assertThat(found).isPresent();
    assertThat(found.get().members()).hasSize(2);
  }

  @Test
  void find_theBiggerGroupWinsOverATighterPair() {
    Optional<TeamCluster> found =
        clusters.find(
            Map.of(
                0L, new Point(0, 0),
                1L, new Point(50, 0),
                2L, new Point(10000, 0),
                3L, new Point(11000, 0),
                4L, new Point(12000, 0)));

    assertThat(found.get().members()).containsExactlyInAnyOrder(2L, 3L, 4L);
  }

  @Test
  void find_equalGroupsGoToTheTighterOne() {
    Optional<TeamCluster> found =
        clusters.find(
            Map.of(
                0L, new Point(0, 0),
                1L, new Point(2400, 0),
                2L, new Point(10000, 0),
                3L, new Point(10100, 0)));

    assertThat(found.get().members()).containsExactlyInAnyOrder(2L, 3L);
  }

  @Test
  void find_heroesFarApartHaveNoGroup() {
    assertThat(
            clusters.find(
                Map.of(0L, new Point(0, 0), 1L, new Point(5000, 0), 2L, new Point(-5000, 0))))
        .isEmpty();
  }

  @Test
  void find_noHeroesHaveNoGroup() {
    assertThat(clusters.find(Map.of())).isEmpty();
  }
}
