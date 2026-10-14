package com.ako.dbuff.service.replay;

import com.ako.dbuff.service.replay.report.ReplayReportProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayProcessingPropertiesTest {

  @Test
  void defaults_keepTheBacklogOffAndUseTheOwnersPriorityWindows() {
    ReplayProcessingProperties properties = new ReplayProcessingProperties();

    assertThat(properties.getFreshWindowHours()).isEqualTo(6);
    assertThat(properties.getRetryPriorityHours()).isEqualTo(24);
    assertThat(properties.getRetryPriorityAttempts()).isEqualTo(2);
    assertThat(properties.getBacklog().isEnabled()).isFalse();
    assertThat(properties.isEnabled()).isFalse();
  }

  @Test
  void environmentVariablesOfTheSwitchOnStepsBind() {
    ReplayProcessingProperties properties =
        bind(
            Map.of(
                "REPLAY_PROCESSING_ENABLED", "true",
                "REPLAY_PROCESSING_BATCH_SIZE", "1",
                "REPLAY_PROCESSING_TEMP_DIR", "/var/tmp/dbuff-replays"));

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getBatchSize()).isEqualTo(1);
    assertThat(properties.getTempDir()).hasToString("/var/tmp/dbuff-replays");
  }

  @Test
  void sectionSwitchesBindFromEnvironmentVariables() {
    ReplayReportProperties properties =
        new Binder(
                ConfigurationPropertySources.from(
                    sources(
                        Map.of(
                            "REPLAY_REPORT_ENABLED", "true",
                            "REPLAY_REPORT_SECTIONS_LANES_ENABLED", "true",
                            "REPLAY_REPORT_SECTIONS_IMPORTANTFIGHTS_ENABLED", "true"))))
            .bind("replay.report", ReplayReportProperties.class)
            .get();

    assertThat(properties.isEnabled()).isTrue();

    assertThat(properties.getSections()).containsOnlyKeys("lanes", "importantfights");
  }

  private static MutablePropertySources sources(Map<String, Object> environment) {
    MutablePropertySources sources = new MutablePropertySources();
    sources.addFirst(new SystemEnvironmentPropertySource("systemEnvironment", environment));
    return sources;
  }

  private static ReplayProcessingProperties bind(Map<String, Object> environment) {
    return new Binder(ConfigurationPropertySources.from(sources(environment)))
        .bind("replay.processing", ReplayProcessingProperties.class)
        .get();
  }
}
