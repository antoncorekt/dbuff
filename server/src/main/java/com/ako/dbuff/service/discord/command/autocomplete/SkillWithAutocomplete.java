package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.SkillNameIndex;
import com.ako.dbuff.service.discord.command.SkillsOptions;
import org.springframework.stereotype.Component;

/** The second skill of {@code /skills skill:<a> with:<b>}: the same list as the first. */
@Component
public class SkillWithAutocomplete extends SkillAutocomplete {
  public SkillWithAutocomplete(SkillNameIndex skills) {
    super(skills);
  }

  @Override
  public String getOptionName() {
    return SkillsOptions.WITH;
  }
}
