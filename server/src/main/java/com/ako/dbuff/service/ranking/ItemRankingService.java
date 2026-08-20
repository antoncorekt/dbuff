package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.repo.ItemRankingRepository;
import com.ako.dbuff.resources.model.ItemComboStatisticResponse;
import com.ako.dbuff.resources.model.ItemRankingResponse;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.NameResolution;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for calculating item rankings per player. Provides statistics about item usage including
 * pick rate, win rate, average purchase time, and average uses per game.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemRankingService {

  private static final int DEFAULT_LIMIT = 10;

  private final ItemRankingRepository itemRankingRepository;
  private final ConstantNameResolver nameResolver;

  /**
   * Gets item rankings for a specific player.
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive). If null, includes all history.
   * @param endDate Optional end date filter (inclusive). If null, uses current date.
   * @param itemNames Optional item names to include. If null or empty, returns top items by pick
   *     count.
   * @param excludedItemNames Optional item names to exclude from results.
   * @param heroNames Optional hero names to restrict the query to.
   * @param limit Maximum number of items to return. Defaults to 10 if null.
   * @return List of ItemRankingResponse ordered by pick count descending
   * @throws UnknownConstantNameException if any supplied name matches no known constant
   */
  @Transactional(readOnly = true)
  public List<ItemRankingResponse> getItemRankings(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<String> itemNames,
      Set<String> excludedItemNames,
      Set<String> heroNames,
      Integer limit) {

    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();
    int effectiveLimit = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;

    Set<Long> itemIds = resolveItemsOrThrow(itemNames);
    Set<Long> excludedItemIds = resolveItemsOrThrow(excludedItemNames);
    Set<Long> heroIds = resolveHeroesOrThrow(heroNames);

    log.info(
        "Fetching item rankings for player {}: startDate={}, endDate={}, items={}, excluded={}, heroes={}, limit={}",
        playerId,
        startDate,
        effectiveEndDate,
        itemIds,
        excludedItemIds,
        heroIds,
        effectiveLimit);

    List<ItemRankingResponse> rankings =
        itemRankingRepository.findItemRankingsByPlayer(
            playerId,
            startDate,
            effectiveEndDate,
            itemIds,
            excludedItemIds,
            heroIds,
            effectiveLimit);

    log.info("Found {} item rankings for player {}", rankings.size(), playerId);
    return rankings;
  }

  /**
   * Gets statistics over the games in which the player held every one of the named items.
   *
   * @throws UnknownConstantNameException if any supplied name matches no known constant
   */
  @Transactional(readOnly = true)
  public ItemComboStatisticResponse getItemComboStatistics(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<String> itemNames,
      Set<String> heroNames) {

    Set<Long> itemIds = resolveItemsOrThrow(itemNames);
    Set<Long> heroIds = resolveHeroesOrThrow(heroNames);
    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

    log.info(
        "Fetching item combo statistics for player {}: items={}, heroes={}, {} to {}",
        playerId,
        itemIds,
        heroIds,
        startDate,
        effectiveEndDate);

    return itemRankingRepository.findItemComboStatistics(
        playerId, itemIds, heroIds, startDate, effectiveEndDate);
  }

  /**
   * Resolves item names to IDs, refusing to proceed if any name is unknown.
   *
   * <p>Throwing rather than dropping is deliberate. A null ID set means "no filter" to the
   * repository, so silently discarding unknown names turns a filtered query into an unfiltered
   * top-N ranking and answers a different question than the caller asked.
   */
  private Set<Long> resolveItemsOrThrow(Set<String> names) {
    NameResolution resolution = nameResolver.resolveItems(names);
    if (resolution.hasUnresolved()) {
      throw new UnknownConstantNameException("items", resolution.unresolvedNames());
    }
    return resolution.idsOrNullIfEmpty();
  }

  private Set<Long> resolveHeroesOrThrow(Set<String> names) {
    NameResolution resolution = nameResolver.resolveHeroes(names);
    if (resolution.hasUnresolved()) {
      throw new UnknownConstantNameException("heroes", resolution.unresolvedNames());
    }
    return resolution.idsOrNullIfEmpty();
  }
}
