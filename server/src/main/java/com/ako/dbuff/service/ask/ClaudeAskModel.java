package com.ako.dbuff.service.ask;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link AskModel} on the Anthropic Messages API. The client is built on first use. */
@Component
@RequiredArgsConstructor
public class ClaudeAskModel implements AskModel {

  private final AskProperties properties;

  private volatile AnthropicClient client;

  @Override
  public BetaMessage create(MessageCreateParams params) {
    return client().beta().messages().create(params);
  }

  @Override
  public boolean available() {
    String key = properties.getApiKey();
    return key != null && !key.isBlank();
  }

  private AnthropicClient client() {
    AnthropicClient local = client;
    if (local == null) {
      synchronized (this) {
        local = client;
        if (local == null) {
          local =
              AnthropicOkHttpClient.builder()
                  .apiKey(properties.getApiKey())
                  .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                  .build();
          client = local;
        }
      }
    }
    return local;
  }
}
