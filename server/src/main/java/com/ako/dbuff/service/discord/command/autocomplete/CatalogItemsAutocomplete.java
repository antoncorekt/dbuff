package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyFile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.Command;

/** The {@code items:} options: categories and entry keys of the item catalog. */
abstract class CatalogItemsAutocomplete implements AutocompleteProvider {
  private final ItemEfficiencyFile catalog;

  CatalogItemsAutocomplete(ItemEfficiencyFile catalog) {
    this.catalog = catalog;
  }

  @Override
  public String getOptionName() {
    return "items";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    Map<String, String> names = new LinkedHashMap<>();
    catalog.selectableNames().forEach(name -> names.put(name, name));
    return ChoiceAccumulator.accumulate(currentInput, names);
  }
}
