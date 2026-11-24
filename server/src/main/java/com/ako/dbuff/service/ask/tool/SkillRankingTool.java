package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ranking.AbilityRankingService;
import com.ako.dbuff.service.ranking.RankingQuery;
import org.springframework.stereotype.Component;

@Component
public class SkillRankingTool extends RankingToolBase {

  private final AbilityRankingService service;

  public SkillRankingTool(AskToolArgs args, AbilityRankingService service) {
    super(args, "skills", "exclude_skills");
    this.service = service;
  }

  @Override
  public String name() {
    return "skill_ranking";
  }

  @Override
  public String description() {
    return "Ranks skills (abilities) by pick count, win rate or Wilson score for players or everyone."
        + " Pick counts are player-games. Example: what are my 10 skills by win rate?";
  }

  @Override
  protected Object rank(RankingQuery query) {
    return service.getAbilityRankings(query);
  }
}
