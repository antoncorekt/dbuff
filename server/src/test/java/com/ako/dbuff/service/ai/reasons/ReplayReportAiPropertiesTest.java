package com.ako.dbuff.service.ai.reasons;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayReportAiPropertiesTest {

  @Test
  void defaultsAreOffWithAnEightThousandTokenBudgetInEnglish() {
    ReplayReportAiProperties properties = new ReplayReportAiProperties();

    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.getFactsTokenBudget()).isEqualTo(8000);
    assertThat(properties.getLanguage()).isEqualTo("English");
    assertThat(properties.getTimeoutSeconds()).isNull();
    assertThat(properties.getModel()).isNull();
    assertThat(ReplayReportAiProperties.MAX_REASONS).isEqualTo(5);
    assertThatCode(properties::validate).doesNotThrowAnyException();
  }

  @Test
  void bindsFromTheEnvironmentStyleKeys() {
    var source =
        new MapConfigurationPropertySource(
            java.util.Map.of(
                "replay.report.ai.enabled", "true",
                "replay.report.ai.facts-token-budget", "3000",
                "replay.report.ai.language", "Russian",
                "replay.report.ai.timeout-seconds", "30"));

    ReplayReportAiProperties bound =
        new Binder(source).bind("replay.report.ai", ReplayReportAiProperties.class).get();

    assertThat(bound.isEnabled()).isTrue();
    assertThat(bound.getFactsTokenBudget()).isEqualTo(3000);
    assertThat(bound.getLanguage()).isEqualTo("Russian");
    assertThat(bound.getTimeoutSeconds()).isEqualTo(30);
  }

  @Test
  void theAllowListMustHoldAtLeastOneModelAndNoBlankOne() {
    ReplayReportAiProperties empty = new ReplayReportAiProperties();
    empty.setAllowedModels(java.util.List.of());
    ReplayReportAiProperties blank = new ReplayReportAiProperties();
    blank.setAllowedModels(java.util.Arrays.asList("gpt-4o", " "));
    ReplayReportAiProperties fine = new ReplayReportAiProperties();
    fine.setAllowedModels(java.util.List.of("gpt-4o-mini", "gpt-4o"));

    assertThatThrownBy(empty::validate).hasMessageContaining("allowed-models");
    assertThatThrownBy(blank::validate).hasMessageContaining("allowed-models");
    assertThatCode(fine::validate).doesNotThrowAnyException();
    assertThat(new ReplayReportAiProperties().getAllowedModels()).isNull();
  }

  @Test
  void reasoningModelsTakeNoTemperatureByDefault_andTheOutputLimitIsChecked() {
    ReplayReportAiProperties properties = new ReplayReportAiProperties();
    assertThat(properties.getNoTemperatureModelPrefixes())
        .containsExactly("o1", "o3", "o4", "gpt-5");
    ReplayReportAiProperties blank = new ReplayReportAiProperties();
    blank.setNoTemperatureModelPrefixes(java.util.Arrays.asList("o3", ""));
    ReplayReportAiProperties tiny = new ReplayReportAiProperties();
    tiny.setMaxOutputTokens(10);

    assertThatThrownBy(blank::validate).hasMessageContaining("no-temperature-model-prefixes");
    assertThatThrownBy(tiny::validate).hasMessageContaining("max-output-tokens");
  }

  @Test
  void rejectsValuesThatCannotWork() {
    ReplayReportAiProperties tiny = new ReplayReportAiProperties();
    tiny.setFactsTokenBudget(10);
    ReplayReportAiProperties blankLanguage = new ReplayReportAiProperties();
    blankLanguage.setLanguage(" ");
    ReplayReportAiProperties zeroTimeout = new ReplayReportAiProperties();
    zeroTimeout.setTimeoutSeconds(0);
    ReplayReportAiProperties noCalls = new ReplayReportAiProperties();
    noCalls.setMaxCallsPerMatchPerDay(0);
    ReplayReportAiProperties blankModel = new ReplayReportAiProperties();
    blankModel.setModel("");

    assertThatThrownBy(tiny::validate).hasMessageContaining("facts-token-budget");
    assertThatThrownBy(blankLanguage::validate).hasMessageContaining("language");
    assertThatThrownBy(zeroTimeout::validate).hasMessageContaining("timeout-seconds");
    assertThatThrownBy(noCalls::validate).hasMessageContaining("max-calls-per-match-per-day");
    assertThatThrownBy(blankModel::validate).hasMessageContaining("model");
  }
}
