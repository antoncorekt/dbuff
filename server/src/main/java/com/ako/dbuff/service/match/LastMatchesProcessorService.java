package com.ako.dbuff.service.match;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class LastMatchesProcessorService {

  private final DotaApiLastMatchesService dotaApiLastMatchesService;
  private final MatchProcessorService matchProcessorService;
  private final MatchRepo matchRepo;
  private final MatchReportOrchestrator matchReportOrchestrator;
  private final DbufInstanceConfigService instanceConfigService;

  /**
   * Processes the last matches for a specific instance configuration.
   *
   * @param instanceId the instance configuration ID
   * @return the set of match IDs that were fetched
   */
  public Set<Long> processLastMatchesForInstance(String instanceId) {
    return processLastMatchesForInstance(instanceId, true, false);
  }

  /**
   * Processes the last matches for a specific instance configuration.
   *
   * @param instanceId the instance configuration ID
   * @param enableAiAnalysis if true, AI analysis will be performed after processing
   * @return the set of match IDs that were fetched
   */
  public Set<Long> processLastMatchesForInstance(String instanceId, boolean enableAiAnalysis) {
    return processLastMatchesForInstance(instanceId, enableAiAnalysis, false);
  }

  /**
   * Processes the last matches for a specific instance configuration.
   *
   * @param instanceId the instance configuration ID
   * @param enableAiAnalysis if true, AI analysis will be performed after processing
   * @param analyzePerMatch if true, each match is analyzed individually by AI
   * @return the set of match IDs that were fetched
   * @throws IllegalArgumentException if instance not found
   */
  public Set<Long> processLastMatchesForInstance(
      String instanceId, boolean enableAiAnalysis, boolean analyzePerMatch) {

    DbufInstanceConfigDomain config =
        instanceConfigService
            .getDomainById(instanceId)
            .orElseThrow(() -> new IllegalArgumentException("Instance not found: " + instanceId));

    if (!config.getActive()) {
      log.warn("Instance {} is not active, skipping processing", instanceId);
      return Set.of();
    }

    Set<Long> playerIds = config.getPlayerIds();
    Set<Long> gameModeIds = config.getGameModeIds();

    log.info(
        "Processing last matches for instance {}: players={}, gameModeIds={}",
        instanceId,
        playerIds,
        gameModeIds);

    // Run processing within instance context
    final Set<Long>[] result = new Set[] {Set.of()};
    ProcessContext.runWithInstanceId(
        instanceId,
        () -> {
          result[0] = processMatchesForPlayers(config, enableAiAnalysis, analyzePerMatch);
        });
    return result[0];
  }

  /**
   * Processes matches for all active instances. Each instance is processed independently with its
   * own context.
   */
  public void processAllActiveInstances() {
    processAllActiveInstances(true, false);
  }

  /**
   * Processes matches for all active instances.
   *
   * @param enableAiAnalysis if true, AI analysis will be performed after processing
   * @param analyzePerMatch if true, each match is analyzed individually by AI
   */
  public void processAllActiveInstances(boolean enableAiAnalysis, boolean analyzePerMatch) {
    List<DbufInstanceConfigDomain> activeInstances =
        instanceConfigService.getAllActive().stream()
            .map(response -> instanceConfigService.getDomainById(response.getId()))
            .filter(opt -> opt.isPresent())
            .map(opt -> opt.get())
            .toList();

    log.info("Processing matches for {} active instances", activeInstances.size());

    for (DbufInstanceConfigDomain config : activeInstances) {
      try {
        processLastMatchesForInstance(config.getId(), enableAiAnalysis, analyzePerMatch);
      } catch (Exception e) {
        log.error("Error processing instance {}: {}", config.getId(), e.getMessage(), e);
      }
    }
  }

  private Set<Long> processMatchesForPlayers(
      DbufInstanceConfigDomain config, boolean enableAiAnalysis, boolean analyzePerMatch) {

    Set<Long> playerIds = config.getPlayerIds();
    Set<Long> gameModeIds = config.getGameModeIds();

    Set<Long> matchesToFetch =
        playerIds.stream()
            .map(dotaApiLastMatchesService::fetchLastMatches)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

    log.info("Matches to fetch: {}", matchesToFetch);

    List<MatchDomain> matchDomains =
        matchesToFetch.stream()
            .map(
                id ->
                    matchRepo
                        .findById(id)
                        .orElseGet(() -> matchRepo.save(MatchDomain.builder().id(id).build())))
            .toList();

    // Filter by game mode IDs if specified (skip filtering if "all" game modes is set)
    List<MatchDomain> filteredMatches = matchDomains;
    if (gameModeIds != null
        && !gameModeIds.isEmpty()
        && !DbufInstanceConfigService.isAllGameModes(gameModeIds)) {
      filteredMatches =
          matchDomains.stream()
              .filter(m -> m.getGameModeId() == null || gameModeIds.contains(m.getGameModeId()))
              .toList();
      log.info(
          "Filtered to {} matches based on game mode IDs: {}", filteredMatches.size(), gameModeIds);
    } else if (DbufInstanceConfigService.isAllGameModes(gameModeIds)) {
      log.info("Processing all game modes (no filtering)");
    }

    final List<MatchDomain> matchesToProcess = filteredMatches;
    final Set<Long> contextPlayerIds = playerIds;

    matchProcessorService
        .process(matchesToProcess)
        .thenAccept(
            listMatches -> {
              if (config.getDiscordChannelId() == null) {
                log.info("Discord channel id null, skip match reporting");
                return;
              }
              if (listMatches.isEmpty()) {
                log.info("No new matches to report");
                return;
              }
              try {
                matchReportOrchestrator.processAndReport(listMatches, config);
              } catch (Exception e) {
                log.error("Failed to generate match reports: {}", e.getMessage(), e);
              }
            });

    return matchesToFetch;
  }

  // ==================== Legacy methods for backward compatibility ====================

  /**
   * @deprecated Use {@link #processLastMatchesForInstance(String)} instead
   */
  @Deprecated
  public Set<Long> processLastMatches() {
    return processLastMatches(true, false);
  }

  /**
   * @deprecated Use {@link #processLastMatchesForInstance(String, boolean)} instead
   */
  @Deprecated
  public Set<Long> processLastMatches(boolean enableAiAnalysis) {
    return processLastMatches(enableAiAnalysis, false);
  }

  /**
   * @deprecated Use {@link #processLastMatchesForInstance(String, boolean, boolean)} instead
   */
  @Deprecated
  public Set<Long> processLastMatches(boolean enableAiAnalysis, boolean analyzePerMatch) {
    // Check if we're in an instance context
    String instanceId = ProcessContext.getCurrentInstanceId();
    if (instanceId != null) {
      return processLastMatchesForInstance(instanceId, enableAiAnalysis, analyzePerMatch);
    }

    // Fallback: process all active instances
    log.warn("processLastMatches called without instance context, processing all active instances");
    processAllActiveInstances(enableAiAnalysis, analyzePerMatch);
    return Set.of();
  }

  /**
   * @deprecated Use {@link #processLastMatchesForInstance(String)} with session analysis
   */
  @Deprecated
  public Set<Long> processLastMatchesWithSessionAnalysis() {
    return processLastMatches(true, false);
  }

  /**
   * @deprecated Use {@link #processLastMatchesForInstance(String, boolean, boolean)} instead
   */
  @Deprecated
  public Set<Long> processLastMatchesWithIndividualAnalysis() {
    return processLastMatches(true, true);
  }

  /**
   * @deprecated Use {@link #processLastMatchesForInstance(String, boolean)} instead
   */
  @Deprecated
  public Set<Long> processLastMatchesWithoutAnalysis() {
    return processLastMatches(false, false);
  }
}
