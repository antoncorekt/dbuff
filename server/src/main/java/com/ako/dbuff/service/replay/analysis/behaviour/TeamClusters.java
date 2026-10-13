package com.ako.dbuff.service.replay.analysis.behaviour;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the biggest group of a team's alive heroes in which every two are within the cluster
 * distance. On a tie the tighter group wins, then the lower slots, so the answer is stable.
 */
public final class TeamClusters {
  public record Point(double x, double y) {
    public double distanceTo(Point other) {
      return Math.hypot(x - other.x, y - other.y);
    }
  }

  private final double distance;
  private final int minSize;
  private final ZoneLookup zones;

  public TeamClusters(double distance, int minSize, ZoneLookup zones) {
    this.distance = distance;
    if (minSize < 2) {
      throw new IllegalArgumentException("A cluster needs at least 2 heroes, not " + minSize);
    }
    this.minSize = minSize;
    this.zones = zones;
  }

  public Optional<TeamCluster> find(Map<Long, Point> alive) {
    List<Long> slots = alive.keySet().stream().sorted().toList();
    List<Long> best = List.of();
    double bestSpread = Double.MAX_VALUE;
    for (int mask = 1; mask < 1 << slots.size(); mask++) {
      if (Integer.bitCount(mask) < Math.max(minSize, best.size())) {
        continue;
      }
      List<Long> group = pick(slots, mask);
      if (!allClose(group, alive)) {
        continue;
      }
      double spread = spread(group, alive);
      if (group.size() > best.size() || spread < bestSpread) {
        best = group;
        bestSpread = spread;
      }
    }
    if (best.isEmpty()) {
      return Optional.empty();
    }
    Point centre = centre(best, alive);
    return Optional.of(
        new TeamCluster(
            new HashSet<>(best), centre.x(), centre.y(), zones.at(centre.x(), centre.y())));
  }

  private static List<Long> pick(List<Long> slots, int mask) {
    List<Long> group = new ArrayList<>();
    for (int i = 0; i < slots.size(); i++) {
      if ((mask & (1 << i)) != 0) {
        group.add(slots.get(i));
      }
    }
    return group;
  }

  private boolean allClose(List<Long> group, Map<Long, Point> alive) {
    for (int i = 0; i < group.size(); i++) {
      for (int j = i + 1; j < group.size(); j++) {
        if (alive.get(group.get(i)).distanceTo(alive.get(group.get(j))) > distance) {
          return false;
        }
      }
    }
    return true;
  }

  private static Point centre(List<Long> group, Map<Long, Point> alive) {
    double x = group.stream().mapToDouble(s -> alive.get(s).x()).average().orElse(0);
    double y = group.stream().mapToDouble(s -> alive.get(s).y()).average().orElse(0);
    return new Point(x, y);
  }

  private static double spread(List<Long> group, Map<Long, Point> alive) {
    Point c = centre(group, alive);
    return group.stream().mapToDouble(s -> alive.get(s).distanceTo(c)).sum();
  }
}
