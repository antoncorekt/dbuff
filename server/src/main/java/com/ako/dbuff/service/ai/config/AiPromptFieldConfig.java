package com.ako.dbuff.service.ai.config;

import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for AI prompt field selection.
 * Allows customization of which fields from each entity should be included in the AI prompt.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiPromptFieldConfig {

  /**
   * Configuration for Match entity fields.
   */
  private MatchFieldConfig matchFields;

  /**
   * Configuration for PlayerMatchStatistic entity fields.
   */
  private PlayerStatisticFieldConfig playerStatisticFields;

  /**
   * Configuration for Item entity fields.
   */
  private ItemFieldConfig itemFields;

  /**
   * Configuration for Ability entity fields.
   */
  private AbilityFieldConfig abilityFields;

  /**
   * Custom system prompt additions.
   * Will be appended to the default system prompt.
   */
  private String customSystemPrompt;

  /**
   * Custom analysis instructions.
   * Will be appended to the analysis request section.
   */
  private String customAnalysisInstructions;

  /**
   * Language for the AI response.
   * Default is "Russian".
   */
  @Builder.Default
  private String responseLanguage = "Russian";

  /**
   * Whether to include sarcasm and jokes in the response.
   */
  @Builder.Default
  private boolean includeSarcasm = true;

  /**
   * Whether to include abilities section in the prompt.
   */
  @Builder.Default
  private boolean includeAbilities = true;

  /**
   * Whether to include items section in the prompt.
   */
  @Builder.Default
  private boolean includeItems = true;

  /**
   * Whether to filter items to only focus players.
   */
  @Builder.Default
  private boolean filterItemsToFocusPlayers = true;

  /**
   * Whether to include neutral items.
   */
  @Builder.Default
  private boolean includeNeutralItems = false;

  /**
   * Creates a default configuration with all fields enabled.
   */
  public static AiPromptFieldConfig defaultConfig() {
    return AiPromptFieldConfig.builder()
        .matchFields(MatchFieldConfig.defaultConfig())
        .playerStatisticFields(PlayerStatisticFieldConfig.defaultConfig())
        .itemFields(ItemFieldConfig.defaultConfig())
        .abilityFields(AbilityFieldConfig.defaultConfig())
        .build();
  }

  /**
   * Creates a minimal configuration with only essential fields.
   */
  public static AiPromptFieldConfig minimalConfig() {
    return AiPromptFieldConfig.builder()
        .matchFields(MatchFieldConfig.minimalConfig())
        .playerStatisticFields(PlayerStatisticFieldConfig.minimalConfig())
        .itemFields(ItemFieldConfig.minimalConfig())
        .abilityFields(AbilityFieldConfig.minimalConfig())
        .includeItems(false)
        .build();
  }

  /**
   * Configuration for Match entity fields.
   */
  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class MatchFieldConfig {
    @Builder.Default
    private boolean includeDuration = true;
    @Builder.Default
    private boolean includeRadiantScore = true;
    @Builder.Default
    private boolean includeDireScore = true;
    @Builder.Default
    private boolean includeRadiantWin = true;
    @Builder.Default
    private boolean includePatch = true;
    @Builder.Default
    private boolean includeGameMode = true;
    @Builder.Default
    private boolean includeFirstBloodTime = false;
    @Builder.Default
    private boolean includeStartTime = false;

    public static MatchFieldConfig defaultConfig() {
      return MatchFieldConfig.builder().build();
    }

    public static MatchFieldConfig minimalConfig() {
      return MatchFieldConfig.builder()
          .includeDuration(true)
          .includeRadiantWin(true)
          .includeRadiantScore(true)
          .includeDireScore(true)
          .includePatch(false)
          .includeGameMode(false)
          .build();
    }
  }

  /**
   * Configuration for PlayerMatchStatistic entity fields.
   */
  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class PlayerStatisticFieldConfig {
    // Core stats - always recommended
    @Builder.Default
    private boolean includeKda = true;
    @Builder.Default
    private boolean includeKills = true;
    @Builder.Default
    private boolean includeDeaths = true;
    @Builder.Default
    private boolean includeAssists = true;
    @Builder.Default
    private boolean includeHero = true;
    @Builder.Default
    private boolean includeTeam = true;
    @Builder.Default
    private boolean includeWin = true;

    // Economy stats
    @Builder.Default
    private boolean includeGoldPerMin = true;
    @Builder.Default
    private boolean includeXpPerMin = true;
    @Builder.Default
    private boolean includeNetWorth = false;
    @Builder.Default
    private boolean includeTotalGold = false;
    @Builder.Default
    private boolean includeGoldSpent = false;

    // Farming stats
    @Builder.Default
    private boolean includeLastHits = true;
    @Builder.Default
    private boolean includeDenies = true;
    @Builder.Default
    private boolean includeNeutralKills = false;
    @Builder.Default
    private boolean includeCampsStacked = false;
    @Builder.Default
    private boolean includeCreepsStacked = false;

    // Combat stats
    @Builder.Default
    private boolean includeHeroDamage = false;
    @Builder.Default
    private boolean includeTowerDamage = false;
    @Builder.Default
    private boolean includeHeroHealing = false;
    @Builder.Default
    private boolean includeDamageTaken = false;
    @Builder.Default
    private boolean includeStuns = false;

    // Lane stats
    @Builder.Default
    private boolean includeLane = true;
    @Builder.Default
    private boolean includeLaneRole = false;
    @Builder.Default
    private boolean includeLaneEfficiency = false;

    // Level and progression
    @Builder.Default
    private boolean includeLevel = false;
    @Builder.Default
    private boolean includeHeroVariant = false;

    // Support stats
    @Builder.Default
    private boolean includeObsPlaced = false;
    @Builder.Default
    private boolean includeSenPlaced = false;

    // Timeline stats
    @Builder.Default
    private boolean includeGoldTimeline = false;
    @Builder.Default
    private boolean includeLastHitsTimeline = false;
    @Builder.Default
    private boolean includeXpTimeline = false;

    // Other stats
    @Builder.Default
    private boolean includeTowerKills = false;
    @Builder.Default
    private boolean includeRoshanKills = false;
    @Builder.Default
    private boolean includeCourierKills = false;
    @Builder.Default
    private boolean includeRunePickups = false;
    @Builder.Default
    private boolean includeBuybackCount = false;
    @Builder.Default
    private boolean includeActionsPerMin = false;
    @Builder.Default
    private boolean includeTeamfightParticipation = false;
    @Builder.Default
    private boolean includeRankTier = false;

    // Item flags
    @Builder.Default
    private boolean includeAganim = false;
    @Builder.Default
    private boolean includeAganimShard = false;
    @Builder.Default
    private boolean includeMoonshard = false;

    // Hand damage
    @Builder.Default
    private boolean includeHandDamage = false;

    public static PlayerStatisticFieldConfig defaultConfig() {
      return PlayerStatisticFieldConfig.builder().build();
    }

    public static PlayerStatisticFieldConfig minimalConfig() {
      return PlayerStatisticFieldConfig.builder()
          .includeKda(true)
          .includeKills(false)
          .includeDeaths(false)
          .includeAssists(false)
          .includeHero(true)
          .includeTeam(true)
          .includeWin(true)
          .includeGoldPerMin(true)
          .includeXpPerMin(true)
          .includeLastHits(false)
          .includeDenies(false)
          .includeLane(false)
          .build();
    }

    public static PlayerStatisticFieldConfig fullConfig() {
      return PlayerStatisticFieldConfig.builder()
          .includeNetWorth(true)
          .includeTotalGold(true)
          .includeGoldSpent(true)
          .includeNeutralKills(true)
          .includeCampsStacked(true)
          .includeCreepsStacked(true)
          .includeHeroDamage(true)
          .includeTowerDamage(true)
          .includeHeroHealing(true)
          .includeDamageTaken(true)
          .includeStuns(true)
          .includeLaneRole(true)
          .includeLaneEfficiency(true)
          .includeLevel(true)
          .includeHeroVariant(true)
          .includeObsPlaced(true)
          .includeSenPlaced(true)
          .includeGoldTimeline(true)
          .includeLastHitsTimeline(true)
          .includeXpTimeline(true)
          .includeTowerKills(true)
          .includeRoshanKills(true)
          .includeCourierKills(true)
          .includeRunePickups(true)
          .includeBuybackCount(true)
          .includeActionsPerMin(true)
          .includeTeamfightParticipation(true)
          .includeRankTier(true)
          .includeAganim(true)
          .includeAganimShard(true)
          .includeMoonshard(true)
          .includeHandDamage(true)
          .build();
    }
  }

  /**
   * Configuration for Item entity fields.
   */
  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ItemFieldConfig {
    @Builder.Default
    private boolean includeItemName = true;
    @Builder.Default
    private boolean includePurchaseTime = true;
    @Builder.Default
    private boolean includeIsNeutral = true;
    @Builder.Default
    private boolean includeNeutralEnhancement = false;
    @Builder.Default
    private boolean includeDamageDealt = false;
    @Builder.Default
    private boolean includeDamageReceived = false;
    @Builder.Default
    private boolean includeUseCount = false;

    public static ItemFieldConfig defaultConfig() {
      return ItemFieldConfig.builder().build();
    }

    public static ItemFieldConfig minimalConfig() {
      return ItemFieldConfig.builder()
          .includeItemName(true)
          .includePurchaseTime(true)
          .includeIsNeutral(false)
          .build();
    }

    public static ItemFieldConfig fullConfig() {
      return ItemFieldConfig.builder()
          .includeNeutralEnhancement(true)
          .includeDamageDealt(true)
          .includeDamageReceived(true)
          .includeUseCount(true)
          .build();
    }
  }

  /**
   * Configuration for Ability entity fields.
   */
  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class AbilityFieldConfig {
    @Builder.Default
    private boolean includeAbilityName = true;
    @Builder.Default
    private boolean includeDamageDealt = false;
    @Builder.Default
    private boolean includeDamageReceived = false;
    @Builder.Default
    private boolean includeUseCount = false;

    public static AbilityFieldConfig defaultConfig() {
      return AbilityFieldConfig.builder().build();
    }

    public static AbilityFieldConfig minimalConfig() {
      return AbilityFieldConfig.builder()
          .includeAbilityName(true)
          .build();
    }

    public static AbilityFieldConfig fullConfig() {
      return AbilityFieldConfig.builder()
          .includeDamageDealt(true)
          .includeDamageReceived(true)
          .includeUseCount(true)
          .build();
    }
  }
}
