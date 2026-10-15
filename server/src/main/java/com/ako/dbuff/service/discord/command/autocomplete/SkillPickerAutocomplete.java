package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.discord.command.SkillNameIndex;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;

/**
 * Skill autocomplete for the skill options of {@code /skills}. Single-valued, offers display names
 * and submits the internal name. One subclass per option, so each is its own bean.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class SkillPickerAutocomplete implements AutocompleteProvider {
  private final SkillNameIndex skills;

  @Override
  public String getCommandName() {
    return "skills";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    try {
      return ChoiceAccumulator.single(currentInput, skills.displayToInternal());
    } catch (Exception e) {
      log.debug("Skill autocomplete failed for input '{}': {}", currentInput, e.getMessage());
      return List.of();
    }
  }
}
