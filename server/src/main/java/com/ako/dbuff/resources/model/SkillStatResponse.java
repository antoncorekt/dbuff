package com.ako.dbuff.resources.model;

import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatKind;
import java.util.List;

/** Stored skill statistics, grouped by player for a match or as one list for a hero. */
public record SkillStatResponse(Long matchId, List<PlayerStats> players) {

  public record PlayerStats(Long playerSlot, Long heroId, List<Stat> stats) {}

  /**
   * One figure. {@code liftPoints} is set for pairs with a baseline; {@code rankScore} is the
   * Wilson lower bound the list is ordered by and not a win rate.
   */
  public record Stat(
      SkillStatKind kind,
      Long firstId,
      Long secondId,
      Long matchId,
      long games,
      long wins,
      Double winRate,
      Double liftPoints,
      double rankScore,
      Integer windowMonths,
      String text) {}
}
