package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.replay.read.AnalysisAssembler;
import com.ako.dbuff.service.replay.read.InvalidSectionsException;
import com.ako.dbuff.service.replay.read.MatchNotStoredException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchAnalysisTool implements AskTool {

  static final List<String> SECTIONS =
      List.of(
          "run",
          "lanes",
          "roles",
          "fights",
          "outliers",
          "behaviour",
          "endgame",
          "items",
          "runes",
          "vision");

  private final AnalysisAssembler assembler;
  private final AskToolArgs args;

  @Override
  public String name() {
    return "match_analysis";
  }

  @Override
  public String description() {
    return "The replay analysis of one match, by section. Omit sections for all of them.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "match_id",
            Map.of("type", "integer", "description", "The match id"),
            "sections",
            Map.of("type", "array", "items", Map.of("type", "string", "enum", SECTIONS))),
        "required",
        List.of("match_id"),
        "additionalProperties",
        false);
  }

  @Override
  public Object call(JsonNode input, AskContext ctx) {
    long matchId = args.requiredLong(input, "match_id");
    Set<String> sections = args.stringSet(input, "sections");
    String csv = sections.isEmpty() ? null : String.join(",", sections);
    try {
      return assembler.assemble(matchId, csv);
    } catch (MatchNotStoredException e) {
      throw new AskToolException("match not stored: " + matchId);
    } catch (InvalidSectionsException e) {
      throw new AskToolException(e.getMessage());
    }
  }
}
