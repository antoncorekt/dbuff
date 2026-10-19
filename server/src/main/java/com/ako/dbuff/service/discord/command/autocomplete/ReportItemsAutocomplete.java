package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyFile;
import org.springframework.stereotype.Component;

/** The {@code items:} option of {@code /match report}. */
@Component
public class ReportItemsAutocomplete extends CatalogItemsAutocomplete {
  public ReportItemsAutocomplete(ItemEfficiencyFile catalog) {
    super(catalog);
  }

  @Override
  public String getCommandName() {
    return "match";
  }

  @Override
  public String getSubcommandName() {
    return "report";
  }
}
