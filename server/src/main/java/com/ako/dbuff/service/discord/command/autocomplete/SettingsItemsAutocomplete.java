package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyFile;
import org.springframework.stereotype.Component;

/** The {@code items:} option of {@code /dbuff settings}. */
@Component
public class SettingsItemsAutocomplete extends CatalogItemsAutocomplete {
  public SettingsItemsAutocomplete(ItemEfficiencyFile catalog) {
    super(catalog);
  }

  @Override
  public String getCommandName() {
    return "dbuff";
  }

  @Override
  public String getSubcommandName() {
    return "settings";
  }
}
