package com.ako.dbuff.resources;

import com.ako.dbuff.config.PlayerConfiguration;
import com.ako.dbuff.service.list.AllHistoryMatchFetcherService;
import com.ako.dbuff.service.list.AllHistoryService;
import com.ako.dbuff.utils.Utils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

  @PostMapping("/processAll")
  public void runAll(
      @RequestParam(value = "players", required = false) List<Long> players,
      @RequestParam(value = "startPage", required = false, defaultValue = "0") Integer startPage,
      @RequestParam(value = "endPage", required = false, defaultValue = "-1") Integer endPage) {

    CompletableFuture.runAsync(
            () -> {
              Utils.nvl(players, PlayerConfiguration.DEFAULT_PLAYERS.keySet())
                  .forEach(
                      (id) -> {
                        log.info("Loading player {} history", id);
                        allHistoryMatchFetcherService.processHistoryMatches(
                            id,
                            ABILITY_DRAFT_ID,
                            AllHistoryService.ScrapperParams.builder()
                                .startPage(startPage)
                                .endPage(endPage)
                                .build());
                      });
            })
        .exceptionally(
            e -> {
              log.error("Error loading players history", e);
              return null;
            });
  }
}
