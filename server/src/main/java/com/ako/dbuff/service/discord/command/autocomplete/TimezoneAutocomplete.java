package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.CommandContext;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/** The {@code timezone:} option of {@code /dbuff settings}, offering IANA region names. */
@Component
public class TimezoneAutocomplete implements AutocompleteProvider {

  /** Region-qualified ids only: the legacy aliases and fixed offsets are noise in a picker. */
  private static final Map<String, String> ZONES = buildZones();

  @Override
  public String getOptionName() {
    return "timezone";
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
    return ChoiceAccumulator.single(currentInput, ZONES);
  }

  private static Map<String, String> buildZones() {
    Map<String, String> zones = new LinkedHashMap<>();
    for (String id : new TreeSet<>(ZoneId.getAvailableZoneIds())) {
      if (id.contains("/") && !id.startsWith("Etc/") && !id.startsWith("SystemV/")) {
        zones.put(id, id);
      }
    }
    zones.put("UTC", "UTC");
    return zones;
  }
}
