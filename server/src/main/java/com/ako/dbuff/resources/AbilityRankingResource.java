package com.ako.dbuff.resources;

import com.ako.dbuff.resources.model.AbilityRankingResponse;
import com.ako.dbuff.service.ranking.AbilityRankingService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for ability ranking statistics per player. Provides endpoints to retrieve ability usage
 * statistics including pick rate and win rate.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/player")
public class AbilityRankingResource {

  private final AbilityRankingService abilityRankingService;

  /**
   * Gets ability rankings for a specific player.
   *
   * <p>Returns a list of abilities ordered by pick count, with statistics including:
   *
   * <ul>
   *   <li>Pick count - number of matches where the ability was picked
   *   <li>Pick rate - percentage of matches where the ability was picked
   *   <li>Win rate - percentage of wins when the ability was picked
   * </ul>
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive). If null, includes all history.
   * @param endDate Optional end date filter (inclusive). If null, uses current date.
   * @param abilities Optional set of ability names to include. If null, returns top abilities by
   *     pick count.
   * @param excludedAbilities Optional set of ability names to exclude from results.
   * @param limit Maximum number of abilities to return. Defaults to 10 if null.
   * @return List of AbilityRankingResponse ordered by pick count descending
   */
  @GetMapping("/{playerId}/abilityRanking")
  public List<AbilityRankingResponse> getAbilityRanking(
      @PathVariable Long playerId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate,
      @RequestParam(required = false) Set<String> abilities,
      @RequestParam(required = false) Set<String> excludedAbilities,
      @RequestParam(required = false) Integer limit) {

    log.info(
        "GET /api/v1/player/{}/abilityRanking - startDate={}, endDate={}, abilities={}, excludedAbilities={}, limit={}",
        playerId,
        startDate,
        endDate,
        abilities,
        excludedAbilities,
        limit);

    return abilityRankingService.getAbilityRankings(
        playerId, startDate, endDate, abilities, excludedAbilities, limit);
  }
}
