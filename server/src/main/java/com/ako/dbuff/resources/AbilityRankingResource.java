package com.ako.dbuff.resources;

import com.ako.dbuff.resources.model.AbilityRankingResponse;
import com.ako.dbuff.service.ranking.AbilityRankingService;
import com.ako.dbuff.service.ranking.RankingQuery;
import com.ako.dbuff.service.ranking.RankingSort;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
   * @param heroes Optional set of hero names to restrict the statistics to.
   * @param gameModes Optional set of game mode names to restrict the statistics to. If null,
   *     includes every mode.
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
      @RequestParam(required = false) Set<String> heroes,
      @RequestParam(required = false) Set<String> gameModes,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false, defaultValue = "0") int minGames) {

    log.info(
        "GET /api/v1/player/{}/abilityRanking - startDate={}, endDate={}, abilities={}, excludedAbilities={}, heroes={}, gameModes={}, limit={}, sort={}, minGames={}",
        playerId,
        startDate,
        endDate,
        abilities,
        excludedAbilities,
        heroes,
        gameModes,
        limit,
        sort,
        minGames);

    RankingSort rankingSort;
    try {
      rankingSort = RankingSort.parse(sort);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "sort must be PICKS, WIN_RATE or WILSON");
    }
    return abilityRankingService.getAbilityRankings(
        new RankingQuery(
            Set.of(playerId),
            startDate,
            endDate,
            abilities,
            excludedAbilities,
            heroes,
            gameModes,
            rankingSort,
            minGames,
            limit));
  }
}
