package com.ako.dbuff.service.replay.analysis.fight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orders each team's heroes at a fight by how far toward the enemy they stand, and compares that
 * with the order their attack ranges suggest: short range in front, long range behind.
 */
public final class FrontlineRanker {

  /**
   * @param rank 1 is the hero closest to the enemy
   * @param delta rank minus the rank his attack range suggests, zero within a group of equal
   *     ranges; negative means further forward than his range suggests. Teammates with an unknown
   *     range are left out of the comparison, and the hero has none himself when his own range is
   *     unknown.
   */
  public record Position(int rank, Integer delta) {}

  /**
   * @param positions the world position of each hero taking part, by player index
   * @param attackRanges the attack range of those heroes that have one known
   * @return a result for each hero whose team has two or more taking part and an enemy to face
   */
  public Map<Integer, Position> rank(
      Map<Integer, int[]> positions, Map<Integer, Integer> attackRanges) {
    Map<Integer, Position> result = new HashMap<>();
    for (int team = 0; team < 2; team++) {
      List<Integer> own = members(positions, team);
      List<Integer> enemy = members(positions, 1 - team);
      if (own.size() < 2 || enemy.isEmpty()) {
        continue;
      }
      double[] ownCentre = centre(positions, own);
      double[] enemyCentre = centre(positions, enemy);
      double ax = enemyCentre[0] - ownCentre[0];
      double ay = enemyCentre[1] - ownCentre[1];
      double length = Math.hypot(ax, ay);
      if (length == 0) {
        continue;
      }
      List<Integer> byFront = new ArrayList<>(own);
      byFront.sort(
          Comparator.comparingDouble(
                  (Integer p) ->
                      -((positions.get(p)[0] - ownCentre[0]) * ax
                              + (positions.get(p)[1] - ownCentre[1]) * ay)
                          / length)
              .thenComparing(Comparator.naturalOrder()));
      List<Integer> withRange = own.stream().filter(attackRanges::containsKey).toList();
      for (int i = 0; i < byFront.size(); i++) {
        int player = byFront.get(i);
        Integer delta =
            attackRanges.containsKey(player)
                ? delta(player, rankAmong(byFront, withRange, i), withRange, attackRanges)
                : null;
        result.put(player, new Position(i + 1, delta));
      }
    }
    return result;
  }

  /** The place of the hero at {@code index} among those of the line whose range is known. */
  private static int rankAmong(List<Integer> line, List<Integer> known, int index) {
    return (int) line.subList(0, index + 1).stream().filter(known::contains).count();
  }

  private static int delta(int player, int rank, List<Integer> own, Map<Integer, Integer> ranges) {
    int range = ranges.get(player);
    int shorter = (int) own.stream().filter(p -> ranges.get(p) < range).count();
    int notLonger = (int) own.stream().filter(p -> ranges.get(p) <= range).count();
    if (rank < shorter + 1) {
      return rank - (shorter + 1);
    }
    return rank > notLonger ? rank - notLonger : 0;
  }

  private static List<Integer> members(Map<Integer, int[]> positions, int team) {
    return positions.keySet().stream().filter(p -> FightDetector.team(p) == team).sorted().toList();
  }

  private static double[] centre(Map<Integer, int[]> positions, List<Integer> players) {
    double x = players.stream().mapToInt(p -> positions.get(p)[0]).average().orElse(0);
    double y = players.stream().mapToInt(p -> positions.get(p)[1]).average().orElse(0);
    return new double[] {x, y};
  }
}
