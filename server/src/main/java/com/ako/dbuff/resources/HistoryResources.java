package com.ako.dbuff.resources;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.executors.Executors;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.list.AllHistoryMatchFetcherService;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/history")
public class HistoryResources {

  private static final int ABILITY_DRAFT_ID = 18;
  private final AllHistoryMatchFetcherService allHistoryMatchFetcherService;
  private final com.ako.dbuff.service.list.AllHistoryService allHistoryService;
  private final DbufInstanceConfigService instanceConfigService;

  /**
   * Processes history for all players in a specific instance configuration.
   *
   * @param instanceId the instance configuration ID (required)
   * @param startPage the starting page for history fetch (default: 0)
   * @param endPage the ending page for history fetch (default: -1 for all)
   * @return 202 Accepted if processing started
   */
  @PostMapping("/processAll")
  public ResponseEntity<Void> runAll(
      @RequestParam(value = "instanceId") String instanceId,
      @RequestParam(value = "startPage", required = false, defaultValue = "0") Integer startPage,
      @RequestParam(value = "endPage", required = false, defaultValue = "-1") Integer endPage) {

    // Validate instance exists
    var instanceOpt = instanceConfigService.getDomainById(instanceId);
    if (instanceOpt.isEmpty()) {
      log.warn("Instance not found: {}", instanceId);
      return ResponseEntity.notFound().build();
    }

    DbufInstanceConfigDomain config = instanceOpt.get();
    if (!config.getActive()) {
      log.warn("Instance {} is not active", instanceId);
      return ResponseEntity.badRequest().build();
    }

    Set<Long> playerIds = config.getPlayerIds();
    log.info("Processing history for instance {} with {} players", instanceId, playerIds.size());

    // Process within instance context
    ProcessContext.runWithInstanceId(
        instanceId,
        () ->
            playerIds.forEach(
                playerId ->
                    processPlayerHistory(
                        instanceId, playerId, startPage, endPage, config.getGameModeIds())));

    return ResponseEntity.accepted().build();
  }

  private void processPlayerHistory(
      String instanceId, Long playerId, Integer startPage, Integer endPage, Set<Long> gameMode) {
    // Execute in virtual thread with full context for logging
    Executors.PROCESSOR_VIRT_EXECUTOR_SERVICE.execute(
        () -> {
          ProcessContext.runWithFullInstanceContext(
              instanceId,
              Executors.ProcessTypes.HISTORY_FETCH,
              null, // matchId will be set when processing individual matches
              playerId,
              () -> {
                try {
                  log.info(
                      "{} Loading player {} history", ProcessContext.getContextString(), playerId);
                  allHistoryMatchFetcherService.processHistoryMatches(
                      playerId,
                      gameMode,
                      com.ako.dbuff.service.list.AllHistoryService.ScrapperParams.builder()
                          .startPage(startPage)
                          .endPage(endPage)
                          .build());
                  log.info(
                      "{} Finished loading player {} history",
                      ProcessContext.getContextString(),
                      playerId);
                } catch (Exception e) {
                  log.error(
                      "{} Error loading player {} history",
                      ProcessContext.getContextString(),
                      playerId,
                      e);
                }
              });
        });
  }
}
