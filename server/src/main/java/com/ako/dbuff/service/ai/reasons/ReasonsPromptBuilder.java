package com.ako.dbuff.service.ai.reasons;

import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Writes the prompt: short, for any game mode, with the facts as {@code ID: line} rows. */
@Component
@RequiredArgsConstructor
public class ReasonsPromptBuilder {
  static final int MAX_TITLE_LENGTH = 80;
  static final int MAX_EXPLANATION_LENGTH = 400;

  private static final String SYSTEM =
      """
      You are a Dota 2 analyst. Using the match facts you are given, explain why the tracked \
      players' team won or lost. In the facts, "your team" is the tracked players' team.
      Rules:
      - Give the %d most decisive reasons, ranked, the most decisive first.
      - Use ONLY the given facts. Never invent heroes, numbers, times or events.
      - Cite the ids of the facts each reason rests on in "evidence".
      - If the facts do not support %d reasons, give only the ones they support; where a point \
      cannot be judged, say "not enough data" instead of guessing.
      - Write "title" (at most %d characters) and "explanation" (one or two sentences, at most %d \
      characters) in %s.
      - Return ONLY a JSON array, no other text and no code fences, in this form:
      [{"rank":1,"title":"...","explanation":"...","evidence":["F3","LANE_1"]}, ...]\
      """;

  private final ReplayReportAiProperties properties;

  public ReasonsPrompt build(FactTrimmer.Trimmed facts) {
    String system =
        String.format(
            Locale.ROOT,
            SYSTEM,
            ReplayReportAiProperties.MAX_REASONS,
            ReplayReportAiProperties.MAX_REASONS,
            MAX_TITLE_LENGTH,
            MAX_EXPLANATION_LENGTH,
            properties.getLanguage());
    String rows = facts.kept().stream().map(Fact::line).collect(Collectors.joining("\n"));
    String left =
        facts.dropped() == 0
            ? ""
            : "\n(" + facts.dropped() + " less important facts were left out because of length.)";
    return new ReasonsPrompt(system, "Match facts:\n" + rows + left);
  }
}
