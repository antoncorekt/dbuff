package com.ako.dbuff.service.ai;

import com.ako.dbuff.dao.model.MatchAnalysisDomain;
import com.ako.dbuff.service.ai.model.MatchAnalysisRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for AI-based match analysis services. Implementations can use different AI providers
 * (OpenAI, Gemini, etc.).
 */
public interface MatchAnalysisAiService {

  /**
   * Returns the name of the AI provider (e.g., "openai", "gemini").
   *
   * @return the provider name
   */
  String getProviderName();

  /**
   * Analyzes match statistics using AI and returns the analysis result.
   *
   * @param request the analysis request containing match data and context
   * @return the analysis result stored in the database
   */
  MatchAnalysisDomain analyzeMatches(MatchAnalysisRequest request);

  /**
   * Analyzes match statistics asynchronously using AI.
   *
   * @param request the analysis request containing match data and context
   * @return a CompletableFuture containing the analysis result
   */
  CompletableFuture<MatchAnalysisDomain> analyzeMatchesAsync(MatchAnalysisRequest request);

  /**
   * Analyzes each match individually and returns a list of analysis results.
   *
   * @param request the analysis request containing match data and context
   * @return list of analysis results, one per match
   */
  List<MatchAnalysisDomain> analyzeMatchesIndividually(MatchAnalysisRequest request);

  /**
   * Analyzes each match individually asynchronously.
   *
   * @param request the analysis request containing match data and context
   * @return a CompletableFuture containing the list of analysis results
   */
  CompletableFuture<List<MatchAnalysisDomain>> analyzeMatchesIndividuallyAsync(
      MatchAnalysisRequest request);

  /**
   * Checks if the AI service is available and properly configured.
   *
   * @return true if the service is ready to use
   */
  boolean isAvailable();
}
