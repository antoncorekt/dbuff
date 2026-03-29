package com.ako.dbuff.service.ai.config;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.service.ai.config.AiPromptFieldConfig.AbilityFieldConfig;
import com.ako.dbuff.service.ai.config.AiPromptFieldConfig.ItemFieldConfig;
import com.ako.dbuff.service.ai.config.AiPromptFieldConfig.MatchFieldConfig;
import com.ako.dbuff.service.ai.config.AiPromptFieldConfig.PlayerStatisticFieldConfig;
import com.ako.dbuff.service.ai.model.MatchAnalysisRequest;
import com.ako.dbuff.service.ai.model.MatchWithPlayerStatistics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Service for building AI prompts based on configurable field selection. */
@Component
public class AiPromptBuilder {

  /** Builds the system prompt based on configuration. */
  public String buildSystemPrompt(MatchAnalysisRequest request, AiPromptFieldConfig config) {
    StringBuilder sb = new StringBuilder();
    sb.append("You are an expert Dota 2 Ability Draft game analyst. ");
    sb.append(
        "This is an Ability Draft mode where players pick abilities and heroes from a shared pool, not standard Dota 2. ");
    sb.append("Average skill level players are playing.\n\n");

    if (config.isIncludeSarcasm()) {
      sb.append("Please use sarcasm and jokes if applicable\n\n");
    }

    sb.append("YOUR ANALYSIS PRIORITIES (in order of importance):\n\n");

    if (config.isIncludeAbilities()) {
      sb.append("1. ABILITY DRAFT STRATEGY ANALYSIS:\n");
      sb.append(
          "   - Analyze the chosen abilities for each player and explain the strategy behind their picks\n");
      sb.append(
          "   - Identify ability synergies and combos (e.g., abilities that work well together)\n");
      sb.append(
          "   - Evaluate how well the picked abilities match the hero's base stats and attack type\n");
      sb.append("   - Highlight creative or unusual ability combinations\n");
      sb.append("   - Point out missed opportunities or suboptimal ability choices\n\n");
    }

    if (config.isIncludeItems()) {
      sb.append("2. ITEM BUILD ANALYSIS:\n");
      sb.append("   - Analyze item choices and how they synergize with the picked abilities\n");
      sb.append(
          "   - IMPORTANT: Evaluate item purchase timing - highlight if items were bought faster or slower than typical benchmarks\n");
      sb.append(
          "   - Common benchmarks: Boots (2-3 min), first major item (12-15 min), second major item (20-25 min)\n");
      sb.append("   - Identify if item builds properly support the ability draft strategy\n");
      sb.append("   - Note any neutral items and their impact\n\n");
    }

    sb.append("3. PERFORMANCE STATISTICS:\n");
    sb.append("   - Analyze KDA (Kills/Deaths/Assists) and what it tells about player impact\n");
    sb.append(
        "   - Evaluate GPM (Gold Per Minute) and XPM (Experience Per Minute) - are they farming efficiently?\n");
    sb.append("   - Last hits and denies - laning phase performance\n");
    sb.append("   - Compare statistics between teams and identify key performers\n");
    sb.append("   - Highlight exceptional or poor performances with specific numbers\n\n");

    sb.append("4. OVERALL MATCH ANALYSIS:\n");
    sb.append("   - Summarize why the winning team won\n");
    sb.append("   - Identify turning points based on the data\n");
    sb.append("   - Provide actionable recommendations for tracked players\n\n");

    sb.append("Please respond in ").append(config.getResponseLanguage()).append(" language.\n");

    // Add focus players section
    List<String> focusPlayerNames = request.getFocusPlayerNames();
    List<Long> focusPlayerIds = request.getFocusPlayerIds();

    if ((focusPlayerNames != null && !focusPlayerNames.isEmpty())
        || (focusPlayerIds != null && !focusPlayerIds.isEmpty())) {
      sb.append("\n--- TRACKED PLAYERS (PRIORITY FOCUS) ---\n");
      sb.append("Pay SPECIAL attention to these players:\n");

      if (focusPlayerNames != null
          && focusPlayerIds != null
          && focusPlayerNames.size() == focusPlayerIds.size()) {
        for (int i = 0; i < focusPlayerNames.size(); i++) {
          sb.append("  - ")
              .append(focusPlayerNames.get(i))
              .append(" (ID: ")
              .append(focusPlayerIds.get(i))
              .append(")\n");
        }
      } else if (focusPlayerNames != null && !focusPlayerNames.isEmpty()) {
        for (String name : focusPlayerNames) {
          sb.append("  - ").append(name).append("\n");
        }
      } else if (focusPlayerIds != null && !focusPlayerIds.isEmpty()) {
        for (Long id : focusPlayerIds) {
          String name = getPlayerName(id);
          sb.append("  - ").append(name).append(" (ID: ").append(id).append(")\n");
        }
      }

      sb.append(
          "\nProvide detailed analysis of their ability choices, item builds, and performance. ");
      sb.append("Give specific recommendations for improvement.\n");
    }

    // Add custom system prompt if provided
    if (config.getCustomSystemPrompt() != null && !config.getCustomSystemPrompt().isBlank()) {
      sb.append("\n--- ADDITIONAL INSTRUCTIONS ---\n");
      sb.append(config.getCustomSystemPrompt()).append("\n");
    }

    if (request.getContextPrompt() != null && !request.getContextPrompt().isBlank()) {
      sb.append("\nAdditional context: ").append(request.getContextPrompt());
    }

    return sb.toString();
  }

