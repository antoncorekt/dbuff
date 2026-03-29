package com.ako.dbuff.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration properties for OpenAI API integration. */
@Data
@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAiConfig {

  /** OpenAI API key. */
  private String apiKey;

  /** OpenAI model to use (e.g., "gpt-4", "gpt-3.5-turbo"). */
  private String model = "gpt-4o-mini";

  /** Maximum tokens for the response. */
  private Integer maxTokens = 4000;

  /** Temperature for response generation (0.0 - 2.0). */
  private Double temperature = 0.7;

  /** API base URL (useful for proxies or alternative endpoints). */
  private String baseUrl = "https://api.openai.com/v1";

  /** Request timeout in seconds. */
  private Integer timeoutSeconds = 60;

  /** Whether the OpenAI integration is enabled. */
  private Boolean enabled = true;

  /**
   * Maximum context window size for the model in tokens. gpt-4o-mini: 128k, gpt-4: 8k,
   * gpt-3.5-turbo: 16k Set conservatively to leave room for response.
   */
  private Integer maxContextTokens = 120000;

  /** Approximate characters per token ratio. English text averages ~4 characters per token. */
  private Double charsPerToken = 4.0;
}
