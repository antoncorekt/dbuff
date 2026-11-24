package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.match.MatchDetailService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchDetailsTool implements AskTool {

  private final MatchDetailService matches;
  private final AskToolArgs args;

  @Override
  public String name() {
    return "match_details";
  }

  @Override
  public String description() {
    return "The stored details of one match: result, duration, players, heroes, items, abilities.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of("match_id", Map.of("type", "integer", "description", "The match id")),
        "required",
        List.of("match_id"),
        "additionalProperties",
        false);
  }

  @Override
  public Object call(JsonNode input, AskContext ctx) {
    long matchId = args.requiredLong(input, "match_id");
    return matches
        .findById(matchId)
        .orElseThrow(() -> new AskToolException("match not stored: " + matchId));
  }
}
