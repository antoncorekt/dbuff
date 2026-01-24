package com.ako.dbuff.resources;

import com.ako.dbuff.config.PlayerConfiguration;
import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.executors.Executors;
import com.ako.dbuff.service.list.AllHistoryMatchFetcherService;
import com.ako.dbuff.utils.Utils;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  @PostMapping("/processAll")
  public void runAll(
      @RequestParam(value = "players", required = false) List<Long> players,
      @RequestParam(value = "startPage", required = false, defaultValue = "0") Integer startPage,
      @RequestParam(value = "endPage", required = false, defaultValue = "-1") Integer endPage) {

    // Use virtual thread executor with scoped values for each player
    Utils.nvl(players, PlayerConfiguration.DEFAULT_PLAYERS.keySet())
        .forEach(playerId -> processPlayerHistory(playerId, startPage, endPage));
  }

  private void processPlayerHistory(Long playerId, Integer startPage, Integer endPage) {
    // Execute in virtual thread with playerId in scope for logging
    Executors.executeWithFullContext(
        Executors.ProcessTypes.HISTORY_FETCH,
        null, // matchId will be set when processing individual matches
        playerId,
        () -> {
          try {
            log.info("{} Loading player {} history", ProcessContext.getContextString(), playerId);
            allHistoryMatchFetcherService.processHistoryMatches(
                playerId,
                ABILITY_DRAFT_ID,
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
  }
}
