package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsItemsAutocompleteTest {
  private final SettingsItemsAutocomplete provider =
      new SettingsItemsAutocomplete(new ItemEfficiencyFile("replay/items.yaml"));

  @Test
  void servesTheItemsOptionOfDbuffSettings() {
    assertThat(provider.getCommandName()).isEqualTo("dbuff");
    assertThat(provider.getSubcommandName()).isEqualTo("settings");
    assertThat(provider.getOptionName()).isEqualTo("items");
  }

  @Test
  void offersCategoriesAndEntryKeysFromTheCatalog() {
    assertThat(provider.getChoices("", null))
        .extracting(c -> c.getAsString())
        .contains("farming", "sustain", "save", "magic_stick", "hand_of_midas", "glimmer_cape");
  }

  @Test
  void buildsAListFromTheLastToken() {
    assertThat(provider.getChoices("farming, mag", null))
        .extracting(c -> c.getAsString())
        .containsExactly("farming,magic_stick");
  }
}
