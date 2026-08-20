package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * Item autocomplete over the in-memory item constant cache.
 *
 * <p>List-valued, so it accumulates: the picker shows display names while the submitted value is
 * the comma-separated short names that {@code ConstantNameResolver} expects.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemAutocomplete implements AutocompleteProvider {

  private final ConstantsManagers constantsManagers;

  @Override
  public String getOptionName() {
    return "items";
  }

  @Override
  public String getCommandName() {
    return "stats";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    try {
      return ChoiceAccumulator.accumulate(currentInput, displayToValue());
    } catch (Exception e) {
      log.debug("Item autocomplete failed for input '{}': {}", currentInput, e.getMessage());
      return List.of();
    }
  }

  private Map<String, String> displayToValue() {
    Map<String, String> candidates = new LinkedHashMap<>();
    for (Map.Entry<String, ItemConstant> entry :
        constantsManagers.getItemConstantMap().entrySet()) {
      String display = entry.getValue().getDname();
      if (display != null && !display.isBlank()) {
        candidates.put(display, entry.getKey());
      }
    }
    return candidates;
  }
}
