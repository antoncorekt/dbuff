package com.ako.dbuff.service.ai;

import com.ako.dbuff.config.OpenAiConfig;
import com.ako.dbuff.dao.model.MatchAnalysisDomain;
import com.ako.dbuff.dao.repo.MatchAnalysisRepo;
import com.ako.dbuff.executors.Executors;
import com.ako.dbuff.service.ai.config.AiPromptBuilder;
import com.ako.dbuff.service.ai.config.AiPromptFieldConfig;
import com.ako.dbuff.service.ai.model.MatchAnalysisRequest;
import com.ako.dbuff.service.ai.model.MatchWithPlayerStatistics;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatCompletionMessageParam;
import com.openai.models.ChatCompletionUserMessageParam;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** OpenAI implementation of the match analysis AI service. */
@Slf4j
@Service
public class OpenAiMatchAnalysisService implements MatchAnalysisAiService {

  private static final String PROVIDER_NAME = "openai";

  private final OpenAiConfig openAiConfig;
  private final MatchAnalysisRepo matchAnalysisRepo;
  private final OpenAIClient openAIClient;
  private final AiPromptBuilder aiPromptBuilder;

  public OpenAiMatchAnalysisService(
      OpenAiConfig openAiConfig,
      MatchAnalysisRepo matchAnalysisRepo,
      AiPromptBuilder aiPromptBuilder) {
    this.openAiConfig = openAiConfig;
    this.matchAnalysisRepo = matchAnalysisRepo;
    this.aiPromptBuilder = aiPromptBuilder;

    if (openAiConfig.getApiKey() != null && !openAiConfig.getApiKey().isBlank()) {
      this.openAIClient = OpenAIOkHttpClient.builder().apiKey(openAiConfig.getApiKey()).build();
    } else {
      this.openAIClient = null;
      log.warn("OpenAI API key not configured. OpenAI service will not be available.");
    }
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public MatchAnalysisDomain analyzeMatches(MatchAnalysisRequest request) {
    if (!isAvailable()) {
      return createFailedAnalysis(request, "OpenAI service is not available or not configured");
    }

    try {
      // Get field configuration from request or use default
      AiPromptFieldConfig config =
          request.getFieldConfig() != null
              ? request.getFieldConfig()
              : AiPromptFieldConfig.defaultConfig();

      // Build prompts using the configurable prompt builder
      String systemPrompt = aiPromptBuilder.buildSystemPrompt(request, config);
      String dataPrompt = aiPromptBuilder.buildDataPrompt(request, config);

      log.debug("Data prompt: {}", dataPrompt);

      // Combine system prompt and user prompt into a single message
      String fullPrompt = systemPrompt + "\n\n" + dataPrompt;

      // Check and truncate if needed to fit within context window
      fullPrompt = truncatePromptIfNeeded(fullPrompt, request, config);

      int estimatedTokens = estimateTokenCount(fullPrompt);
      log.info(
          "Prompt length: {} chars, estimated tokens: {}", fullPrompt.length(), estimatedTokens);

      // Build messages list using the proper API
      List<ChatCompletionMessageParam> messages = new ArrayList<>();

      messages.add(
          ChatCompletionMessageParam.ofChatCompletionUserMessageParam(
              ChatCompletionUserMessageParam.builder()
                  .content(ChatCompletionUserMessageParam.Content.ofTextContent(fullPrompt))
                  .role(ChatCompletionUserMessageParam.Role.USER)
                  .build()));

      ChatCompletionCreateParams params =
          ChatCompletionCreateParams.builder()
              .model(openAiConfig.getModel())
              .messages(messages)
              .maxTokens(openAiConfig.getMaxTokens())
              .temperature(openAiConfig.getTemperature())
              .build();

      log.info("Starting AI request with {} estimated tokens", estimatedTokens);

      ChatCompletion completion = openAIClient.chat().completions().create(params);

      String analysisText =
          completion.choices().stream()
              .findFirst()
              .map(choice -> choice.message().content().orElse(""))
              .orElse("");

      log.info("analysisText: {}", analysisText);

      Integer tokensUsed = completion.usage().map(usage -> (int) usage.totalTokens()).orElse(null);

      String matchIds =
          request.getMatchesWithStatistics().stream()
              .map(m -> String.valueOf(m.getMatch().getId()))
              .collect(Collectors.joining(","));

      MatchAnalysisDomain analysis =
          MatchAnalysisDomain.builder()
              .analysisText(analysisText)
              .aiProvider(PROVIDER_NAME)
              .aiModel(openAiConfig.getModel())
              .createdAt(LocalDateTime.now())
              .contextPrompt(fullPrompt)
              .matchIds(matchIds)
              .matchCount(request.getMatchesWithStatistics().size())
              .tokensUsed(tokensUsed)
              .success(true)
              .build();

      return matchAnalysisRepo.save(analysis);

    } catch (Exception e) {
      log.error("Failed to analyze matches with OpenAI: {}", e.getMessage(), e);
      return createFailedAnalysis(request, e.getMessage());
    }
  }

  @Override
  public CompletableFuture<MatchAnalysisDomain> analyzeMatchesAsync(MatchAnalysisRequest request) {
    return CompletableFuture.supplyAsync(
        () -> analyzeMatches(request), Executors.PROCESSOR_VIRT_EXECUTOR_SERVICE);
  }

  @Override
  public List<MatchAnalysisDomain> analyzeMatchesIndividually(MatchAnalysisRequest request) {
    List<MatchAnalysisDomain> results = new ArrayList<>();

    for (MatchWithPlayerStatistics matchWithStats : request.getMatchesWithStatistics()) {
      MatchAnalysisRequest singleMatchRequest =
          MatchAnalysisRequest.builder()
              .matchesWithStatistics(List.of(matchWithStats))
              .contextPrompt(request.getContextPrompt())
              .focusPlayerIds(request.getFocusPlayerIds())
              .focusPlayerNames(request.getFocusPlayerNames())
              .analyzePerMatch(true)
              .fieldConfig(request.getFieldConfig())
              .build();

      MatchAnalysisDomain analysis = analyzeMatches(singleMatchRequest);
      results.add(analysis);
    }

    return results;
  }

  @Override
  public CompletableFuture<List<MatchAnalysisDomain>> analyzeMatchesIndividuallyAsync(
      MatchAnalysisRequest request) {
    return CompletableFuture.supplyAsync(
        () -> analyzeMatchesIndividually(request), Executors.PROCESSOR_VIRT_EXECUTOR_SERVICE);
  }

  @Override
  public boolean isAvailable() {
    return openAiConfig.getEnabled()
        && openAIClient != null
        && openAiConfig.getApiKey() != null
        && !openAiConfig.getApiKey().isBlank();
  }

  private MatchAnalysisDomain createFailedAnalysis(MatchAnalysisRequest request, String error) {
    String matchIds =
        request.getMatchesWithStatistics().stream()
            .map(m -> String.valueOf(m.getMatch().getId()))
            .collect(Collectors.joining(","));

    MatchAnalysisDomain analysis =
        MatchAnalysisDomain.builder()
            .aiProvider(PROVIDER_NAME)
            .aiModel(openAiConfig.getModel())
            .createdAt(LocalDateTime.now())
            .matchIds(matchIds)
            .matchCount(request.getMatchesWithStatistics().size())
            .success(false)
            .errorMessage(error)
            .build();

    return matchAnalysisRepo.save(analysis);
  }

  /**
   * Estimates the number of tokens in a text string. Uses a simple character-based estimation
   * (approximately 4 chars per token for English).
   */
  private int estimateTokenCount(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    return (int) Math.ceil(text.length() / openAiConfig.getCharsPerToken());
  }

  /**
   * Truncates the prompt if it exceeds the model's context window. Prioritizes keeping the system
   * prompt and essential match data.
   */
  private String truncatePromptIfNeeded(
      String fullPrompt, MatchAnalysisRequest request, AiPromptFieldConfig config) {
    int maxInputTokens = openAiConfig.getMaxContextTokens() - openAiConfig.getMaxTokens();
    int estimatedTokens = estimateTokenCount(fullPrompt);

    if (estimatedTokens <= maxInputTokens) {
      return fullPrompt;
    }

    log.warn(
        "Prompt exceeds context limit. Estimated: {} tokens, Max: {} tokens. Truncating...",
        estimatedTokens,
        maxInputTokens);

    // Calculate how many characters we can keep
    int maxChars = (int) (maxInputTokens * openAiConfig.getCharsPerToken());

    // Strategy: Keep the system prompt and truncate the data section
    String systemPrompt = aiPromptBuilder.buildSystemPrompt(request, config);
    int systemPromptLength = systemPrompt.length();

    // Reserve space for system prompt + separator + truncation notice
    String truncationNotice =
        "\n\n[NOTE: Some match data was truncated due to length limits. "
            + "Analysis is based on available data.]\n\n";
    int reservedLength = systemPromptLength + truncationNotice.length() + 100;

    int availableForData = maxChars - reservedLength;

    if (availableForData <= 0) {
      // Even system prompt is too long, just truncate everything
      log.error("System prompt alone exceeds context limit. Truncating aggressively.");
      return fullPrompt.substring(0, Math.min(fullPrompt.length(), maxChars));
    }

    // Extract the data portion (everything after system prompt)
    String dataPrompt = fullPrompt.substring(systemPromptLength);

    // Truncate data portion intelligently - try to keep complete sections
    String truncatedData = truncateDataSection(dataPrompt, availableForData, config);

    String result = systemPrompt + truncationNotice + truncatedData;

    log.info(
        "Truncated prompt from {} to {} chars (estimated {} tokens)",
        fullPrompt.length(),
        result.length(),
        estimateTokenCount(result));

    return result;
  }

  /** Truncates the data section while trying to preserve complete match blocks. */
  private String truncateDataSection(String dataPrompt, int maxLength, AiPromptFieldConfig config) {
    if (dataPrompt.length() <= maxLength) {
      return dataPrompt;
    }

    // Try to find a good break point - look for match separators or section headers
    String[] breakPoints = {
      "=== MATCH ID:", "PLAYER STATISTICS:", "PLAYER ABILITIES:", "PLAYER ITEMS:", "\n\n"
    };

    String truncated = dataPrompt.substring(0, maxLength);

    // Find the last complete section
    int bestBreakPoint = -1;
    for (String breakPoint : breakPoints) {
      int lastIndex = truncated.lastIndexOf(breakPoint);
      if (lastIndex > maxLength / 2 && lastIndex > bestBreakPoint) {
        bestBreakPoint = lastIndex;
      }
    }

    if (bestBreakPoint > 0) {
      truncated = truncated.substring(0, bestBreakPoint);
    }

    // Add the analysis request section at the end based on config
    StringBuilder analysisRequest = new StringBuilder("\n\nPlease provide:\n");
    analysisRequest.append("1. Overall match summary\n");
    analysisRequest.append("2. Standout performances (both positive and negative)\n");
    analysisRequest.append("3. Key statistics highlights\n");
    if (config.isIncludeAbilities()) {
      analysisRequest.append(
          "4. Ability draft analysis - synergies and strategy behind ability choices\n");
    }
    if (config.isIncludeItems()) {
      analysisRequest.append(
          "5. Item build analysis - timing, effectiveness, and alignment with abilities\n");
    }
    analysisRequest.append("6. Recommendations for tracked players\n");

    return truncated + analysisRequest.toString();
  }
}
