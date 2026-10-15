package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.FakeCommandContext;
import com.ako.dbuff.service.discord.command.SkillNameIndex;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class SkillAutocompleteTest {
  private final SkillNameIndex index = Mockito.mock(SkillNameIndex.class);

  private SkillPickerAutocomplete provider() {
    Map<String, String> skills = new LinkedHashMap<>();
    skills.put("Storm Bolt", "sven_storm_bolt");
    skills.put("Thunder Clap", "brewmaster_thunder_clap");
    Mockito.when(index.displayToInternal()).thenReturn(skills);
    return new SkillAutocomplete(index);
  }

  @Test
  void servesTheSkillAndWithOptionsOfSkills() {
    SkillPickerAutocomplete skill = provider();
    SkillPickerAutocomplete with = new SkillWithAutocomplete(index);

    assertThat(skill.getCommandNames()).containsExactly("skills");
    assertThat(skill.getOptionName()).isEqualTo("skill");
    assertThat(with.getCommandNames()).containsExactly("skills");
    assertThat(with.getOptionName()).isEqualTo("with");
  }

  @Test
  void theTwoOptionsAreSeparateBeansOfOneAbstractBase() {
    assertThat(SkillAutocomplete.class.getSuperclass()).isEqualTo(SkillPickerAutocomplete.class);
    assertThat(SkillWithAutocomplete.class.getSuperclass())
        .isEqualTo(SkillPickerAutocomplete.class);
    assertThat(SkillAutocomplete.class.isAssignableFrom(SkillWithAutocomplete.class)).isFalse();
  }

  @Test
  void offersDisplayNamesAndSubmitsInternalNames() {
    List<Command.Choice> choices =
        provider().getChoices("storm", FakeCommandContext.builder().build());

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getName()).isEqualTo("Storm Bolt");
    assertThat(choices.get(0).getAsString()).isEqualTo("sven_storm_bolt");
  }

  @Test
  void aFailureGivesAnEmptyPickerNotAnException() {
    Mockito.when(index.displayToInternal()).thenThrow(new IllegalStateException("no constants"));

    assertThat(new SkillAutocomplete(index).getChoices("x", FakeCommandContext.builder().build()))
        .isEmpty();
  }
}
