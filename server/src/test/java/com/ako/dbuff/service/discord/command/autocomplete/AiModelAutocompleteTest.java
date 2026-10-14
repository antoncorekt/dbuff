package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.ai.reasons.AiReasonsService;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiModelAutocompleteTest {
  private final AiReasonsService service = mock(AiReasonsService.class);
  private final AiModelAutocomplete provider = new AiModelAutocomplete(service);

  @Test
  void servesTheModelOptionOfMatchAi() {
    assertThat(provider.getCommandName()).isEqualTo("match");
    assertThat(provider.getSubcommandName()).isEqualTo("ai");
    assertThat(provider.getOptionName()).isEqualTo("model");
  }

  @Test
  void offersOnlyTheAllowedModelsThatMatchTheInput() {
    when(service.allowedModels()).thenReturn(List.of("gpt-4o-mini", "gpt-4o", "o3"));

    assertThat(provider.getChoices("gpt", null))
        .extracting(c -> c.getAsString())
        .containsExactlyInAnyOrder("gpt-4o-mini", "gpt-4o");
    assertThat(provider.getChoices("", null)).hasSize(3);
  }
}
