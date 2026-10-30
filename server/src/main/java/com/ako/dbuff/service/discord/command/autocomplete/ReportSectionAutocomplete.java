package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.replay.report.ReportCommandHandler;
import com.ako.dbuff.service.replay.report.SectionSwitch;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * The {@code section:} option of {@code /match report}: {@code all}, {@code everything} and the
 * registered sections, so a new section appears without an edit here.
 */
@Component
public class ReportSectionAutocomplete implements AutocompleteProvider {
  private final SectionSwitch sectionSwitch;

  public ReportSectionAutocomplete(@Lazy SectionSwitch sectionSwitch) {
    this.sectionSwitch = sectionSwitch;
  }

  @Override
  public String getOptionName() {
    return ReportCommandHandler.SECTION_OPTION;
  }

  @Override
  public String getCommandName() {
    return "match";
  }

  @Override
  public String getSubcommandName() {
    return "report";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    Map<String, String> names = new LinkedHashMap<>();
    names.put(ReportCommandHandler.ALL, ReportCommandHandler.ALL);
    names.put(ReportCommandHandler.EVERYTHING, ReportCommandHandler.EVERYTHING);
    sectionSwitch.visibleNames().forEach(name -> names.put(name, name));
    return ChoiceAccumulator.single(currentInput, names);
  }
}
