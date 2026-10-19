package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportItemsAutocompleteTest {
  private final ReportItemsAutocomplete provider =
      new ReportItemsAutocomplete(new ItemEfficiencyFile("replay/items.yaml"));

  @Test
  void servesTheItemsOptionOfMatchReport() {
    assertThat(provider.getCommandName()).isEqualTo("match");
    assertThat(provider.getSubcommandName()).isEqualTo("report");
    assertThat(provider.getOptionName()).isEqualTo("items");
  }

  @Test
  void offersCategoriesAndEntryKeysFromTheCatalog() {
    assertThat(provider.getChoices("", null))
        .extracting(c -> c.getAsString())
        .contains("farming", "sustain", "magic_stick", "hand_of_midas");
  }
}
