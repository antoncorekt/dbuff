package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.ako.dbuff.service.ranking.ItemRankingService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ItemComboStatsTool implements AskTool {

  private final AskToolArgs args;
  private final ItemRankingService service;

  @Override
  public String name() {
    return "item_combo_stats";
  }

  @Override
  public String description() {
    return "Games and win rate of one player when they own all the given items in a game. Example:"
        + " how often do I win with Blink Dagger and Black King Bar together?";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> props = new LinkedHashMap<>(PlayerToolSupport.commonProperties(true));
    props.put("items", PlayerToolSupport.stringArray("Item names, one or more."));
    return PlayerToolSupport.schema(props, List.of("items"));
  }

  @Override
  public Object call(JsonNode input, AskContext ctx) {
    Set<String> items = args.stringSet(input, "items");
    if (items.isEmpty()) {
      throw new AskToolException("items is required: give one or more item names");
    }
    ResolvedPlayer player = ComboPlayers.single(args, input, ctx);
    PlayerToolSupport.Filters f = PlayerToolSupport.filters(args, input);
    return service.getItemComboStatistics(
        player.accountId(), f.from(), f.to(), items, f.heroes(), f.gameModes());
  }
}
