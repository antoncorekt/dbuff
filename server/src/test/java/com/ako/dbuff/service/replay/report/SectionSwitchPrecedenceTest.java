package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.InstanceSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SectionSwitchPrecedenceTest {
  private final ReplayReportSection on = TestSections.named("lanes", 20, true);
  private final ReplayReportSection off = TestSections.named("important-fights", 65, false);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final SectionSwitch sw = new SectionSwitch(properties, List.of(on, off));

  private static InstanceSettings channel(String sections) {
    return InstanceSettings.builder().sections(sections).build();
  }

  private void kill(String name) {
    ReplayReportProperties.SectionToggle toggle = new ReplayReportProperties.SectionToggle();
    toggle.setEnabled(false);
    properties.getSections().put(name, toggle);
  }

  @Test
  void nothingSet_eachSectionFollowsItsOwnDefault() {
    assertThat(sw.isAutomatic(on, new InstanceSettings())).isTrue();
    assertThat(sw.isAutomatic(off, new InstanceSettings())).isFalse();
    assertThat(sw.isAutomatic(on, null)).isTrue();
  }

  @Test
  void globalList_isTheOnlyAutomaticSwitchOnceSet() {
    properties.setDefaultSections(List.of("important-fights"));

    assertThat(sw.isAutomatic(off, new InstanceSettings())).isTrue();
    assertThat(sw.isAutomatic(on, new InstanceSettings())).isFalse();
  }

  @Test
  void emptyGlobalList_postsNothingEvenForSectionsOnByDefault() {
    properties.setDefaultSections(List.of());

    assertThat(sw.isAutomatic(on, new InstanceSettings())).isFalse();
    assertThat(sw.isAutomatic(off, new InstanceSettings())).isFalse();
  }

  @Test
  void channelList_beatsTheGlobalListAndTheDefaults() {
    properties.setDefaultSections(List.of("lanes"));

    assertThat(sw.isAutomatic(on, channel("important-fights"))).isFalse();
    assertThat(sw.isAutomatic(off, channel("important-fights"))).isTrue();
  }

  @Test
  void emptyChannelList_postsNothingAtAll() {
    properties.setDefaultSections(List.of("lanes"));

    assertThat(sw.isAutomatic(on, channel(""))).isFalse();
  }

  @Test
  void channelWithoutAList_followsTheGlobalOne() {
    properties.setDefaultSections(List.of("lanes"));

    assertThat(sw.isAutomatic(on, InstanceSettings.builder().language("Russian").build())).isTrue();
  }

  @Test
  void killSwitch_hidesTheSectionEverywhere() {
    kill("lanes");
    properties.setDefaultSections(List.of("lanes"));

    assertThat(sw.isHidden(on)).isTrue();
    assertThat(sw.isAutomatic(on, channel("lanes"))).isFalse();
    assertThat(sw.visibleNames()).containsExactly("important-fights");
  }

  @Test
  void enabledTrue_isNotAKillSwitch() {
    ReplayReportProperties.SectionToggle toggle = new ReplayReportProperties.SectionToggle();
    toggle.setEnabled(true);
    properties.getSections().put("important_fights", toggle);

    assertThat(sw.isHidden(off)).isFalse();
    assertThat(sw.isAutomatic(off, new InstanceSettings())).isTrue();
  }

  @Test
  void find_ignoresCaseDashAndUnderscoreAndReturnsTheRegisteredName() {
    assertThat(sw.find("Important_Fights")).containsSame(off);
    assertThat(sw.find("nope")).isEmpty();
    assertThat(sw.find(null)).isEmpty();
  }

  @Test
  void visibleNames_followPostingOrder() {
    assertThat(sw.visibleNames()).containsExactly("lanes", "important-fights");
  }

  @Test
  void validate_failsOnAnUnknownNameInTheGlobalList() {
    properties.setEnabled(true);
    properties.setDefaultSections(List.of("lanes", "nope"));

    assertThatThrownBy(sw::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("default-sections")
        .hasMessageContaining("nope")
        .hasMessageContaining("[important-fights, lanes]");
  }

  @Test
  void validate_acceptsTheGlobalListInAnySpelling() {
    properties.setEnabled(true);
    properties.setDefaultSections(List.of("IMPORTANT_FIGHTS", "lanes"));

    sw.validate();
  }

  @Test
  void anEmptyPropertyBindsToAnEmptyListAndAnAbsentOneToNull() {
    Binder empty =
        new Binder(
            new MapConfigurationPropertySource(Map.of("replay.report.default-sections", "")));
    Binder absent = new Binder(new MapConfigurationPropertySource(Map.of()));
    Binder listed =
        new Binder(
            new MapConfigurationPropertySource(
                Map.of("replay.report.default-sections", "lanes, swing")));

    assertThat(
            empty.bindOrCreate("replay.report", ReplayReportProperties.class).getDefaultSections())
        .isEmpty();
    assertThat(
            absent.bindOrCreate("replay.report", ReplayReportProperties.class).getDefaultSections())
        .isNull();
    assertThat(
            listed.bindOrCreate("replay.report", ReplayReportProperties.class).getDefaultSections())
        .containsExactly("lanes", "swing");
  }

  @Test
  void cooldownProperty_mustNotBeNegative() {
    properties.setOnDemandCooldownSeconds(-1);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("on-demand-cooldown-seconds");
    properties.setOnDemandCooldownSeconds(0);
    properties.validate();
  }
}
