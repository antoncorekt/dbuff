package com.ako.dbuff.service.constant.data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads OpenDota's per-level numbers, which arrive as a single value or a list and as strings or
 * numbers. Entries that are not numbers (an empty string, {@code undefined}) are skipped, because
 * one dirty ability must not fail the whole constants load.
 */
public class LevelValuesDeserializer extends JsonDeserializer<List<Double>> {

  @Override
  public List<Double> deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    JsonNode node = parser.readValueAsTree();
    List<Double> values = new ArrayList<>();
    if (node.isArray()) {
      node.forEach(element -> addIfNumber(values, element));
    } else {
      addIfNumber(values, node);
    }
    return values;
  }

  private static void addIfNumber(List<Double> values, JsonNode node) {
    if (node.isNumber()) {
      values.add(node.doubleValue());
    } else if (node.isTextual()) {
      try {
        values.add(Double.parseDouble(node.textValue().trim()));
      } catch (NumberFormatException ignored) {
        // not a number, skipped on purpose
      }
    }
  }
}
