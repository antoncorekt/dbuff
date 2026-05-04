package com.ako.dbuff.resources;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.MatchAnalysisDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.KillLogRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.resources.model.AnalyzeMatchesRequest;
import com.ako.dbuff.resources.model.MatchDetailResponse;
import com.ako.dbuff.resources.model.MatchReportResponse;
import com.ako.dbuff.service.ai.MatchStatisticsSummarizerService;
import com.ako.dbuff.service.ai.config.AiPromptFieldConfig;
import com.ako.dbuff.service.details.MatchParserHandler;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.match.LastMatchesProcessorService;
import com.ako.dbuff.service.match.MatchCompletenessService;
import com.ako.dbuff.service.match.MatchDeletionService;
import com.ako.dbuff.service.match.MatchProcessorService;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator;
import com.ako.dbuff.service.match.report.analyzer.PerPlayerReport;
import com.ako.dbuff.service.match.report.analyzer.Report;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
  private final ItemRepository itemRepository;
  private final AbilityRepo abilityRepo;
  private final KillLogRepo killLogRepo;
  private final DbufInstanceConfigService instanceConfigService;
  private final MatchCompletenessService matchCompletenessService;
  private final MatchProcessorService matchProcessorService;
  private final MatchReportOrchestrator matchReportOrchestrator;

  @GetMapping("/{id}")
  public ResponseEntity<MatchDetailResponse> getMatch(@PathVariable Long id) {
    return matchRepo
        .findById(id)
        .map(match -> ResponseEntity.ok(buildMatchDetailResponse(match)))
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/{id}/parse")
  public MatchDetailResponse parseMatch(@PathVariable String id) {
    MatchDomain match = matchParserHandler.handle(Long.parseLong(id));
    return buildMatchDetailResponse(match);
  }

  /**
   * Processes the last matches for a specific instance configuration.
   *
   * @param instanceId the instance configuration ID (required)
   * @param enableAi if true (default), AI analysis will be performed after processing; if false,
   *     only match processing without AI analysis
   * @return the set of match IDs that were fetched
   */
  @PostMapping("/processLast")
  public ResponseEntity<Set<Long>> processLastMatches(
      @RequestParam(name = "instanceId") String instanceId,
      @RequestParam(name = "enableAi", defaultValue = "true") boolean enableAi) {

    log.info("Processing last matches for instance {} with AI analysis: {}", instanceId, enableAi);

    // Validate instance exists
    if (instanceConfigService.getById(instanceId).isEmpty()) {
      log.warn("Instance not found: {}", instanceId);
      return ResponseEntity.notFound().build();
    }

    Set<Long> matchIds =
        lastMatchesProcessorService.processLastMatchesForInstance(instanceId, enableAi);
    return ResponseEntity.ok(matchIds);
  }

  /**
   * Processes the last matches for all active instances.
   *
   * @param enableAi if true (default), AI analysis will be performed after processing
   */
  @PostMapping("/processAll")
  public ResponseEntity<Void> processAllInstances(
      @RequestParam(name = "enableAi", defaultValue = "true") boolean enableAi) {

    log.info("Processing last matches for all active instances with AI analysis: {}", enableAi);
    lastMatchesProcessorService.processAllActiveInstances(enableAi, false);
    return ResponseEntity.accepted().build();
  }

  /**
   * Analyzes specified matches using AI for a specific instance. Only matches that have been
   * processed (have player statistics) will be analyzed. Unprocessed matches will be skipped.
   *
   * <p>Supports optional field configuration to customize which data is included in the AI prompt.
   * Available presets: "default", "minimal", "full".
   *
   * @param instanceId the instance configuration ID (required)
   * @param matches comma-separated list of match IDs to analyze
   * @param request optional request body containing custom AI prompt and field configuration
   * @return CompletableFuture containing the list of analysis results
   */
  @PostMapping("/analyze")
  public CompletableFuture<ResponseEntity<List<MatchAnalysisDomain>>> analyzeMatches(
      @RequestParam("instanceId") String instanceId,
      @RequestParam("matches") String matches,
      @RequestBody(required = false) AnalyzeMatchesRequest request) {

    // Validate instance exists
    var instanceOpt = instanceConfigService.getDomainById(instanceId);
    if (instanceOpt.isEmpty()) {
      log.warn("Instance not found: {}", instanceId);
      return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
    }

    List<Long> matchIds = parseMatchIds(matches);
    log.info(
        "Received request to analyze {} matches for instance {}: {}",
        matchIds.size(),
        instanceId,
        matchIds);

    if (matchIds.isEmpty()) {
      log.warn("No match IDs provided for analysis");
      return CompletableFuture.completedFuture(ResponseEntity.ok(List.of()));
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
      log.info(
          "Skipped {} unprocessed/missing matches: {}", skippedMatchIds.size(), skippedMatchIds);
    }

    if (processedMatches.isEmpty()) {
      log.warn("No processed matches found for analysis");
      return CompletableFuture.completedFuture(ResponseEntity.ok(List.of()));
    }

    log.info("Analyzing {} processed matches for instance {}", processedMatches.size(), instanceId);

    // Extract configuration from request
    String customPrompt = request != null ? request.getCustomPrompt() : null;
    AiPromptFieldConfig fieldConfig = resolveFieldConfig(request);
    Set<Long> focusPlayerIds = instanceOpt.get().getPlayerIds();

    // Run analysis within instance context using runWithInstanceId (no checked exception)
    CompletableFuture<ResponseEntity<List<MatchAnalysisDomain>>> result = new CompletableFuture<>();
    ProcessContext.runWithInstanceId(
        instanceId,
        () -> {
          matchStatisticsSummarizerService
              .summarizeAndAnalyze(
                  processedMatches, false, customPrompt, fieldConfig, focusPlayerIds)
              .thenApply(ResponseEntity::ok)
              .whenComplete(
                  (response, ex) -> {
                    if (ex != null) {
                      result.completeExceptionally(ex);
                    } else {
                      result.complete(response);
                    }
                  });
        });
    return result;
  }

  /**
   * Resolves the field configuration from the request. Priority: fieldConfig > preset > default
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
        .map(
            s -> {
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

  @GetMapping("/findIncompleted")
  public ResponseEntity<List<Map.Entry<Long, Map<String, Object>>>> findIncompletedMatches() {
    List<Map.Entry<Long, Map<String, Object>>> incompletedMatches =
        matchCompletenessService.findIncompletedMatches();

    log.info("total incompletedMatches: {}", incompletedMatches.size());
    return ResponseEntity.ok(incompletedMatches);
  }

  @PostMapping("/findIncompletedAndFix")
  public ResponseEntity<List<Map.Entry<Long, Map<String, Object>>>> findIncompletedMatchesAndFix() {
    List<Map.Entry<Long, Map<String, Object>>> incompletedMatches =
        matchCompletenessService.findIncompletedMatches();

    incompletedMatches.forEach(
        x -> matchProcessorService.processMatch(MatchDomain.builder().id(x.getKey()).build()));

    log.info("total incompletedMatches: {}", incompletedMatches.size());
    return ResponseEntity.ok(incompletedMatches);
  }

  @GetMapping("/{id}/report")
  public ResponseEntity<MatchReportResponse> getMatchReport(
      @PathVariable Long id,
      @RequestParam("instanceId") String instanceId,
      @RequestParam(name = "playerIds", required = false) Set<Long> filterPlayerIds) {

    var instanceOpt = instanceConfigService.getDomainById(instanceId);
    if (instanceOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    var matchOpt = matchRepo.findById(id);
    if (matchOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    List<Report> reports = matchReportOrchestrator.analyzeMatch(matchOpt.get(), instanceOpt.get());

    if (filterPlayerIds != null && !filterPlayerIds.isEmpty()) {
      reports =
          reports.stream()
              .filter(
                  r ->
                      !(r instanceof PerPlayerReport perPlayer)
                          || filterPlayerIds.contains(perPlayer.getPlayerId()))
              .toList();
    }

    MatchReportResponse response =
        MatchReportResponse.builder().matchId(id).reports(reports).build();

    return ResponseEntity.ok(response);
  }

  private MatchDetailResponse buildMatchDetailResponse(MatchDomain match) {
    Long matchId = match.getId();
    return MatchDetailResponse.builder()
        .match(match)
        .players(playerGameStatisticRepo.findAllByMatchId(matchId))
        .items(itemRepository.findAllByMatchId(matchId))
        .abilities(abilityRepo.findAllByMatchId(matchId))
        .killLogs(killLogRepo.findAllByMatchId(matchId))
        .analysis(match.getAnalysis())
        .build();
  }
}
