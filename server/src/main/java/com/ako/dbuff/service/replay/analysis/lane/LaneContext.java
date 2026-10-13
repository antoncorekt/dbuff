package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.GameTime;
import java.util.List;

/** One side of one lane, for the contributors to explain. */
record LaneContext(
    LaneFacts facts, LanePair pair, TeamSide side, SideTotals own, SideTotals enemy) {

  List<LaneHero> ownHeroes() {
    return pair.side(side);
  }

  List<LaneHero> enemyHeroes() {
    return pair.side(LanePair.other(side));
  }

  boolean isInPair(long slot) {
    LaneHero hero = facts.laneHeroes().get(slot);
    return hero != null && hero.lane() == pair.lane();
  }

  String name(long slot) {
    return facts.name(slot);
  }

  String teamName() {
    return teamName(side);
  }

  String enemyName() {
    return teamName(LanePair.other(side));
  }

  private static String teamName(TeamSide team) {
    return team == TeamSide.RADIANT ? "Radiant" : "Dire";
  }

  /** {@code A}, {@code A and B}, {@code A, B and C}. */
  static String list(List<String> items) {
    if (items.size() <= 1) {
      return String.join("", items);
    }
    return String.join(", ", items.subList(0, items.size() - 1))
        + " and "
        + items.get(items.size() - 1);
  }

  static String clock(double seconds) {
    return GameTime.clock(seconds);
  }
}
