package com.ako.dbuff.service.replay.analysis.skillstat;

/** Games played and won by the players that matched a question. */
public record SkillCounts(long games, long wins) {
  public static final SkillCounts NONE = new SkillCounts(0, 0);

  public SkillCounts minus(SkillCounts other) {
    return new SkillCounts(games - other.games, wins - other.wins);
  }
}
