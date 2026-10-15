package com.ako.dbuff.service.replay.analysis.role;

import java.util.EnumMap;
import java.util.Map;

/**
 * Checks a {@code {"FUNCTION": value}} entry of a checked-in role file, so every such file names a
 * failure the same way: the file, the entry and what is wrong.
 */
public final class FunctionValues {
  private FunctionValues() {}

  /**
   * @param label the kind of file, for the message ("Role override file")
   * @param entry the entry the values belong to, named in the message
   * @throws IllegalStateException for no function, an unknown function or a value outside (0, 1]
   */
  public static Map<RoleFunction, Double> parse(
      String label, String source, String entry, Map<String, Double> values) {
    if (values == null || values.isEmpty()) {
      throw invalid(label, source, "'" + entry + "' has no function");
    }
    Map<RoleFunction, Double> result = new EnumMap<>(RoleFunction.class);
    values.forEach(
        (function, value) -> {
          RoleFunction parsed;
          try {
            parsed = RoleFunction.valueOf(function);
          } catch (IllegalArgumentException e) {
            throw invalid(
                label, source, "'" + entry + "' names the unknown function '" + function + "'");
          }
          if (value == null || value.isNaN() || value <= 0 || value > 1) {
            throw invalid(
                label, source, "'" + entry + "' has " + function + " " + value + ", not in (0, 1]");
          }
          result.put(parsed, value);
        });
    return result;
  }

  public static IllegalStateException invalid(String label, String source, String problem) {
    return new IllegalStateException(label + " " + source + ": " + problem);
  }
}
