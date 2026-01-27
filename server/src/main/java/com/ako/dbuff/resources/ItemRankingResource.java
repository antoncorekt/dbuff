package com.ako.dbuff.resources;

import com.ako.dbuff.resources.model.ItemRankingResponse;
import com.ako.dbuff.service.ranking.ItemRankingService;
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
 * REST API for item ranking statistics per player. Provides endpoints to retrieve item usage
 * statistics including pick rate, win rate, and average purchase time.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/player")
public class ItemRankingResource {

  private final ItemRankingService itemRankingService;

  /**
   * Gets item rankings for a specific player.
   *
   * <p>Returns a list of items ordered by pick count, with statistics including:
   *
   * <ul>
   *   <li>Pick count - number of matches where the item was purchased
   *   <li>Pick rate - percentage of matches where the item was purchased
   *   <li>Win rate - percentage of wins when the item was purchased
   *   <li>Average purchase time - average time in seconds when the item was purchased
   * </ul>
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive). If null, includes all history.
   * @param endDate Optional end date filter (inclusive). If null, uses current date.
   * @param items Optional set of item dnames to include. If null, returns top items by pick count.
   * @param excludedItems Optional set of item dnames to exclude from results.
   * @param limit Maximum number of items to return. Defaults to 10 if null.
   * @return List of ItemRankingResponse ordered by pick count descending
   */
  @GetMapping("/{playerId}/itemRanking")
  public List<ItemRankingResponse> getItemRanking(
      @PathVariable Long playerId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate,
      @RequestParam(required = false) Set<String> items,
      @RequestParam(required = false) Set<String> excludedItems,
      @RequestParam(required = false) Integer limit) {

    log.info(
        "GET /api/v1/player/{}/itemRanking - startDate={}, endDate={}, items={}, excludedItems={}, limit={}",
        playerId,
        startDate,
        endDate,
        items,
        excludedItems,
        limit);

    return itemRankingService.getItemRankings(
        playerId, startDate, endDate, items, excludedItems, limit);
  }
}
