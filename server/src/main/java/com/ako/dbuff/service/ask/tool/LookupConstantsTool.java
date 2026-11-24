package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.ConstantNameResolver.ConstantMatch;
import com.ako.dbuff.service.constant.ConstantNameResolver.Kind;
import com.ako.dbuff.service.constant.GameModeResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LookupConstantsTool implements AskTool {

  private static final String GAME_MODE = "game_mode";
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 50;

  private final ConstantNameResolver constants;
  private final GameModeResolver gameModes;
  private final AskToolArgs args;

  @Override
  public String name() {
    return "lookup_constants";
  }

  @Override
  public String description() {
    return "Finds heroes, abilities, items or game modes by a name fragment and returns their ids,"
        + " for use in sql_query.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "kind",
            Map.of("type", "string", "enum", List.of("hero", "ability", "item", GAME_MODE)),
            "query",
            Map.of("type", "string", "description", "Name fragment, case-insensitive"),
            "limit",
            Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT)),
        "required",
        List.of("kind", "query"),
        "additionalProperties",
        false);
  }

  @Override
  public Object call(JsonNode input, AskContext ctx) {
    String kind = text(input, "kind").toLowerCase(Locale.ROOT);
    String query = text(input, "query");
    int limit = args.optionalInt(input, "limit", 1, MAX_LIMIT).orElse(DEFAULT_LIMIT);
    if (GAME_MODE.equals(kind)) {
      return gameModes(query, limit);
    }
    Kind constantKind;
    try {
      constantKind = Kind.valueOf(kind.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new AskToolException("kind must be one of hero, ability, item, game_mode");
    }
    return constants.search(constantKind, query, limit);
  }

  private List<ConstantMatch> gameModes(String query, int limit) {
    String needle = query.toLowerCase(Locale.ROOT);
    return gameModes.displayToSubmittedValue().entrySet().stream()
        .filter(
            e ->
                e.getKey().toLowerCase(Locale.ROOT).contains(needle)
                    || e.getValue().toLowerCase(Locale.ROOT).contains(needle))
        .limit(limit)
        .map(e -> new ConstantMatch(modeId(e.getValue()), e.getValue(), e.getKey()))
        .toList();
  }

  private String modeId(String submitted) {
    Set<Long> ids = gameModes.resolve(Set.of(submitted)).idsOrNullIfEmpty();
    return ids == null ? null : ids.iterator().next().toString();
  }

  private static String text(JsonNode input, String field) {
    JsonNode node = input == null ? null : input.get(field);
    if (node == null || node.asText().isBlank()) {
      throw new AskToolException(field + " is required");
    }
    return node.asText().trim();
  }
}
