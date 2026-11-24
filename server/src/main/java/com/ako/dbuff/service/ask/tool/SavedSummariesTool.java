package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.ako.dbuff.service.ranking.PlayerStatisticService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavedSummariesTool implements AskTool {

  private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

  private final AskToolArgs args;
  private final PlayerStatisticService service;

  @Override
  public String name() {
    return "saved_summaries";
  }

  @Override
  public String description() {
    return "Saved weekly and monthly statistic summaries of players, newest first, for comparing"
        + " periods. Example: is Ann doing better than in her previous weeks?";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return PlayerToolSupport.schema(PlayerToolSupport.commonProperties(false), List.of());
  }

  @Override
  public Object call(JsonNode input, AskContext ctx) {
    List<ResolvedPlayer> players = args.players(input, "players", ctx);
    LocalDate from = args.optionalDate(input, "from").orElse(null);
    LocalDate to = args.optionalDate(input, "to").orElse(null);
    return players.stream()
        .map(
            p -> {
              Map<String, Object> item = new LinkedHashMap<>();
              item.put("player", p.name());
              item.put("result", summaries(p.accountId(), from, to, ctx));
              return item;
            })
        .toList();
  }

  private Object summaries(Long playerId, LocalDate from, LocalDate to, AskContext ctx) {
    if (from == null && to == null) {
      return service.getPersistedStatistics(playerId);
    }
    return service.getPersistedStatistics(
        playerId, from != null ? from : EARLIEST, to != null ? to : ctx.today());
  }
}
