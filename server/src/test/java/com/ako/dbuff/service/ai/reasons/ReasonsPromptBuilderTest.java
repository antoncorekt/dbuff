package com.ako.dbuff.service.ai.reasons;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReasonsPromptBuilderTest {
  private final ReplayReportAiProperties properties = new ReplayReportAiProperties();
  private final ReasonsPromptBuilder builder = new ReasonsPromptBuilder(properties);

  private static FactTrimmer.Trimmed facts(int dropped) {
    return new FactTrimmer.Trimmed(
        List.of(
            new Fact("RESULT", FactKind.RESULT, "Dire won after 34:12."),
            new Fact("F1", FactKind.FIGHT, "8:10 Dire won the fight.")),
        dropped);
  }

  @Test
  void theSystemPromptStatesTheRulesAndTheOutputShape() {
    ReasonsPrompt prompt = builder.build(facts(0));

    assertThat(prompt.system())
        .contains("5 most decisive reasons")
        .contains("Use ONLY the given facts")
        .contains("Cite the ids")
        .contains("not enough data")
        .contains("in English")
        .contains("Return ONLY a JSON array")
        .contains("\"evidence\":[\"F3\",\"LANE_1\"]")
        .doesNotContain("Ability Draft");
  }

  @Test
  void theUserPromptListsTheFactsAsIdRows_andSaysNothingWhenNoneWereDropped() {
    ReasonsPrompt prompt = builder.build(facts(0));

    assertThat(prompt.user())
        .isEqualTo("Match facts:\nRESULT: Dire won after 34:12.\nF1: 8:10 Dire won the fight.");
  }

  @Test
  void theNumberOfDroppedFactsIsStatedInOneLine() {
    ReasonsPrompt prompt = builder.build(facts(7));

    assertThat(prompt.user())
        .endsWith("\n(7 less important facts were left out because of length.)");
  }

  @Test
  void theLanguageComesFromTheSettings() {
    properties.setLanguage("Russian");

    assertThat(builder.build(facts(0)).system()).contains("in Russian");
  }

  @Test
  void theChannelLanguageOverridesTheServerOneOnlyWhenSet() {
    assertThat(builder.build(facts(0), "Russian").system())
        .contains("in Russian")
        .doesNotContain("in English");
    assertThat(builder.build(facts(0), null).system()).contains("in English");
    assertThat(builder.build(facts(0), " ").system()).contains("in English");
  }
}
