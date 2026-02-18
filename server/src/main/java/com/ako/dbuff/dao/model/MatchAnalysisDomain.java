package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain entity for storing AI-generated match analysis results. One analysis can cover multiple
 * matches (when analyzed together) or a single match.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MatchAnalysisDomain {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** The AI-generated analysis text. */
  @Lob
  @Column(columnDefinition = "TEXT")
  private String analysisText;

  /** The AI provider used for this analysis (e.g., "openai", "gemini"). */
  private String aiProvider;

  /** The model used for analysis (e.g., "gpt-4", "gpt-3.5-turbo"). */
  private String aiModel;

  /** Timestamp when the analysis was created. */
  private LocalDateTime createdAt;

  /** The context prompt that was sent to the AI. */
  @Lob
  @Column(columnDefinition = "TEXT")
  private String contextPrompt;

  /** Comma-separated list of match IDs that were analyzed together. */
  private String matchIds;

  /** Number of matches included in this analysis. */
  private Integer matchCount;

  /** Total tokens used for this analysis (if available from AI provider). */
  private Integer tokensUsed;

  /** Whether the analysis was successful. */
  private Boolean success;

  /** Error message if analysis failed. */
  @Column(length = 1000)
  private String errorMessage;
}
