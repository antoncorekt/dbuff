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
