package com.ako.dbuff.service.discord.command.autocomplete;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.interactions.commands.Command;

/**
 * Turns a partially typed option value into Discord autocomplete choices.
 *
 * <p>List-valued options arrive as one comma-separated string because Discord options are
 * single-valued. {@link #accumulate} completes only the final token and rebuilds the whole string,
 * so selecting a suggestion replaces the entire option value with prefix-plus-selection.
 */
public final class ChoiceAccumulator {

  /** Discord returns at most this many autocomplete suggestions. */
  public static final int MAX_CHOICES = 25;

  /** Discord rejects a choice whose submitted value exceeds this length. */
  public static final int MAX_VALUE_LENGTH = 100;

  /** Discord rejects a choice whose display name exceeds this length. */
  public static final int MAX_NAME_LENGTH = 100;

  private ChoiceAccumulator() {}

  /**
   * Choices for a list-valued option.
   *
   * @param currentInput what the user has typed so far, e.g. {@code "blink, black k"}
   * @param candidates display name to submitted value, e.g. {@code "Blink Dagger" -> "blink"}
   * @return up to {@link #MAX_CHOICES} choices, each carrying the whole accumulated value
   */
  public static List<Command.Choice> accumulate(
      String currentInput, Map<String, String> candidates) {

    String input = currentInput == null ? "" : currentInput;
    int lastComma = input.lastIndexOf(',');

    String prefixRaw = lastComma < 0 ? "" : input.substring(0, lastComma);
    String activeToken = lastComma < 0 ? input.trim() : input.substring(lastComma + 1).trim();

    Set<String> alreadyChosen = new LinkedHashSet<>(resolveAll(splitTokens(prefixRaw), candidates));

    String prefixValue = String.join(",", alreadyChosen);
    String prefixDisplay = String.join(", ", displayFor(alreadyChosen, candidates));

    List<Command.Choice> choices = new ArrayList<>();
    for (Map.Entry<String, String> candidate : sortedByDisplayName(candidates)) {
      if (choices.size() >= MAX_CHOICES) {
        break;
      }
      String display = candidate.getKey();
      String value = candidate.getValue();

      if (alreadyChosen.contains(value)) {
        continue;
      }
      if (!matches(activeToken, display, value)) {
        continue;
      }

      String fullValue = prefixValue.isEmpty() ? value : prefixValue + "," + value;
      String fullDisplay = prefixDisplay.isEmpty() ? display : prefixDisplay + ", " + display;

      // Dropped rather than truncated: a truncated value would submit a different
      // item than the one displayed.
      if (fullValue.length() > MAX_VALUE_LENGTH || fullDisplay.length() > MAX_NAME_LENGTH) {
        continue;
      }
      choices.add(new Command.Choice(fullDisplay, fullValue));
    }
    return choices;
  }

  /**
   * Choices for a single-valued option such as {@code hero:}.
   *
   * <p>Never accumulates. An input containing a comma matches nothing, which is the correct signal
   * that a list is not accepted here.
   *
   * @param currentInput what the user has typed so far
   * @param candidates display name to submitted value
   * @return up to {@link #MAX_CHOICES} choices
   */
  public static List<Command.Choice> single(String currentInput, Map<String, String> candidates) {
    String input = currentInput == null ? "" : currentInput.trim();
    if (input.contains(",")) {
      return List.of();
    }

    List<Command.Choice> choices = new ArrayList<>();
    for (Map.Entry<String, String> candidate : sortedByDisplayName(candidates)) {
      if (choices.size() >= MAX_CHOICES) {
        break;
      }
      if (!matches(input, candidate.getKey(), candidate.getValue())) {
        continue;
      }
      if (candidate.getValue().length() > MAX_VALUE_LENGTH
          || candidate.getKey().length() > MAX_NAME_LENGTH) {
        continue;
      }
      choices.add(new Command.Choice(candidate.getKey(), candidate.getValue()));
    }
    return choices;
  }

  private static boolean matches(String token, String display, String value) {
    if (token.isEmpty()) {
      return true;
    }
    String needle = token.toLowerCase();
    return display.toLowerCase().contains(needle) || value.toLowerCase().contains(needle);
  }

  private static List<String> splitTokens(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  /**
   * Maps each prefix token to its canonical value, keeping the token verbatim when it resolves to
   * nothing — the user may be mid-edit, and silently deleting their text would be hostile.
   */
  private static List<String> resolveAll(List<String> tokens, Map<String, String> candidates) {
    List<String> resolved = new ArrayList<>();
    for (String token : tokens) {
      resolved.add(canonicalValue(token, candidates).orElse(token));
    }
    return resolved;
  }

  private static Optional<String> canonicalValue(String token, Map<String, String> candidates) {
    return candidates.entrySet().stream()
        .filter(
            entry ->
                entry.getKey().equalsIgnoreCase(token) || entry.getValue().equalsIgnoreCase(token))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  private static List<String> displayFor(Set<String> values, Map<String, String> candidates) {
    List<String> displays = new ArrayList<>();
    for (String value : values) {
      displays.add(
          candidates.entrySet().stream()
              .filter(entry -> entry.getValue().equals(value))
              .map(Map.Entry::getKey)
              .findFirst()
              .orElse(value));
    }
    return displays;
  }

  private static List<Map.Entry<String, String>> sortedByDisplayName(
      Map<String, String> candidates) {
    return candidates.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
        .toList();
  }
}
