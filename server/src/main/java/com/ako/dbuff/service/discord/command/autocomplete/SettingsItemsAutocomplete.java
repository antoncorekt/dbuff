package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyFile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * The {@code items:} option of {@code /dbuff settings}: categories and entry keys of the catalog.
 */
@Component
public class SettingsItemsAutocomplete implements AutocompleteProvider {
  private final ItemEfficiencyFile catalog;

  public SettingsItemsAutocomplete(ItemEfficiencyFile catalog) {
    this.catalog = catalog;
  }

  @Override
  public String getOptionName() {
    return "items";
  }

  @Override
  public String getCommandName() {
    return "dbuff";
  }

  @Override
  public String getSubcommandName() {
    return "settings";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    Map<String, String> names = new LinkedHashMap<>();
    catalog.selectableNames().forEach(name -> names.put(name, name));
    return ChoiceAccumulator.accumulate(currentInput, names);
  }
}
