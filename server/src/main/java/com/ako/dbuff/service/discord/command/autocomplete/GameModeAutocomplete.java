package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * Game mode autocomplete: shows human-readable mode names, submits the numeric mode ID.
 *
 * <p>This is the whole point of the option — {@code DbufInstanceConfigDomain} stores {@code
 * Set<Long> gameModeIds}, so the old text command required users to know that All Pick is 22.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameModeAutocomplete implements AutocompleteProvider {

  private final ConstantsManagers constantsManagers;

  @Override
  public String getOptionName() {
    return "mode";
  }

  @Override
  public String getCommandName() {
    return "dbuff";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    try {
      return ChoiceAccumulator.single(currentInput, displayToValue());
    } catch (Exception e) {
      log.debug("Game mode autocomplete failed for input '{}': {}", currentInput, e.getMessage());
      return List.of();
    }
  }

  private Map<String, String> displayToValue() {
    Map<String, String> candidates = new LinkedHashMap<>();
    for (Map.Entry<String, MatchTypeConstant> entry :
        constantsManagers.getMatchTypeConstantMap().entrySet()) {
      MatchTypeConstant mode = entry.getValue();
      String id = mode.getId() != null ? mode.getId() : entry.getKey();
      candidates.put(prettyName(mode.getName(), id), id);
    }
    return candidates;
  }

  /** Turns {@code game_mode_all_pick} into {@code All Pick}, falling back to the raw ID. */
  private String prettyName(String rawName, String id) {
    if (rawName == null || rawName.isBlank()) {
      return "Mode " + id;
    }
    String stripped = rawName.replace("game_mode_", "").replace('_', ' ').trim();
    if (stripped.isEmpty()) {
      return "Mode " + id;
    }
    StringBuilder pretty = new StringBuilder(stripped.length());
    boolean capitalize = true;
    for (char c : stripped.toCharArray()) {
      pretty.append(capitalize ? Character.toUpperCase(c) : c);
      capitalize = c == ' ';
    }
    return pretty.toString();
  }
}
