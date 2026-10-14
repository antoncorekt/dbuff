package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.ai.reasons.AiReasonsCommandHandler;
import com.ako.dbuff.service.ai.reasons.AiReasonsService;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** The {@code model:} option of {@code /match ai}, offering the allowed models. */
@Component
public class AiModelAutocomplete implements AutocompleteProvider {
  private final AiReasonsService service;

  public AiModelAutocomplete(@Lazy AiReasonsService service) {
    this.service = service;
  }

  @Override
  public String getOptionName() {
    return AiReasonsCommandHandler.MODEL_OPTION;
  }

  @Override
  public String getCommandName() {
    return "match";
  }

  @Override
  public String getSubcommandName() {
    return "ai";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    Map<String, String> models = new LinkedHashMap<>();
    service.allowedModels().forEach(m -> models.put(m, m));
    return ChoiceAccumulator.single(currentInput, models);
  }
}
