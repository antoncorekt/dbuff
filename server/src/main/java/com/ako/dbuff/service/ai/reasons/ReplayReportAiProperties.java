package com.ako.dbuff.service.ai.reasons;

import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bound from {@code replay.report.ai.*}: the AI "top 5 reasons" report. Off until {@code enabled}
 * is turned on; the OpenAI key, model and request timeout come from {@code openai.*} unless set
 * here.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.report.ai")
public class ReplayReportAiProperties {
  /** The reasons report asks for exactly this many reasons at most. */
  public static final int MAX_REASONS = 5;

  private static final int MIN_FACTS_TOKEN_BUDGET = 500;
  private static final int MAX_FACTS_TOKEN_BUDGET = 100_000;
  private static final int MAX_CALLS_PER_DAY = 100;

  /** The switch of the whole feature; nothing calls the model while it is off. */
  private boolean enabled = false;

  /** The most tokens of facts sent to the model; the lowest-weight facts are dropped above it. */
  private int factsTokenBudget = 8000;

  /** The language the reasons are written in. */
  private String language = "English";

  /** The request timeout in seconds; the {@code openai.timeout-seconds} value when not set. */
  private Integer timeoutSeconds;

  /** The model; the {@code openai.model} value when not set. */
  private String model;

  /**
   * The models {@code /match ai} may be asked to use instead of {@link #model}; the default model
   * is always allowed. Unset means the default model only.
   */
  private List<String> allowedModels;

  /**
   * Models whose names start with one of these take no {@code temperature} (reasoning families
   * accept only their default); the request omits it for them.
   */
  private List<String> noTemperatureModelPrefixes = List.of("o1", "o3", "o4", "gpt-5");

  /** The most tokens of the answer, reasoning tokens of the reasoning models included. */
  private int maxOutputTokens = 4000;

  /** The most paid model calls per match in a day; stored answers are reused without a call. */
  private int maxCallsPerMatchPerDay = 3;

  @PostConstruct
  public void validate() {
    if (factsTokenBudget < MIN_FACTS_TOKEN_BUDGET || factsTokenBudget > MAX_FACTS_TOKEN_BUDGET) {
      throw new IllegalStateException(
          "replay.report.ai.facts-token-budget must be "
              + MIN_FACTS_TOKEN_BUDGET
              + " to "
              + MAX_FACTS_TOKEN_BUDGET
              + ", got "
              + factsTokenBudget);
    }
    if (language == null || language.isBlank()) {
      throw new IllegalStateException("replay.report.ai.language must not be blank");
    }
    if (timeoutSeconds != null && timeoutSeconds < 1) {
      throw new IllegalStateException(
          "replay.report.ai.timeout-seconds must be positive, got " + timeoutSeconds);
    }
    if (model != null && model.isBlank()) {
      throw new IllegalStateException("replay.report.ai.model must not be blank when set");
    }
    if (allowedModels != null
        && (allowedModels.isEmpty()
            || allowedModels.stream().anyMatch(m -> m == null || m.isBlank()))) {
      throw new IllegalStateException(
          "replay.report.ai.allowed-models must list at least one model and no blank one, got "
              + allowedModels);
    }
    if (noTemperatureModelPrefixes == null
        || noTemperatureModelPrefixes.stream().anyMatch(p -> p == null || p.isBlank())) {
      throw new IllegalStateException(
          "replay.report.ai.no-temperature-model-prefixes must not hold a blank prefix, got "
              + noTemperatureModelPrefixes);
    }
    if (maxOutputTokens < 200 || maxOutputTokens > 32_000) {
      throw new IllegalStateException(
          "replay.report.ai.max-output-tokens must be 200 to 32000, got " + maxOutputTokens);
    }
    if (maxCallsPerMatchPerDay < 1 || maxCallsPerMatchPerDay > MAX_CALLS_PER_DAY) {
      throw new IllegalStateException(
          "replay.report.ai.max-calls-per-match-per-day must be 1 to "
              + MAX_CALLS_PER_DAY
              + ", got "
              + maxCallsPerMatchPerDay);
    }
  }
}
