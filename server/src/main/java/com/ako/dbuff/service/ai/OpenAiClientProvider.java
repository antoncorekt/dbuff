package com.ako.dbuff.service.ai;

import com.ako.dbuff.config.OpenAiConfig;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The one OpenAI client of the application, absent when no API key is configured. */
@Component
public class OpenAiClientProvider {
  private final OpenAIClient client;

  public OpenAiClientProvider(OpenAiConfig config) {
    String key = config.getApiKey();
    this.client =
        key == null || key.isBlank() ? null : OpenAIOkHttpClient.builder().apiKey(key).build();
  }

  public Optional<OpenAIClient> client() {
    return Optional.ofNullable(client);
  }
}
