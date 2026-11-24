package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.ako.dbuff.service.ranking.RankingQuery;
import com.ako.dbuff.service.ranking.RankingSort;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared input handling of the skill and item ranking tools. */
abstract class RankingToolBase implements AskTool {

  private final AskToolArgs args;
  private final String includeField;
  private final String excludeField;

  RankingToolBase(AskToolArgs args, String includeField, String excludeField) {
    this.args = args;
    this.includeField = includeField;
    this.excludeField = excludeField;
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> props = new LinkedHashMap<>(PlayerToolSupport.commonProperties(true));
    props.put(
        "sort",
        PlayerToolSupport.enumOf(
            "Order: picks (default), win_rate, or wilson (win rate adjusted for few games).",
            List.of("picks", "win_rate", "wilson")));
    props.put("min_games", PlayerToolSupport.integer("Minimum games, 0-1000. Default 0."));
    props.put("limit", PlayerToolSupport.integer("Rows to return, 1-50. Default 10."));
    props.put(
        "scope",
        PlayerToolSupport.enumOf(
            "players (default) ranks the given players together; all ranks every stored player.",
            List.of("players", "all")));
    props.put(includeField, PlayerToolSupport.stringArray("Only these names. Optional."));
    props.put(excludeField, PlayerToolSupport.stringArray("Leave these names out. Optional."));
    return PlayerToolSupport.schema(props, List.of());
  }

  @Override
  public Object call(JsonNode input, AskContext ctx) {
    return rank(query(input, ctx));
  }

  protected abstract Object rank(RankingQuery query);

  RankingQuery query(JsonNode input, AskContext ctx) {
    Set<Long> playerIds = playerIds(input, ctx);
    PlayerToolSupport.Filters f = PlayerToolSupport.filters(args, input);
    return new RankingQuery(
        playerIds,
        f.from(),
        f.to(),
        args.stringSet(input, includeField),
        args.stringSet(input, excludeField),
        f.heroes(),
        f.gameModes(),
        sort(input),
        args.optionalInt(input, "min_games", 0, 1000).orElse(0),
        args.optionalInt(input, "limit", 1, 50).orElse(10));
  }

  private Set<Long> playerIds(JsonNode input, AskContext ctx) {
    String scope = text(input, "scope");
    if ("all".equals(scope)) {
      return Set.of();
    }
    if (scope != null && !"players".equals(scope)) {
      throw new AskToolException("scope must be players or all, got: " + scope);
    }
    return args.players(input, "players", ctx).stream()
        .map(ResolvedPlayer::accountId)
        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  private RankingSort sort(JsonNode input) {
    String value = text(input, "sort");
    try {
      return RankingSort.parse(value);
    } catch (IllegalArgumentException e) {
      throw new AskToolException("sort must be picks, win_rate or wilson, got: " + value);
    }
  }

  private static String text(JsonNode input, String field) {
    JsonNode node = input == null ? null : input.get(field);
    if (node == null || node.isNull() || node.asText().isBlank()) {
      return null;
    }
    return node.asText().trim().toLowerCase(java.util.Locale.ROOT);
  }
}
