package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.Resolution;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads the common tool arguments; a bad value is an {@link AskToolException}. */
@Component
@RequiredArgsConstructor
public class AskToolArgs {

  private final PlayerReferenceResolver playerResolver;

  /** The players named in {@code field}; an empty or missing field gives the asker. */
  public List<ResolvedPlayer> players(JsonNode input, String field, AskContext ctx) {
    Set<String> references = stringSet(input, field);
    if (references.isEmpty()) {
      if (ctx.asker() == null) {
        throw new AskToolException(
            "No player given and the asker is not linked to a player. Valid players: "
                + validPlayers(ctx));
      }
      return List.of(ctx.asker());
    }
    Resolution resolution = playerResolver.resolve(ctx.channelId(), new ArrayList<>(references));
    if (resolution.hasUnresolved()) {
      throw new AskToolException(
          "Unknown player: "
              + String.join(", ", resolution.unresolved())
              + ". Valid players: "
              + validPlayers(ctx));
    }
    return resolution.players();
  }

  public Set<String> stringSet(JsonNode input, String field) {
    Set<String> values = new LinkedHashSet<>();
    JsonNode node = input == null ? null : input.get(field);
    if (node == null || node.isNull()) {
      return values;
    }
    if (node.isArray()) {
      node.forEach(item -> addText(values, item));
    } else {
      addText(values, node);
    }
    return values;
  }

  public Optional<LocalDate> optionalDate(JsonNode input, String field) {
    JsonNode node = input == null ? null : input.get(field);
    if (node == null || node.isNull() || node.asText().isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(LocalDate.parse(node.asText().trim()));
    } catch (DateTimeParseException e) {
      throw new AskToolException(field + " must be a date like 2026-01-31, got: " + node.asText());
    }
  }

  public Optional<Integer> optionalInt(JsonNode input, String field, int min, int max) {
    JsonNode node = input == null ? null : input.get(field);
    if (node == null || node.isNull()) {
      return Optional.empty();
    }
    if (!node.canConvertToInt()) {
      throw new AskToolException(field + " must be an integer, got: " + node.asText());
    }
    int value = node.asInt();
    if (value < min || value > max) {
      throw new AskToolException(field + " must be between " + min + " and " + max);
    }
    return Optional.of(value);
  }

  public long requiredLong(JsonNode input, String field) {
    JsonNode node = input == null ? null : input.get(field);
    if (node == null || node.isNull() || !node.canConvertToLong()) {
      throw new AskToolException(field + " is required and must be a number");
    }
    return node.asLong();
  }

  private static void addText(Set<String> values, JsonNode item) {
    String text = item.asText().trim();
    if (!text.isEmpty()) {
      values.add(text);
    }
  }

  private static String validPlayers(AskContext ctx) {
    return ctx.focusGroup().stream().map(ResolvedPlayer::name).collect(Collectors.joining(", "));
  }
}
