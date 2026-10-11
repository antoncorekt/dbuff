package com.ako.dbuff.service.discord.command.autocomplete;

import java.util.List;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneAutocompleteTest {

  private final TimezoneAutocomplete provider = new TimezoneAutocomplete();

  @Test
  void servesTheTimezoneOptionOfDbuffSettingsOnly() {
    assertThat(provider.getCommandNames()).containsExactly("dbuff");
    assertThat(provider.getSubcommandName()).isEqualTo("settings");
    assertThat(provider.getOptionName()).isEqualTo("timezone");
  }

  @Test
  void typingACityFindsItsZone() {
    List<Command.Choice> choices = provider.getChoices("new_york", null);

    assertThat(choices).extracting(Command.Choice::getAsString).contains("America/New_York");
  }

  @Test
  void anEmptyInput_offersAtMostOnePageOfChoices() {
    assertThat(provider.getChoices("", null)).hasSize(ChoiceAccumulator.MAX_CHOICES);
  }

  @Test
  void aCommaMatchesNothing_becauseOnlyOneZoneIsAccepted() {
    assertThat(provider.getChoices("Europe/Kyiv,", null)).isEmpty();
  }

  @Test
  void legacyAndFixedOffsetIdsAreNotOffered() {
    assertThat(provider.getChoices("Etc/", null)).isEmpty();
  }
}
