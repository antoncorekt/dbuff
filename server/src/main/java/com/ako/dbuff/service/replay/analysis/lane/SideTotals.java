package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;

/**
 * What the heroes of one side of a lane did inside their windows, added up. {@code xp} and {@code
 * gold} are null when no hero of the side has the series.
 */
record SideTotals(
    TeamSide team,
    int heroes,
    int lastHits,
    int siegeLastHits,
    int denies,
    double creepScore,
    Double xp,
    Double gold,
    double averageLevel,
    int kills,
    int deaths,
    long damageDealt,
    long damageTaken,
    long towerDamage,
    boolean ownTowerFell,
    boolean enemyTowerFell) {

  static SideTotals of(
      TeamSide team, List<HeroLaneStats> stats, boolean ownTowerFell, boolean enemyTowerFell) {
    return new SideTotals(
        team,
        stats.size(),
        stats.stream().mapToInt(HeroLaneStats::lastHits).sum(),
        stats.stream().mapToInt(HeroLaneStats::siegeLastHits).sum(),
        stats.stream().mapToInt(HeroLaneStats::denies).sum(),
        stats.stream().mapToDouble(HeroLaneStats::creepScore).sum(),
        sum(stats.stream().map(HeroLaneStats::xp).toList()),
        sum(stats.stream().map(HeroLaneStats::gold).toList()),
        stats.stream().mapToInt(HeroLaneStats::level).filter(l -> l > 0).average().orElse(0),
        stats.stream().mapToInt(HeroLaneStats::kills).sum(),
        stats.stream().mapToInt(HeroLaneStats::deaths).sum(),
        stats.stream().mapToLong(HeroLaneStats::damageDealt).sum(),
        stats.stream().mapToLong(HeroLaneStats::damageTaken).sum(),
        stats.stream().mapToLong(HeroLaneStats::towerDamage).sum(),
        ownTowerFell,
        enemyTowerFell);
  }

  private static Double sum(List<Double> values) {
    List<Double> present = values.stream().filter(v -> v != null).toList();
    return present.isEmpty() ? null : present.stream().mapToDouble(Double::doubleValue).sum();
  }
}
