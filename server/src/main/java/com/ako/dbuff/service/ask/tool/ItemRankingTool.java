package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ranking.ItemRankingService;
import com.ako.dbuff.service.ranking.RankingQuery;
import org.springframework.stereotype.Component;

@Component
public class ItemRankingTool extends RankingToolBase {

  private final ItemRankingService service;

  public ItemRankingTool(AskToolArgs args, ItemRankingService service) {
    super(args, "items", "exclude_items");
    this.service = service;
  }

  @Override
  public String name() {
    return "item_ranking";
  }

  @Override
  public String description() {
    return "Ranks items by pick count, win rate or Wilson score for players or everyone. Pick counts"
        + " are player-games. Example: which items does Bob win most with?";
  }

  @Override
  protected Object rank(RankingQuery query) {
    return service.getItemRankings(query);
  }
}
