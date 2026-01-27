package com.ako.dbuff.resources;

import com.ako.dbuff.resources.model.FindPlayerMatchesResponse;
import com.ako.dbuff.service.ranking.FindPlayerMatchesService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for finding player matches. Provides endpoints to search for a player by name and
 * retrieve their matches with statistics.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class FindPlayerResource {

  private final FindPlayerMatchesService findPlayerMatchesService;

  /**
   * Finds matches for a player by their name.
   *
   * <p>Returns a list of matches where the player participated, ordered by match date descending.
   * Each match includes statistics for the searched player and default players (from
   * PlayerConfiguration.DEFAULT_PLAYERS).
   *
   * <p>If the player is not found, returns an empty list.
   *
   * @param playerName The player's name to search for
   * @param limit Maximum number of matches to return. Defaults to 20 if not specified.
   * @return List of FindPlayerMatchesResponse ordered by match date descending
   */
  @GetMapping("/findPlayer/{playerName}")
  public List<FindPlayerMatchesResponse> findPlayerMatches(
      @PathVariable String playerName, @RequestParam(required = false) Integer limit) {

    log.info("GET /api/v1/findPlayer/{} - limit={}", playerName, limit);

    return findPlayerMatchesService.findPlayerMatches(playerName, limit);
  }
}
