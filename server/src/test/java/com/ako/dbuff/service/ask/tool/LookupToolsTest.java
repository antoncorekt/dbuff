package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.NameResolution;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillLookup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LookupToolsTest {

  private static final AskContext CTX =
      new AskContext("i", "c", List.of(), null, LocalDate.of(2026, 11, 22), Set.of());

  @Test
  void unknownSkillCarriesTheSuggestion() throws Exception {
    ConstantNameResolver names = mock(ConstantNameResolver.class);
    when(names.resolveAbilities(Set.of("blnk")))
        .thenReturn(new NameResolution(Set.of(), Set.of("blnk")));
    when(names.suggestAbility("blnk")).thenReturn(Optional.of("antimage_blink"));
    SkillLookupTool tool =
        new SkillLookupTool(
            mock(SkillLookup.class), names, mock(ConstantsManagers.class), new AskToolArgs(null));
    JsonNode input = new ObjectMapper().readTree("{\"mode\":\"skill\",\"skill\":\"blnk\"}");

    assertThatThrownBy(() -> tool.call(input, CTX))
        .isInstanceOf(AskToolException.class)
        .hasMessageContaining("blnk")
        .hasMessageContaining("antimage_blink");
  }
}
