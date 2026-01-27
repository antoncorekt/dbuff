package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.repo.ItemRankingRepository;
import com.ako.dbuff.resources.model.ItemRankingResponse;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.ItemConstant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for calculating item rankings per player. Provides statistics about item usage including
 * pick rate, win rate, and average purchase time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemRankingService {

  private static final int DEFAULT_LIMIT = 10;

  private final ItemRankingRepository itemRankingRepository;
  private final ConstantsManagers constantsManagers;

  /**
   * Gets item rankings for a specific player.
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive). If null, includes all history.
   * @param endDate Optional end date filter (inclusive). If null, uses current date.
   * @param itemDnames Optional set of item dnames to include. If null, returns top items by pick
   *     count.
   * @param excludedItemDnames Optional set of item dnames to exclude from results.
   * @param limit Maximum number of items to return. Defaults to 10 if null.
   * @return List of ItemRankingResponse ordered by pick count descending
   */
  @Transactional(readOnly = true)
  public List<ItemRankingResponse> getItemRankings(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<String> itemDnames,
      Set<String> excludedItemDnames,
      Integer limit) {

    log.info(
        "Fetching item rankings for player {} with startDate={}, endDate={}, items={}, excludedItems={}, limit={}",
        playerId,
        startDate,
        endDate,
        itemDnames,
        excludedItemDnames,
        limit);

    // Use current date as default end date if not specified
    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

    // Use default limit if not specified
    int effectiveLimit = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;

    // Convert item dnames to IDs using ConstantsManagers
    Set<Long> itemIds = convertDnamesToIds(itemDnames);
    Set<Long> excludedItemIds = convertDnamesToIds(excludedItemDnames);

    log.debug(
        "Converted item dnames to IDs: items={} -> {}, excludedItems={} -> {}",
        itemDnames,
        itemIds,
        excludedItemDnames,
        excludedItemIds);

    List<ItemRankingResponse> rankings =
        itemRankingRepository.findItemRankingsByPlayer(
            playerId, startDate, effectiveEndDate, itemIds, excludedItemIds, effectiveLimit);

    log.info("Found {} item rankings for player {}", rankings.size(), playerId);

    return rankings;
  }

  /**
   * Converts a set of item dnames to their corresponding IDs using the item constant map.
   *
   * @param dnames Set of item dnames (e.g., "blink", "black_king_bar")
   * @return Set of item IDs, or null if input is null or empty
   */
  private Set<Long> convertDnamesToIds(Set<String> dnames) {
    if (dnames == null || dnames.isEmpty()) {
      return null;
    }

    Map<String, ItemConstant> itemConstantMap = constantsManagers.getItemConstantMap();

    Set<Long> ids =
        dnames.stream()
            .map(
                dname -> {
                  ItemConstant itemConstant = itemConstantMap.get(dname);
                  if (itemConstant == null) {
                    log.warn("Unknown item dname: {}", dname);
                    return null;
                  }
                  return itemConstant.getId();
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    return ids.isEmpty() ? null : ids;
  }
}
