package com.ako.dbuff.service.ai.model;

import com.ako.dbuff.service.ai.config.AiPromptFieldConfig;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request object for AI analysis containing match statistics and context information.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchAnalysisRequest {

  /** List of matches with their player statistics to analyze. */
  private List<MatchWithPlayerStatistics> matchesWithStatistics;

  /** Additional context text to provide to the AI for analysis. */
  private String contextPrompt;

  /** List of player IDs to focus on during analysis. */
  private List<Long> focusPlayerIds;

  /** List of player names to focus on during analysis. */
  private List<String> focusPlayerNames;

  /**
   * Whether to analyze each match individually or all matches together.
   * If true, AI will be called once per match.
   * If false, AI will be called once for all matches.
   */
  private boolean analyzePerMatch;

  /**
   * Configuration for which fields to include in the AI prompt.
   * If null, default configuration will be used.
   */
  private AiPromptFieldConfig fieldConfig;
}
