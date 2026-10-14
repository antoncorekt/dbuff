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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The reasons model on OpenAI chat completions, with the client and defaults of {@code openai.*}.
 */
@Component
@RequiredArgsConstructor
public class OpenAiReasonsModel implements ReasonsModel {
  private final OpenAiClientProvider clients;
  private final OpenAiConfig openAi;
  private final ReplayReportAiProperties properties;

  @Override
  public boolean available() {
    return clients.client().isPresent();
  }

  @Override
  public String modelName() {
    return properties.getModel() != null ? properties.getModel() : openAi.getModel();
  }

  @Override
  public ModelAnswer ask(ReasonsPrompt prompt) {
    OpenAIClient client =
        clients.client().orElseThrow(() -> new ReasonsModelException("no OpenAI API key", null));
    String model = modelName();
    int seconds =
        properties.getTimeoutSeconds() != null
            ? properties.getTimeoutSeconds()
            : openAi.getTimeoutSeconds();
    ChatCompletionCreateParams params =
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
            .maxTokens(openAi.getMaxTokens())
            .temperature(0.2)
            .build();
    try {
      ChatCompletion completion =
          client
              .chat()
              .completions()
              .create(
                  params, RequestOptions.builder().timeout(Duration.ofSeconds(seconds)).build());
      String text =
          completion.choices().stream()
              .findFirst()
              .map(choice -> choice.message().content().orElse(""))
              .orElse("");
      Integer tokens = completion.usage().map(u -> (int) u.totalTokens()).orElse(null);
      return new ModelAnswer(text, tokens, model);
    } catch (RuntimeException e) {
      throw new ReasonsModelException("OpenAI call failed: " + e.getMessage(), e);
    }
  }
}
