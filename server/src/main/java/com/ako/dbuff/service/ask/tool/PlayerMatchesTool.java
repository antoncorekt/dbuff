package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.ako.dbuff.service.ranking.PlayerStatisticService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlayerMatchesTool implements AskTool {

  private static final int DEFAULT_LIMIT = 20;

  private final AskToolArgs args;
  private final PlayerStatisticService service;

  @Override
  public String name() {
    return "player_matches";
  }

  @Override
  public String description() {
    return "Match ids and dates of a player's matches, most recent first. Example: which were Bob's"
        + " last 5 matches on Invoker?";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> props = PlayerToolSupport.commonProperties(true);
    props.put("limit", PlayerToolSupport.integer("Maximum matches, 1-100. Default 20."));
    return PlayerToolSupport.schema(props, List.of());
  }

  @Override
  public Object call(JsonNode input, AskContext ctx) {
    List<ResolvedPlayer> players = args.players(input, "players", ctx);
    PlayerToolSupport.Filters f = PlayerToolSupport.filters(args, input);
    int limit = args.optionalInt(input, "limit", 1, 100).orElse(DEFAULT_LIMIT);
    return players.stream()
        .map(
            p -> {
              Map<String, Object> item = new LinkedHashMap<>();
              item.put("player", p.name());
              item.put(
                  "result",
                  service.getPlayerMatches(
                      p.accountId(), f.from(), f.to(), f.heroes(), f.gameModes(), Set.of(), limit));
              return item;
            })
        .toList();
  }
}
