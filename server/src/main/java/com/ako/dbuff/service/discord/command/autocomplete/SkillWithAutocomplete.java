package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.SkillNameIndex;
import com.ako.dbuff.service.discord.command.SkillsOptions;
import org.springframework.stereotype.Component;

/** {@code /skills with:}, the second skill of a pair: the same list as the first. */
@Component
public class SkillWithAutocomplete extends SkillPickerAutocomplete {
  public SkillWithAutocomplete(SkillNameIndex skills) {
    super(skills);
  }

  @Override
  public String getOptionName() {
    return SkillsOptions.WITH;
  }
}
