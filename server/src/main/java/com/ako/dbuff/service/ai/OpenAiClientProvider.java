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
  private final OpenAIClient noRetryClient;

  public OpenAiClientProvider(OpenAiConfig config) {
    String key = config.getApiKey();
    boolean keyed = key != null && !key.isBlank();
    this.client = keyed ? OpenAIOkHttpClient.builder().apiKey(key).build() : null;
    this.noRetryClient =
        keyed ? OpenAIOkHttpClient.builder().apiKey(key).maxRetries(0).build() : null;
  }

  public Optional<OpenAIClient> client() {
    return Optional.ofNullable(client);
  }

  /** A client that never retries, so a request's timeout is the most a call can take. */
  public Optional<OpenAIClient> noRetryClient() {
    return Optional.ofNullable(noRetryClient);
  }
}
