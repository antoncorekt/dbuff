package com.ako.dbuff.resources;

import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse;
import com.ako.dbuff.service.ranking.ScoreboardStatisticService;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API that combines scoreboard name detection with external player statistics: it extracts the
 * player names from an uploaded scoreboard image, excludes the focus group's own players, and
 * returns the statistics for each remaining opponent against the focus group.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ScoreboardStatisticResource {

  private final ScoreboardStatisticService scoreboardStatisticService;

  /**
   * Detects the players on a scoreboard image and returns statistics for each opponent (excluding
   * the focus group itself).
   *
   * <p>The focus group is resolved from the instance configuration when {@code instance_id} is
   * provided; otherwise it falls back to {@code focus_player_ids}.
   *
   * @param instanceId the instance configuration ID; focus group is taken from it when present
   * @param focusPlayerIds focus group player IDs; used only when {@code instance_id} is null
   * @param image the uploaded scoreboard image (PNG, JPEG, etc.)
   * @return statistics for each detected opponent
   * @throws IOException if the uploaded file cannot be read
   */
  @PostMapping(value = "/scoreboardStatistics", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<List<ExternalPlayerStatisticResponse>> getScoreboardStatistics(
      @RequestParam(value = "instance_id", required = false) String instanceId,
      @RequestParam(value = "focus_player_ids", required = false) Set<Long> focusPlayerIds,
      @RequestParam("image") MultipartFile image)
      throws IOException {

    log.info(
        "POST /api/v1/scoreboardStatistics - instanceId={}, focusPlayerIds={}, filename={},"
            + " size={} bytes",
        instanceId,
        focusPlayerIds,
        image.getOriginalFilename(),
        image.getSize());

    List<ExternalPlayerStatisticResponse> response;
    if (instanceId != null) {
      response = scoreboardStatisticService.getStatisticsForInstance(instanceId, image.getBytes());
    } else {
      response =
          scoreboardStatisticService.getStatistics(
              focusPlayerIds != null ? focusPlayerIds : List.of(), image.getBytes());
    }

    return ResponseEntity.ok(response);
  }
}
