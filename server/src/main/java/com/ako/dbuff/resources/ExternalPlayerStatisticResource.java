package com.ako.dbuff.resources;

import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse;
import com.ako.dbuff.service.ranking.ExternalPlayerStatisticService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for external player statistics. Computes the history of games where a focus group of
 * players played WITH or AGAINST an external player identified by name.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ExternalPlayerStatisticResource {

  private final ExternalPlayerStatisticService externalPlayerStatisticService;

  /**
   * Gets the history of games where the focus group played with or against an external player.
   *
   * <p>The focus group is resolved from the instance configuration when {@code instance_id} is
   * provided; otherwise it falls back to {@code focus_player_ids}.
   *
   * @param instanceId The instance configuration ID. Focus group is taken from this instance when
   *     present.
   * @param focusPlayerIds Focus group player IDs. Used only when {@code instance_id} is null.
   * @param playerName The external player's name to search for (required).
   * @return the external player statistics; an empty response if the player is not found.
   */
  @GetMapping("/externalPlayerStatistic")
  public ResponseEntity<ExternalPlayerStatisticResponse> getExternalPlayerStatistic(
      @RequestParam(value = "instance_id", required = false) String instanceId,
      @RequestParam(value = "focus_player_ids", required = false) Set<Long> focusPlayerIds,
      @RequestParam("player_name") String playerName) {

    log.info(
        "GET /api/v1/externalPlayerStatistic - instanceId={}, focusPlayerIds={}, playerName={}",
        instanceId,
        focusPlayerIds,
        playerName);

    ExternalPlayerStatisticResponse response;
    if (instanceId != null) {
      response = externalPlayerStatisticService.getStatisticsForInstance(instanceId, playerName);
    } else {
      response =
          externalPlayerStatisticService.getStatistics(
              focusPlayerIds != null ? focusPlayerIds : List.of(), playerName);
    }

    return ResponseEntity.ok(response);
  }
}
