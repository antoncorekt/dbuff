package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class SectionSwitchContextTest {

  private static final List<Class<? extends ReplayReportSection>> REAL =
      List.of(
          SwingSection.class,
          DeathSection.class,
          FightSection.class,
          MovementSection.class,
          DraftSection.class,
          CompositionSection.class,
          ImportantFightsSection.class,
          LanesSection.class,
          RotationsSection.class,
          SmokeSection.class,
          ObjectivesSection.class,
          OpeningSection.class,
          VisionSection.class,
          ItemsSection.class,
          OutliersSection.class);

  private static final Set<String> OFF_BY_DEFAULT =
      Set.of(
          "important-fights",
          "lanes",
          "rotations",
          "smoke",
          "objectives",
          "opening",
          "vision",
          "items",
          "outliers");

  @Configuration
  @EnableConfigurationProperties(ReplayReportProperties.class)
  static class PropertiesConfig {}

  private ApplicationContextRunner runner() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class)
            .withBean(SectionSwitch.class);
    for (Class<? extends ReplayReportSection> type : REAL) {
      runner = withSection(runner, type);
    }
    return runner;
  }

  private static <T extends ReplayReportSection> ApplicationContextRunner withSection(
      ApplicationContextRunner runner, Class<T> type) {
    return runner.withBean(type, () -> instance(type));
  }

  private static <T> T instance(Class<T> type) {
    try {
      Constructor<?> constructor = type.getDeclaredConstructors()[0];
      Object[] args = Arrays.stream(constructor.getParameterTypes()).map(Mockito::mock).toArray();
      return type.cast(constructor.newInstance(args));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void theRealSectionsHaveUniqueStableNamesAndOnlyTheNewOneIsOffByDefault() {
    runner()
        .withPropertyValues("replay.report.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              Map<String, ReplayReportSection> byName =
                  context.getBeansOfType(ReplayReportSection.class).values().stream()
                      .collect(Collectors.toMap(ReplayReportSection::name, s -> s));
              assertThat(byName.keySet())
                  .containsExactlyInAnyOrder(
                      "swing",
                      "deaths",
                      "fights",
                      "movement",
                      "draft",
                      "composition",
                      "important-fights",
                      "lanes",
                      "rotations",
                      "smoke",
                      "objectives",
                      "opening",
                      "vision",
                      "items",
                      "outliers");
              assertThat(byName.values())
                  .filteredOn(s -> !OFF_BY_DEFAULT.contains(s.name()))
                  .allMatch(ReplayReportSection::defaultEnabled);
              assertThat(byName.get("important-fights").defaultEnabled()).isFalse();
              OFF_BY_DEFAULT.forEach(
                  name -> assertThat(byName.get(name).defaultEnabled()).isFalse());
            });
  }

  @Test
  void everyRealSectionHasItsOwnOrder() {
    runner()
        .run(
            context -> {
              List<Integer> orders =
                  context.getBeansOfType(ReplayReportSection.class).values().stream()
                      .map(ReplayReportSection::order)
                      .toList();
              assertThat(orders).doesNotHaveDuplicates();
            });
  }

  @Test
  void aSectionWithoutTheOverrideIsOffByDefault() {
    ReplayReportSection section = new TestSection("fresh");
    assertThat(section.defaultEnabled()).isFalse();
  }

  @Test
  void aPropertyForAnUnknownSectionFailsStartUpOnlyWhileTheReportIsOn() {
    runner()
        .withPropertyValues(
            "replay.report.enabled=true", "replay.report.sections.nope.enabled=true")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .rootCause()
                    .hasMessageContaining("nope")
                    .hasMessageContaining("swing"));
    runner()
        .withPropertyValues("replay.report.sections.nope.enabled=true")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void bindsTheSectionPropertyFromTheYamlFormAndTheEnvironmentVariableForm() {
    ConfigurationPropertySource yaml =
        new MapConfigurationPropertySource(
            Map.of("replay.report.sections.movement.enabled", "false"));
    ConfigurationPropertySource env =
        ConfigurationPropertySource.from(
            new SystemEnvironmentPropertySource(
                "systemEnvironment", env("REPLAY_REPORT_SECTIONS_MOVEMENT_ENABLED", "false")));

    for (ConfigurationPropertySource source : List.of(yaml, env)) {
      ReplayReportProperties bound =
          new Binder(source).bindOrCreate("replay.report", ReplayReportProperties.class);
      MovementSection movement = new MovementSection(null);
      assertThat(new SectionSwitch(bound, List.of(movement)).isEnabled(movement)).isFalse();
    }
  }

  @Test
  void bindsADashedSectionNameFromTheEnvironmentVariableForm() {
    ConfigurationPropertySource env =
        ConfigurationPropertySource.from(
            new SystemEnvironmentPropertySource(
                "systemEnvironment", env("REPLAY_REPORT_SECTIONS_TEAMSPLITS_ENABLED", "true")));
    ReplayReportProperties bound =
        new Binder(env).bindOrCreate("replay.report", ReplayReportProperties.class);
    ReplayReportSection splits = new TestSection("team-splits");

    assertThat(new SectionSwitch(bound, List.of(splits)).isEnabled(splits)).isTrue();
  }

  private static Map<String, Object> env(String name, String value) {
    return new HashMap<>(Map.of(name, value));
  }

  private record TestSection(String name) implements ReplayReportSection {
    @Override
    public int order() {
      return 1;
    }

    @Override
    public ReportDetail minimumDetail() {
      return ReportDetail.STANDARD;
    }

    @Override
    public List<String> lines(ReplayReportInput input) {
      return List.of();
    }
  }
}
