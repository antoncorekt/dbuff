package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.config.OpenAiConfig;
import com.ako.dbuff.service.ai.OpenAiClientProvider;
import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatCompletionMessageParam;
import com.openai.models.ChatCompletionSystemMessageParam;
import com.openai.models.ChatCompletionUserMessageParam;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The reasons model on OpenAI chat completions, on a client that does not retry. The request uses
 * {@code max_completion_tokens} and leaves out {@code temperature} for the reasoning families.
 */
@Component
@RequiredArgsConstructor
public class OpenAiReasonsModel implements ReasonsModel {
  private static final double TEMPERATURE = 0.2;

  private final OpenAiClientProvider clients;
  private final OpenAiConfig openAi;
  private final ReplayReportAiProperties properties;

  @Override
  public boolean available() {
    return clients.noRetryClient().isPresent();
  }

  @Override
  public String defaultModel() {
    return properties.getModel() != null ? properties.getModel() : openAi.getModel();
  }

  @Override
  public ModelAnswer ask(ReasonsPrompt prompt, String model) {
    OpenAIClient client =
        clients
            .noRetryClient()
            .orElseThrow(() -> new ReasonsModelException("no OpenAI API key", null));
    int seconds =
        properties.getTimeoutSeconds() != null
            ? properties.getTimeoutSeconds()
            : openAi.getTimeoutSeconds();
    ChatCompletion completion;
    try {
      completion =
          client
              .chat()
              .completions()
              .create(
                  params(prompt, model),
                  RequestOptions.builder().timeout(Duration.ofSeconds(seconds)).build());
    } catch (RuntimeException e) {
      throw new ReasonsModelException("OpenAI call failed: " + e.getMessage(), e);
    }
    return answer(completion, model);
  }

  ChatCompletionCreateParams params(ReasonsPrompt prompt, String model) {
    ChatCompletionCreateParams.Builder builder =
        ChatCompletionCreateParams.builder()
            .model(model)
            .messages(
                List.of(
                    ChatCompletionMessageParam.ofChatCompletionSystemMessageParam(
                        ChatCompletionSystemMessageParam.builder()
                            .content(
                                ChatCompletionSystemMessageParam.Content.ofTextContent(
                                    prompt.system()))
                            .role(ChatCompletionSystemMessageParam.Role.SYSTEM)
                            .build()),
                    ChatCompletionMessageParam.ofChatCompletionUserMessageParam(
                        ChatCompletionUserMessageParam.builder()
                            .content(
                                ChatCompletionUserMessageParam.Content.ofTextContent(prompt.user()))
                            .role(ChatCompletionUserMessageParam.Role.USER)
                            .build())))
            .maxCompletionTokens(properties.getMaxOutputTokens());
    if (!rejectsTemperature(model)) {
      builder.temperature(TEMPERATURE);
    }
    return builder.build();
  }

  ModelAnswer answer(ChatCompletion completion, String model) {
    ChatCompletion.Choice choice =
        completion.choices().stream()
            .findFirst()
            .orElseThrow(() -> new ReasonsModelException("the model returned no choice", null));
    if (choice.finishReason().known() == ChatCompletion.Choice.FinishReason.Known.LENGTH) {
      throw new ReasonsModelException("answer cut by the token limit", null);
    }
    String text = choice.message().content().orElse("");
    if (text.isBlank()) {
      throw new ReasonsModelException(
          "the model returned no content (a refusal or an empty answer)", null);
    }
    Integer tokens = completion.usage().map(u -> (int) u.totalTokens()).orElse(null);
    return new ModelAnswer(text, tokens, model);
  }

  private boolean rejectsTemperature(String model) {
    String name = model.toLowerCase(Locale.ROOT);
    return properties.getNoTemperatureModelPrefixes().stream()
        .anyMatch(p -> name.startsWith(p.toLowerCase(Locale.ROOT)));
  }
}
