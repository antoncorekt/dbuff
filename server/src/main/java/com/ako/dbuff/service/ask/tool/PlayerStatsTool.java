package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.ako.dbuff.service.ranking.PlayerStatisticService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlayerStatsTool implements AskTool {

  private final AskToolArgs args;
  private final PlayerStatisticService service;

  @Override
  public String name() {
    return "player_stats";
  }

  @Override
  public String description() {
    return "Aggregated statistics of one or more players over a period: matches, win rate, averages and"
        + " most played heroes. Example: how did Ann do in the last month?";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> props = PlayerToolSupport.commonProperties(true);
    props.put(
        "hero_limit", PlayerToolSupport.integer("Number of popular heroes, 1-20. Default 3."));
    return PlayerToolSupport.schema(props, List.of());
  }

  @Override
  public Object call(JsonNode input, AskContext ctx) {
    List<ResolvedPlayer> players = args.players(input, "players", ctx);
    PlayerToolSupport.Filters f = PlayerToolSupport.filters(args, input);
    Integer heroLimit = args.optionalInt(input, "hero_limit", 1, 20).orElse(null);
    return players.stream()
        .map(
            p -> {
              Map<String, Object> item = new LinkedHashMap<>();
              item.put("player", p.name());
              item.put(
                  "result",
                  service.getPlayerStatistics(
                      p.accountId(), f.from(), f.to(), heroLimit, f.heroes(), f.gameModes()));
              return item;
            })
        .toList();
  }
}
