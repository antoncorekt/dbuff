package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.discord.command.SkillNameIndex;
import com.ako.dbuff.service.discord.command.SkillsOptions;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * Skill autocomplete for {@code /skills skill:}. Single-valued, offers display names and submits
 * the internal name.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillAutocomplete implements AutocompleteProvider {
  private final SkillNameIndex skills;

  @Override
  public String getOptionName() {
    return SkillsOptions.SKILL;
  }

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
