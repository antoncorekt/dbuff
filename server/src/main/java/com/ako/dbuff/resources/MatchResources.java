package com.ako.dbuff.resources;

import com.ako.dbuff.dao.model.MatchAnalysisDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.resources.model.AnalyzeMatchesRequest;
import com.ako.dbuff.service.ai.MatchStatisticsSummarizerService;
import com.ako.dbuff.service.ai.config.AiPromptFieldConfig;
import com.ako.dbuff.service.details.MatchParserHandler;
import com.ako.dbuff.service.match.LastMatchesProcessorService;
import com.ako.dbuff.service.match.MatchDeletionService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/matches")
public class MatchResources {

  private final MatchParserHandler matchParserHandler;
  private final LastMatchesProcessorService lastMatchesProcessorService;
  private final MatchDeletionService matchDeletionService;
  private final MatchStatisticsSummarizerService matchStatisticsSummarizerService;
  private final MatchRepo matchRepo;
  private final PlayerGameStatisticRepo playerGameStatisticRepo;

  @PostMapping("/{id}/parse")
  public MatchDomain parseMatch(@PathVariable String id) {
    return matchParserHandler.handle(Long.parseLong(id));
  }

  /**
   * Processes the last matches for all configured players.
   *
   * @param enableAi if true (default), AI analysis will be performed after processing;
   *                 if false, only match processing without AI analysis
   * @return the set of match IDs that were fetched
   */
  @PostMapping("/processLast")
  public Set<Long> processLastMatches(
      @RequestParam(name = "enableAi", defaultValue = "true") boolean enableAi) {
    log.info("Processing last matches with AI analysis: {}", enableAi);
    return lastMatchesProcessorService.processLastMatches(enableAi);
  }

  /**
   * Analyzes specified matches using AI. Only matches that have been processed (have player
   * statistics) will be analyzed. Unprocessed matches will be skipped.
   *
   * <p>Supports optional field configuration to customize which data is included in the AI prompt.
   * Available presets: "default", "minimal", "full".
   *
   * @param matches comma-separated list of match IDs to analyze
   * @param request optional request body containing custom AI prompt and field configuration
   * @return CompletableFuture containing the list of analysis results
   */
  @PostMapping("/analyze")
  public CompletableFuture<List<MatchAnalysisDomain>> analyzeMatches(
      @RequestParam("matches") String matches,
      @RequestBody(required = false) AnalyzeMatchesRequest request) {

    List<Long> matchIds = parseMatchIds(matches);
    log.info("Received request to analyze {} matches: {}", matchIds.size(), matchIds);

    if (matchIds.isEmpty()) {
      log.warn("No match IDs provided for analysis");
      return CompletableFuture.completedFuture(List.of());
    }

    // Filter to only include matches that have been processed (have player statistics)
    List<MatchDomain> processedMatches = new ArrayList<>();
    List<Long> skippedMatchIds = new ArrayList<>();

    for (Long matchId : matchIds) {
      var matchOpt = matchRepo.findById(matchId);
      if (matchOpt.isPresent()) {
        // Check if match has player statistics (i.e., has been processed)
        boolean hasStats = !playerGameStatisticRepo.findAllByMatchId(matchId).isEmpty();
        if (hasStats) {
          processedMatches.add(matchOpt.get());
        } else {
          log.warn("Match {} has no player statistics, skipping AI analysis", matchId);
          skippedMatchIds.add(matchId);
        }
      } else {
        log.warn("Match {} not found in database, skipping", matchId);
        skippedMatchIds.add(matchId);
      }
    }

    if (!skippedMatchIds.isEmpty()) {
      log.info("Skipped {} unprocessed/missing matches: {}", skippedMatchIds.size(), skippedMatchIds);
    }

    if (processedMatches.isEmpty()) {
      log.warn("No processed matches found for analysis");
      return CompletableFuture.completedFuture(List.of());
    }

    log.info("Analyzing {} processed matches", processedMatches.size());

    // Extract configuration from request
    String customPrompt = request != null ? request.getCustomPrompt() : null;
    AiPromptFieldConfig fieldConfig = resolveFieldConfig(request);

    return matchStatisticsSummarizerService.summarizeAndAnalyze(
        processedMatches, false, customPrompt, fieldConfig);
  }

  /**
   * Resolves the field configuration from the request.
   * Priority: fieldConfig > preset > default
   *
   * @param request the analyze request
   * @return the resolved field configuration
   */
  private AiPromptFieldConfig resolveFieldConfig(AnalyzeMatchesRequest request) {
    if (request == null) {
      return null; // Use default in service
    }

    // If explicit fieldConfig is provided, use it
    if (request.getFieldConfig() != null) {
      return request.getFieldConfig();
    }

    // If preset is specified, use the preset configuration
    if (request.getPreset() != null && !request.getPreset().isBlank()) {
      return switch (request.getPreset().toLowerCase()) {
        case "minimal" -> AiPromptFieldConfig.minimalConfig();
        case "full" -> {
          AiPromptFieldConfig config = AiPromptFieldConfig.defaultConfig();
          config.setPlayerStatisticFields(
              AiPromptFieldConfig.PlayerStatisticFieldConfig.fullConfig());
          config.setItemFields(AiPromptFieldConfig.ItemFieldConfig.fullConfig());
          config.setAbilityFields(AiPromptFieldConfig.AbilityFieldConfig.fullConfig());
          yield config;
        }
        case "default" -> AiPromptFieldConfig.defaultConfig();
        default -> {
          log.warn("Unknown preset '{}', using default configuration", request.getPreset());
          yield AiPromptFieldConfig.defaultConfig();
        }
      };
    }

    return null; // Use default in service
  }

  /**
   * Parses comma-separated match IDs string into a list of Long values.
   *
   * @param matches comma-separated match IDs
   * @return list of parsed match IDs
   */
  private List<Long> parseMatchIds(String matches) {
    if (matches == null || matches.isBlank()) {
      return List.of();
    }

    return Arrays.stream(matches.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> {
          try {
            return Long.parseLong(s);
          } catch (NumberFormatException e) {
            log.warn("Invalid match ID: {}", s);
            return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Deletes a match and all related data including player statistics, items, abilities, and
   * analysis (if not shared with other matches).
   *
   * @param id the match ID to delete
   * @return 204 No Content on success
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteMatch(@PathVariable Long id) {
    log.info("Received request to delete match {}", id);
    matchDeletionService.deleteMatch(id);
    return ResponseEntity.noContent().build();
  }
}
