package com.ako.dbuff.resources.model;

import com.ako.dbuff.service.ai.config.AiPromptFieldConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for the analyze matches endpoint. Contains optional custom prompt and field
 * configuration for AI analysis.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnalyzeMatchesRequest {

  /**
   * Optional custom prompt to append to the AI analysis context. Can be used to ask specific
   * questions or focus on particular aspects.
   */
  private String customPrompt;

  /**
   * Optional configuration for which fields to include in the AI prompt. If null, default
   * configuration will be used.
   *
   * <p>Example configuration:
   *
   * <pre>
   * {
   *   "fieldConfig": {
   *     "matchFields": {
   *       "includeDuration": true,
   *       "includeRadiantScore": true,
   *       "includeDireScore": true,
   *       "includeRadiantWin": true,
   *       "includePatch": true,
   *       "includeGameMode": true
   *     },
   *     "playerStatisticFields": {
   *       "includeKda": true,
   *       "includeGoldPerMin": true,
   *       "includeXpPerMin": true,
   *       "includeLastHits": true,
   *       "includeDenies": true,
   *       "includeLane": true,
   *       "includeHeroDamage": false,
   *       "includeTowerDamage": false
   *     },
   *     "itemFields": {
   *       "includeItemName": true,
   *       "includePurchaseTime": true,
   *       "includeIsNeutral": true
   *     },
   *     "abilityFields": {
   *       "includeAbilityName": true,
   *       "includeDamageDealt": false,
   *       "includeUseCount": false
   *     },
   *     "includeAbilities": true,
   *     "includeItems": true,
   *     "filterItemsToFocusPlayers": true,
   *     "includeNeutralItems": false,
   *     "responseLanguage": "Russian",
   *     "includeSarcasm": true,
   *     "customSystemPrompt": "Focus on teamfight analysis",
   *     "customAnalysisInstructions": "Also analyze ward placement patterns"
   *   }
   * }
   * </pre>
   */
  private AiPromptFieldConfig fieldConfig;

  /**
   * Preset configuration name to use. Available presets: "default", "minimal", "full". If both
   * fieldConfig and preset are provided, fieldConfig takes precedence.
   */
  private String preset;
}
