package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.replay.report.SectionSwitch;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** The {@code sections:} option of {@code /dbuff settings}, a list of registered sections. */
@Component
public class SettingsSectionsAutocomplete implements AutocompleteProvider {
  private final SectionSwitch sectionSwitch;

  public SettingsSectionsAutocomplete(@Lazy SectionSwitch sectionSwitch) {
    this.sectionSwitch = sectionSwitch;
  }

  @Override
  public String getOptionName() {
    return "sections";
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
    sectionSwitch.visibleNames().forEach(name -> names.put(name, name));
    return ChoiceAccumulator.accumulate(currentInput, names);
  }
}
