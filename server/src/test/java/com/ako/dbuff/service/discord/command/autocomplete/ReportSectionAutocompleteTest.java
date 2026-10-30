package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.replay.report.ReplayReportProperties;
import com.ako.dbuff.service.replay.report.SectionSwitch;
import com.ako.dbuff.service.replay.report.TestSections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportSectionAutocompleteTest {
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final SectionSwitch sectionSwitch =
      new SectionSwitch(
          properties,
          List.of(
              TestSections.named("lanes", 20, false),
              TestSections.named("opendota", 200, false),
              TestSections.named("brand-new", 300, false)));
  private final ReportSectionAutocomplete provider = new ReportSectionAutocomplete(sectionSwitch);
  private final SettingsSectionsAutocomplete settingsProvider =
      new SettingsSectionsAutocomplete(sectionSwitch);

  @Test
  void servesTheSectionOptionOfMatchReport() {
    assertThat(provider.getCommandName()).isEqualTo("match");
    assertThat(provider.getSubcommandName()).isEqualTo("report");
    assertThat(provider.getOptionName()).isEqualTo("section");
  }

  @Test
  void offersAllAndEveryRegisteredSectionIncludingANewOne() {
    assertThat(provider.getChoices("", null))
        .extracting(c -> c.getAsString())
        .containsExactlyInAnyOrder("all", "everything", "lanes", "opendota", "brand-new");
  }

  @Test
  void filtersByWhatWasTyped() {
    assertThat(provider.getChoices("lan", null))
        .extracting(c -> c.getAsString())
        .containsExactly("lanes");
  }

  @Test
  void leavesOutASectionHiddenByTheKillSwitch() {
    ReplayReportProperties.SectionToggle toggle = new ReplayReportProperties.SectionToggle();
    toggle.setEnabled(false);
    properties.getSections().put("opendota", toggle);

    assertThat(provider.getChoices("", null))
        .extracting(c -> c.getAsString())
        .doesNotContain("opendota");
  }

  @Test
  void theSettingsPickerBuildsAListAndOffersNoAll() {
    assertThat(settingsProvider.getCommandName()).isEqualTo("dbuff");
    assertThat(settingsProvider.getSubcommandName()).isEqualTo("settings");
    assertThat(settingsProvider.getOptionName()).isEqualTo("sections");
    assertThat(settingsProvider.getChoices("lanes, br", null))
        .extracting(c -> c.getAsString())
        .containsExactly("lanes,brand-new");
    assertThat(settingsProvider.getChoices("", null))
        .extracting(c -> c.getAsString())
        .doesNotContain("all");
  }
}