  /** Builds the data prompt based on configuration. */
  public String buildDataPrompt(MatchAnalysisRequest request, AiPromptFieldConfig config) {
    StringBuilder sb = new StringBuilder();
    sb.append("Please analyze the following Dota 2 match statistics:\n\n");

    MatchFieldConfig matchConfig =
        config.getMatchFields() != null
            ? config.getMatchFields()
            : MatchFieldConfig.defaultConfig();
    PlayerStatisticFieldConfig playerConfig =
        config.getPlayerStatisticFields() != null
            ? config.getPlayerStatisticFields()
            : PlayerStatisticFieldConfig.defaultConfig();
    ItemFieldConfig itemConfig =
        config.getItemFields() != null ? config.getItemFields() : ItemFieldConfig.defaultConfig();
    AbilityFieldConfig abilityConfig =
        config.getAbilityFields() != null
            ? config.getAbilityFields()
            : AbilityFieldConfig.defaultConfig();

    for (MatchWithPlayerStatistics matchWithStats : request.getMatchesWithStatistics()) {
      MatchDomain match = matchWithStats.getMatch();

      // Build match header
      sb.append(buildMatchHeader(match, matchConfig));

      // Build player statistics table
      sb.append(buildPlayerStatisticsTable(matchWithStats, playerConfig, request));

      // Build abilities section
      if (config.isIncludeAbilities()
          && matchWithStats.getAbilities() != null
          && !matchWithStats.getAbilities().isEmpty()) {
        sb.append(buildAbilitiesSection(matchWithStats, abilityConfig));
      }

      // Build items section
      if (config.isIncludeItems()
          && matchWithStats.getItems() != null
          && !matchWithStats.getItems().isEmpty()) {
        sb.append(buildItemsSection(matchWithStats, itemConfig, config, request));
      }
    }

    // Build analysis request section
    sb.append(buildAnalysisRequestSection(config));

    return sb.toString();
  }

  private String buildMatchHeader(MatchDomain match, MatchFieldConfig config) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== MATCH ID: ").append(match.getId()).append(" ===\n");

    if (config.isIncludeDuration()) {
      sb.append("Duration: ").append(formatDuration(match.getDuration())).append("\n");
    }
    if (config.isIncludeRadiantScore()) {
      sb.append("Radiant Score: ").append(match.getRadiantScore()).append("\n");
    }
    if (config.isIncludeDireScore()) {
      sb.append("Dire Score: ").append(match.getDireScore()).append("\n");
    }
    if (config.isIncludeRadiantWin()) {
      sb.append("Radiant Win: ").append(match.getRadiantWin()).append("\n");
    }
    if (config.isIncludePatch()) {
      sb.append("Patch: ").append(match.getPatchStr()).append("\n");
    }
    if (config.isIncludeGameMode()) {
      sb.append("Game Mode: ").append(match.getGameModeName()).append("\n");
    }
    if (config.isIncludeFirstBloodTime() && match.getFirstBloodTime() != null) {
      sb.append("First Blood: ").append(formatDuration(match.getFirstBloodTime())).append("\n");
    }
    if (config.isIncludeStartTime() && match.getStartTime() != null) {
      sb.append("Start Time: ").append(match.getStartLocalDate()).append("\n");
    }

