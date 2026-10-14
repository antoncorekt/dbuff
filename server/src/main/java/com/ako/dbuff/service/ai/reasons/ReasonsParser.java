package com.ako.dbuff.service.ai.reasons;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns the model's text into reasons we can trust: strips code fences, drops reasons without a
 * title, an explanation or any evidence id that exists in the input, keeps at most five, ranks them
 * again from 1 and caps the lengths. Fewer than five is fine.
 */
@Component
@RequiredArgsConstructor
public class ReasonsParser {
  private static final String ELLIPSIS = "…";

  private final ObjectMapper mapper;

  private record Candidate(int rank, int position, Reason reason) {}

  /**
   * @throws IllegalArgumentException when the text holds no JSON array of reasons
   */
  public List<Reason> parse(String raw, Set<String> factIds) {
    JsonNode array = array(raw);
    List<Candidate> candidates = new ArrayList<>();
    for (int i = 0; i < array.size(); i++) {
      JsonNode node = array.get(i);
      Reason reason = reason(node, factIds);
      if (reason != null) {
        int rank = node.path("rank").isInt() ? node.get("rank").asInt() : Integer.MAX_VALUE;
        candidates.add(new Candidate(rank, i, reason));
      }
    }
    candidates.sort(Comparator.comparingInt(Candidate::rank).thenComparingInt(Candidate::position));
    List<Reason> reasons = new ArrayList<>();
    for (Candidate c : candidates.stream().limit(ReplayReportAiProperties.MAX_REASONS).toList()) {
      reasons.add(
          new Reason(
              reasons.size() + 1,
              c.reason().title(),
              c.reason().explanation(),
              c.reason().evidence()));
    }
    return List.copyOf(reasons);
  }

  private JsonNode array(String raw) {
    String text = raw == null ? "" : raw;
    int start = text.indexOf('[');
    int end = text.lastIndexOf(']');
    if (start < 0 || end <= start) {
      throw new IllegalArgumentException("the answer holds no JSON array");
    }
    try {
      JsonNode node = mapper.readTree(text.substring(start, end + 1));
      if (!node.isArray()) {
        throw new IllegalArgumentException("the answer is not a JSON array");
      }
      return node;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("the answer is not valid JSON: " + e.getOriginalMessage());
    }
  }

  private static Reason reason(JsonNode node, Set<String> factIds) {
    String title = node.path("title").asText("").strip();
    String explanation = node.path("explanation").asText("").strip();
    Set<String> evidence = new LinkedHashSet<>();
    node.path("evidence")
        .forEach(
            e -> {
              String id = e.asText("").strip();
              if (factIds.contains(id)) {
                evidence.add(id);
              }
            });
    if (title.isEmpty() || explanation.isEmpty() || evidence.isEmpty()) {
      return null;
    }
    return new Reason(
        0,
        cut(title, ReasonsPromptBuilder.MAX_TITLE_LENGTH),
        cut(explanation, ReasonsPromptBuilder.MAX_EXPLANATION_LENGTH),
        List.copyOf(evidence));
  }

  private static String cut(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max - 1) + ELLIPSIS;
  }
}
