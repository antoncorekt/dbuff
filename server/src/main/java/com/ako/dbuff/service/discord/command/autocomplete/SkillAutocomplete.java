package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.SkillNameIndex;
import com.ako.dbuff.service.discord.command.SkillsOptions;
import org.springframework.stereotype.Component;

/** {@code /skills skill:}. */
@Component
public class SkillAutocomplete extends SkillPickerAutocomplete {
  public SkillAutocomplete(SkillNameIndex skills) {
    super(skills);
  }

  @Override
  public String getOptionName() {
    return SkillsOptions.SKILL;
  }
}