    sb.append("\n");
    return sb.toString();
  }

  private String buildPlayerStatisticsTable(
      MatchWithPlayerStatistics matchWithStats,
      PlayerStatisticFieldConfig config,
      MatchAnalysisRequest request) {
    StringBuilder sb = new StringBuilder();
    sb.append("PLAYER STATISTICS:\n");

    // Build dynamic header based on config
    List<String> headers = new ArrayList<>();
    List<Integer> widths = new ArrayList<>();

    headers.add("Player (ID)");
    widths.add(30);

    if (config.isIncludeHero()) {
      headers.add("Hero");
      widths.add(15);
    }
    if (config.isIncludeTeam()) {
      headers.add("Team");
      widths.add(8);
    }
    if (config.isIncludeKda()) {
      headers.add("KDA");
      widths.add(6);
    }
    if (config.isIncludeKills()) {
      headers.add("K");
      widths.add(4);
    }
    if (config.isIncludeDeaths()) {
      headers.add("D");
      widths.add(4);
    }
    if (config.isIncludeAssists()) {
      headers.add("A");
      widths.add(4);
    }
    if (config.isIncludeGoldPerMin()) {
      headers.add("GPM");
      widths.add(6);
    }
    if (config.isIncludeXpPerMin()) {
      headers.add("XPM");
      widths.add(6);
    }
    if (config.isIncludeLastHits()) {
      headers.add("LH");
      widths.add(6);
    }
    if (config.isIncludeDenies()) {
      headers.add("Denies");
      widths.add(8);
    }
    if (config.isIncludeWin()) {
      headers.add("Win");
      widths.add(6);
    }
    if (config.isIncludeLane()) {
      headers.add("Lane");
      widths.add(6);
    }
    if (config.isIncludeNetWorth()) {
      headers.add("NW");
      widths.add(8);
    }
    if (config.isIncludeLevel()) {
      headers.add("Lvl");
      widths.add(4);
    }
    if (config.isIncludeHeroDamage()) {
      headers.add("HD");
      widths.add(8);
    }
    if (config.isIncludeTowerDamage()) {
      headers.add("TD");
      widths.add(8);
    }
    if (config.isIncludeHeroHealing()) {
      headers.add("HH");
      widths.add(8);
    }
    if (config.isIncludeObsPlaced()) {
      headers.add("Obs");
      widths.add(4);
    }
    if (config.isIncludeSenPlaced()) {
      headers.add("Sen");
      widths.add(4);
    }

    // Build header row
    StringBuilder headerRow = new StringBuilder();
    for (int i = 0; i < headers.size(); i++) {
      headerRow.append(String.format("%-" + widths.get(i) + "s", headers.get(i)));
      if (i < headers.size() - 1) {
        headerRow.append(" | ");
      }
    }
    sb.append(headerRow).append("\n");

    // Calculate total width for separator
    int totalWidth = widths.stream().mapToInt(Integer::intValue).sum() + (headers.size() - 1) * 3;
    sb.append("-".repeat(totalWidth)).append("\n");

    // Build data rows
    for (PlayerMatchStatisticDomain player : matchWithStats.getPlayerStatistics()) {
      StringBuilder row = new StringBuilder();
      String playerNameWithId = getPlayerNameWithId(player.getPlayerId());

      row.append(String.format("%-30s", playerNameWithId));

      if (config.isIncludeHero()) {
        String heroName =
            player.getHeroPrettyName() != null ? player.getHeroPrettyName() : player.getHeroName();
        row.append(" | ").append(String.format("%-15s", heroName != null ? heroName : "N/A"));
      }
      if (config.isIncludeTeam()) {
        String team = player.getPlayerSlot() < 5 ? "Radiant" : "Dire";
        row.append(" | ").append(String.format("%-8s", team));
      }
      if (config.isIncludeKda()) {
        row.append(" | ")
            .append(
                String.format(
                    "%-6s", player.getKda() != null ? player.getKda().toString() : "N/A"));
      }
      if (config.isIncludeKills()) {
        row.append(" | ")
            .append(String.format("%-4d", player.getKills() != null ? player.getKills() : 0));
      }
      if (config.isIncludeDeaths()) {
        row.append(" | ")
            .append(String.format("%-4d", player.getDeaths() != null ? player.getDeaths() : 0));
      }
      if (config.isIncludeAssists()) {
        row.append(" | ")
            .append(String.format("%-4d", player.getAssists() != null ? player.getAssists() : 0));
      }
      if (config.isIncludeGoldPerMin()) {
        row.append(" | ")
            .append(
                String.format("%-6d", player.getGoldPerMin() != null ? player.getGoldPerMin() : 0));
      }
      if (config.isIncludeXpPerMin()) {
        row.append(" | ")
            .append(String.format("%-6d", player.getXpPerMin() != null ? player.getXpPerMin() : 0));
      }
      if (config.isIncludeLastHits()) {
        row.append(" | ")
            .append(String.format("%-6d", player.getLastHits() != null ? player.getLastHits() : 0));
      }
      if (config.isIncludeDenies()) {
        row.append(" | ")
            .append(String.format("%-8d", player.getDenies() != null ? player.getDenies() : 0));
      }
      if (config.isIncludeWin()) {
        row.append(" | ")
            .append(
                String.format(
                    "%-6s", player.getWin() != null && player.getWin() == 1 ? "Yes" : "No"));
      }
      if (config.isIncludeLane()) {
        row.append(" | ")
            .append(String.format("%-6d", player.getLane() != null ? player.getLane() : 0));
      }
      if (config.isIncludeNetWorth()) {
        row.append(" | ")
            .append(String.format("%-8d", player.getNetWorth() != null ? player.getNetWorth() : 0));
      }
      if (config.isIncludeLevel()) {
        row.append(" | ")
            .append(String.format("%-4d", player.getLevel() != null ? player.getLevel() : 0));
      }
      if (config.isIncludeHeroDamage()) {
        row.append(" | ")
            .append(
                String.format("%-8d", player.getHeroDamage() != null ? player.getHeroDamage() : 0));
      }
      if (config.isIncludeTowerDamage()) {
        row.append(" | ")
            .append(
                String.format(
                    "%-8d", player.getTowerDamage() != null ? player.getTowerDamage() : 0));
      }
      if (config.isIncludeHeroHealing()) {
        row.append(" | ")
            .append(
                String.format(
                    "%-8d", player.getHeroHealing() != null ? player.getHeroHealing() : 0));
      }
      if (config.isIncludeObsPlaced()) {
        row.append(" | ")
            .append(
                String.format("%-4d", player.getObsPlaced() != null ? player.getObsPlaced() : 0));
      }
      if (config.isIncludeSenPlaced()) {
        row.append(" | ")
            .append(
                String.format("%-4d", player.getSenPlaced() != null ? player.getSenPlaced() : 0));
      }

      sb.append(row).append("\n");
    }

    // Add timeline stats if configured
    if (config.isIncludeGoldTimeline()
        || config.isIncludeLastHitsTimeline()
        || config.isIncludeXpTimeline()) {
      sb.append("\nTIMELINE STATISTICS:\n");
      sb.append(buildTimelineStats(matchWithStats, config));
    }

    sb.append("\n");
    return sb.toString();
  }

  private String buildTimelineStats(
      MatchWithPlayerStatistics matchWithStats, PlayerStatisticFieldConfig config) {
    StringBuilder sb = new StringBuilder();

    for (PlayerMatchStatisticDomain player : matchWithStats.getPlayerStatistics()) {
      String playerNameWithId = getPlayerNameWithId(player.getPlayerId());
      sb.append(playerNameWithId).append(":\n");

      if (config.isIncludeGoldTimeline()) {
        sb.append("  Gold: ");
        sb.append("5m=").append(player.getGoldAt5Min() != null ? player.getGoldAt5Min() : "N/A");
        sb.append(", 10m=")
            .append(player.getGoldAt10Min() != null ? player.getGoldAt10Min() : "N/A");
        sb.append(", 15m=")
            .append(player.getGoldAt15Min() != null ? player.getGoldAt15Min() : "N/A");
        sb.append(", 20m=")
            .append(player.getGoldAt20Min() != null ? player.getGoldAt20Min() : "N/A");
        sb.append("\n");
      }
      if (config.isIncludeLastHitsTimeline()) {
        sb.append("  LH: ");
        sb.append("5m=")
            .append(player.getLastHitsAt5Min() != null ? player.getLastHitsAt5Min() : "N/A");
        sb.append(", 10m=")
            .append(player.getLastHitsAt10Min() != null ? player.getLastHitsAt10Min() : "N/A");
        sb.append(", 15m=")
            .append(player.getLastHitsAt15Min() != null ? player.getLastHitsAt15Min() : "N/A");
        sb.append(", 20m=")
            .append(player.getLastHitsAt20Min() != null ? player.getLastHitsAt20Min() : "N/A");
        sb.append("\n");
      }
      if (config.isIncludeXpTimeline()) {
        sb.append("  XP: ");
        sb.append("5m=").append(player.getXpAt5Min() != null ? player.getXpAt5Min() : "N/A");
        sb.append(", 10m=").append(player.getXpAt10Min() != null ? player.getXpAt10Min() : "N/A");
        sb.append(", 15m=").append(player.getXpAt15Min() != null ? player.getXpAt15Min() : "N/A");
        sb.append(", 20m=").append(player.getXpAt20Min() != null ? player.getXpAt20Min() : "N/A");
        sb.append("\n");
      }
    }

    return sb.toString();
  }

  private String buildAbilitiesSection(
      MatchWithPlayerStatistics matchWithStats, AbilityFieldConfig config) {
    StringBuilder sb = new StringBuilder();
    sb.append("PLAYER ABILITIES:\n");
    sb.append(String.format("%-30s | %-15s | %s%n", "Player (ID)", "Hero", "Abilities"));
    sb.append("-".repeat(120)).append("\n");

    Map<Long, List<AbilityDomain>> abilitiesByPlayerSlot =
        matchWithStats.getAbilities().stream()
            .collect(Collectors.groupingBy(AbilityDomain::getPlayerSlot));

    for (PlayerMatchStatisticDomain player : matchWithStats.getPlayerStatistics()) {
      String playerNameWithId = getPlayerNameWithId(player.getPlayerId());
      String heroName =
          player.getHeroPrettyName() != null ? player.getHeroPrettyName() : player.getHeroName();

      List<AbilityDomain> playerAbilities =
          abilitiesByPlayerSlot.getOrDefault(player.getPlayerSlot().longValue(), List.of());

      String abilitiesStr =
          playerAbilities.stream()
              .map(
                  a -> {
                    StringBuilder abilityStr = new StringBuilder();
                    String name = a.getPrettyName() != null ? a.getPrettyName() : a.getName();
                    abilityStr.append(name);

                    if (config.isIncludeDamageDealt()
                        && a.getDamageDealt() != null
                        && a.getDamageDealt() > 0) {
                      abilityStr.append(" [dmg:").append(a.getDamageDealt()).append("]");
                    }
                    if (config.isIncludeUseCount()
                        && a.getUseCount() != null
                        && a.getUseCount() > 0) {
                      abilityStr.append(" [uses:").append(a.getUseCount()).append("]");
                    }

                    return abilityStr.toString();
                  })
              .collect(Collectors.joining(", "));

      if (!abilitiesStr.isEmpty()) {
        sb.append(String.format("%-30s | %-15s | %s%n", playerNameWithId, heroName, abilitiesStr));
      }
    }

    sb.append("\n");
    return sb.toString();
  }

  private String buildItemsSection(
      MatchWithPlayerStatistics matchWithStats,
      ItemFieldConfig itemConfig,
      AiPromptFieldConfig config,
      MatchAnalysisRequest request) {
    StringBuilder sb = new StringBuilder();
    sb.append("PLAYER ITEMS:\n");
    sb.append(
        String.format("%-30s | %-15s | %s%n", "Player (ID)", "Hero", "Items (with purchase time)"));
    sb.append("-".repeat(140)).append("\n");

    // Filter items based on configuration
    List<ItemDomain> filteredItems =
        matchWithStats.getItems().stream()
            .filter(item -> config.isIncludeNeutralItems() || !item.isNeutral())
            .filter(
                item ->
                    !config.isFilterItemsToFocusPlayers()
                        || request.getFocusPlayerIds() == null
                        || request.getFocusPlayerIds().isEmpty()
                        || request.getFocusPlayerIds().contains(item.getPlayerId()))
            .collect(Collectors.toList());

    Map<Long, List<ItemDomain>> itemsByPlayerSlot =
        filteredItems.stream().collect(Collectors.groupingBy(ItemDomain::getPlayerSlot));

    for (PlayerMatchStatisticDomain player : matchWithStats.getPlayerStatistics()) {
      String playerNameWithId = getPlayerNameWithId(player.getPlayerId());
      String heroName =
          player.getHeroPrettyName() != null ? player.getHeroPrettyName() : player.getHeroName();

      List<ItemDomain> playerItems =
          itemsByPlayerSlot.getOrDefault(player.getPlayerSlot().longValue(), List.of());

      String itemsStr =
          playerItems.stream()
              .sorted(
                  (a, b) -> {
                    Long timeA = a.getItemPurchaseTime() != null ? a.getItemPurchaseTime() : 0L;
                    Long timeB = b.getItemPurchaseTime() != null ? b.getItemPurchaseTime() : 0L;
                    return timeA.compareTo(timeB);
                  })
              .map(
                  item -> {
                    StringBuilder itemStr = new StringBuilder();
                    String itemName =
                        item.getItemPrettyName() != null
                            ? item.getItemPrettyName()
                            : item.getItemName();
                    itemStr.append(itemName);

                    if (itemConfig.isIncludePurchaseTime()) {
                      itemStr
                          .append(" (")
                          .append(formatItemPurchaseTime(item.getItemPurchaseTime()))
                          .append(")");
                    }
                    if (itemConfig.isIncludeIsNeutral() && item.isNeutral()) {
                      itemStr.append(" [Neutral]");
                    }
                    if (itemConfig.isIncludeDamageDealt()
                        && item.getDamageDealt() != null
                        && item.getDamageDealt() > 0) {
                      itemStr.append(" [dmg:").append(item.getDamageDealt()).append("]");
                    }
                    if (itemConfig.isIncludeUseCount()
                        && item.getUseCount() != null
                        && item.getUseCount() > 0) {
                      itemStr.append(" [uses:").append(item.getUseCount()).append("]");
                    }

                    return itemStr.toString();
                  })
              .collect(Collectors.joining(", "));

      if (!itemsStr.isEmpty()) {
        sb.append(String.format("%-30s | %-15s | %s%n", playerNameWithId, heroName, itemsStr));
      }
    }

    sb.append("\n");
    return sb.toString();
  }

  private String buildAnalysisRequestSection(AiPromptFieldConfig config) {
    StringBuilder sb = new StringBuilder();
    sb.append("\nPlease provide:\n");
    sb.append("1. Overall match summary\n");
    sb.append("2. Standout performances (both positive and negative)\n");
    sb.append("3. Key statistics highlights\n");

    if (config.isIncludeAbilities()) {
      sb.append("4. Ability draft analysis - synergies and strategy behind ability choices\n");
    }
    if (config.isIncludeItems()) {
      sb.append("5. Item build analysis - timing, effectiveness, and alignment with abilities\n");
    }

    sb.append("6. Recommendations for tracked players\n");
    sb.append("7. Notable patterns or trends across matches (if multiple matches)\n");

    if (config.getCustomAnalysisInstructions() != null
        && !config.getCustomAnalysisInstructions().isBlank()) {
      sb.append("\nAdditional analysis instructions:\n");
      sb.append(config.getCustomAnalysisInstructions()).append("\n");
    }

    return sb.toString();
  }

  private String getPlayerName(Long playerId) {
    if (playerId == null || playerId == -1) {
      return "Anonymous";
    }
    return "Player " + playerId;
  }

  private String getPlayerNameWithId(Long playerId) {
    if (playerId == null || playerId == -1) {
      return "Anonymous";
    }
    return "Player (" + playerId + ")";
  }

  private String formatDuration(Long durationSeconds) {
    if (durationSeconds == null) {
      return "Unknown";
    }
    long minutes = durationSeconds / 60;
    long seconds = durationSeconds % 60;
    return String.format("%d:%02d", minutes, seconds);
  }

  private String formatItemPurchaseTime(Long purchaseTimeSeconds) {
    if (purchaseTimeSeconds == null) {
      return "N/A";
    }
    long minutes = purchaseTimeSeconds / 60;
    long seconds = Math.abs(purchaseTimeSeconds % 60);
    if (purchaseTimeSeconds < 0) {
      return String.format("-%d:%02d", Math.abs(minutes), seconds);
    }
    return String.format("%d:%02d", minutes, seconds);
  }
}
