package com.ako.dbuff.service.ask.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The inputs and schema pieces the player tools share. */
final class PlayerToolSupport {

  private PlayerToolSupport() {}

  /** The period, hero and game-mode filters common to the player tools. */
  record Filters(LocalDate from, LocalDate to, Set<String> heroes, Set<String> gameModes) {}

  static Filters filters(AskToolArgs args, JsonNode input) {
    return new Filters(
        args.optionalDate(input, "from").orElse(null),
        args.optionalDate(input, "to").orElse(null),
        args.stringSet(input, "heroes"),
        args.stringSet(input, "game_modes"));
  }

  static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
    return Map.of("type", "object", "properties", properties, "required", required);
  }

  static Map<String, Object> commonProperties(boolean withPeriodFilters) {
    Map<String, Object> props = new LinkedHashMap<>();
    props.put(
        "players",
        stringArray("Player names or account ids. Empty or missing means the person asking."));
    props.put("from", string("Start date, ISO like 2026-01-31. Optional."));
    props.put("to", string("End date, ISO like 2026-01-31. Optional."));
    if (withPeriodFilters) {
      props.put("heroes", stringArray("Hero names to restrict to. Optional."));
      props.put("game_modes", stringArray("Game mode names to restrict to. Optional."));
    }
    return props;
  }

  static Map<String, Object> string(String description) {
    return Map.of("type", "string", "description", description);
  }

  static Map<String, Object> stringArray(String description) {
    return Map.of("type", "array", "items", Map.of("type", "string"), "description", description);
  }

  static Map<String, Object> integer(String description) {
    return Map.of("type", "integer", "description", description);
  }

  static Map<String, Object> enumOf(String description, List<String> values) {
    return Map.of("type", "string", "enum", values, "description", description);
  }
}
