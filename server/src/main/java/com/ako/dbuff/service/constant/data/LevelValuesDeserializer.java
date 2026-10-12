package com.ako.dbuff.service.constant.data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads OpenDota's per-level numbers, which arrive as a single value or a list and as strings or
 * numbers. A list keeps its level positions: an entry that is not a number becomes null at its
 * index. A single value that is not a number gives null for the whole field, so "no data" differs
 * from an empty list. A text of numbers separated by slashes or spaces ("60 / 120") is read as
 * levels. One dirty ability must not fail the whole constants load.
 */
public class LevelValuesDeserializer extends JsonDeserializer<List<Double>> {

  private static final Pattern SEPARATOR = Pattern.compile("\\s*/\\s*|\\s+");

  @Override
  public List<Double> deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    JsonNode node = parser.readValueAsTree();
    if (!node.isArray()) {
      return parseSingle(node);
    }
    List<Double> values = new ArrayList<>();
    node.forEach(element -> values.add(parseNumber(element)));
    return values;
  }

  private static List<Double> parseSingle(JsonNode node) {
    if (node.isTextual()) {
      String[] parts = SEPARATOR.split(node.textValue().trim());
      List<Double> values = new ArrayList<>();
      for (String part : parts) {
        Double value = parseText(part);
        if (value == null) {
          return null;
        }
        values.add(value);
      }
      return values;
    }
    Double value = parseNumber(node);
    return value == null ? null : new ArrayList<>(List.of(value));
  }

  private static Double parseNumber(JsonNode node) {
    if (node.isNumber()) {
      return node.doubleValue();
    }
    return node.isTextual() ? parseText(node.textValue().trim()) : null;
  }

  private static Double parseText(String text) {
    try {
      return Double.parseDouble(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
