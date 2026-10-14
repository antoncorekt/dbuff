package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.config.OpenAiConfig;
import com.ako.dbuff.service.ai.OpenAiClientProvider;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatCompletionMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OpenAiReasonsModelTest {
  private final ReplayReportAiProperties properties = new ReplayReportAiProperties();
  private final OpenAiConfig openAi = new OpenAiConfig();
  private final OpenAiReasonsModel model =
      new OpenAiReasonsModel(mock(OpenAiClientProvider.class), openAi, properties);
  private final ReasonsPrompt prompt = new ReasonsPrompt("system text", "user text");

  private static ChatCompletion completion(
      ChatCompletion.Choice.FinishReason reason, String content) {
    return ChatCompletion.builder()
        .id("c1")
        .created(1L)
        .model("m")
        .choices(
            List.of(
                ChatCompletion.Choice.builder()
                    .finishReason(reason)
                    .index(0L)
                    .logprobs(
                        ChatCompletion.Choice.Logprobs.builder()
                            .content(List.of())
                            .refusal(List.of())
                            .build())
                    .message(ChatCompletionMessage.builder().content(content).refusal("").build())
                    .build()))
        .build();
  }

  @Test
  void aStandardModelGetsMaxCompletionTokensAndTemperature_neverMaxTokens() {
    ChatCompletionCreateParams params = model.params(prompt, "gpt-4o-mini");

    assertThat(params.model().toString()).contains("gpt-4o-mini");
    assertThat(params.maxCompletionTokens()).contains(4000L);
    assertThat(params.maxTokens()).isEmpty();
    assertThat(params.temperature()).contains(0.2);
    assertThat(params.messages()).hasSize(2);
  }

  @Test
  void reasoningFamiliesGetNoTemperature() {
    for (String name : List.of("o1-mini", "o3", "o4-mini", "gpt-5", "GPT-5-mini")) {
      assertThat(model.params(prompt, name).temperature()).as(name).isEmpty();
      assertThat(model.params(prompt, name).maxCompletionTokens()).as(name).isPresent();
    }
  }

  @Test
  void theNoTemperaturePrefixesAreConfigurable() {
    properties.setNoTemperatureModelPrefixes(List.of("custom-"));

    assertThat(model.params(prompt, "custom-1").temperature()).isEmpty();
    assertThat(model.params(prompt, "gpt-5").temperature()).isPresent();
  }

  @Test
  void aStoppedAnswerIsReturnedWithItsTokens() {
    ModelAnswer answer =
        model.answer(completion(ChatCompletion.Choice.FinishReason.STOP, "[]"), "gpt-4o-mini");

    assertThat(answer.text()).isEqualTo("[]");
    assertThat(answer.model()).isEqualTo("gpt-4o-mini");
  }

  @Test
  void anAnswerCutByTheTokenLimitIsAFailureWithAClearReason() {
    assertThatThrownBy(
            () -> model.answer(completion(ChatCompletion.Choice.FinishReason.LENGTH, "[{"), "m"))
        .isInstanceOf(ReasonsModelException.class)
        .hasMessageContaining("cut by the token limit");
  }

  @Test
  void anEmptyContentIsAFailureWithAClearReason() {
    assertThatThrownBy(
            () -> model.answer(completion(ChatCompletion.Choice.FinishReason.STOP, " "), "m"))
        .isInstanceOf(ReasonsModelException.class)
        .hasMessageContaining("no content");
  }
}
