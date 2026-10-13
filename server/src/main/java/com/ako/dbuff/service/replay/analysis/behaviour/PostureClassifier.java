package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.TeamClusters.Point;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Labels one moment of a team from where its living heroes stand; dead heroes are not counted.
 *
 * <ul>
 *   <li>GROUPED: the biggest cluster holds {@code groupedMinHeroes} living heroes, or all of them
 *       when fewer are alive.
 *   <li>SPLIT: not grouped, and at least {@code splitMinAlone} heroes are alone. A hero is alone
 *       when he is outside the cluster, not farming the jungle, and no living teammate is within
 *       the cluster distance of him; so two alone heroes are always far from each other, whether
 *       they stand in different lanes or in one.
 *   <li>MIXED: anything else, for example a group of three and a pair.
 * </ul>
 *
 * A team with fewer living heroes than {@code minLiving} has no label: two or three survivors are
 * not a posture. A group of three and a pair is MIXED, while a group of three and two lone heroes
 * is SPLIT.
 */
public final class PostureClassifier {
  private final TeamClusters clusters;
  private final double distance;
  private final int groupedMinHeroes;
  private final int splitMinAlone;
  private final int minLiving;

  public PostureClassifier(
      TeamClusters clusters,
      double distance,
      int groupedMinHeroes,
      int splitMinAlone,
      int minLiving) {
    this.clusters = clusters;
    this.distance = distance;
    this.groupedMinHeroes = groupedMinHeroes;
    this.splitMinAlone = splitMinAlone;
    this.minLiving = minLiving;
  }

  public Optional<Posture> classify(Map<Long, Point> alive, Set<Long> jungleFarmers) {
    if (alive.size() < minLiving) {
      return Optional.empty();
    }
    Set<Long> grouped = clusters.find(alive).map(TeamCluster::members).orElse(Set.of());
    if (grouped.size() >= Math.min(groupedMinHeroes, alive.size())) {
      return Optional.of(Posture.GROUPED);
    }
    long alone =
        alive.keySet().stream()
            .filter(slot -> !grouped.contains(slot) && !jungleFarmers.contains(slot))
            .filter(slot -> isolated(slot, alive))
            .count();
    return Optional.of(alone >= splitMinAlone ? Posture.SPLIT : Posture.MIXED);
  }

  private boolean isolated(long slot, Map<Long, Point> alive) {
    return alive.entrySet().stream()
        .filter(e -> e.getKey() != slot)
        .allMatch(e -> e.getValue().distanceTo(alive.get(slot)) > distance);
  }
}
