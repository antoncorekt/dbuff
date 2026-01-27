package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.ItemDomain_;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchDomain_;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerDomain_;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain_;
import com.ako.dbuff.resources.model.ItemRankingResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * Repository for item ranking queries using Criteria Builder. Provides complex aggregation queries
 * for item statistics per player.
 */
@Repository
public class ItemRankingRepository {

  @PersistenceContext private EntityManager entityManager;

  /**
   * Finds item rankings for a specific player with optional filters.
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive)
   * @param endDate Optional end date filter (inclusive)
   * @param itemIds Optional set of item IDs to include (if null, returns top items by pick rate)
   * @param excludedItems Optional set of item IDs to exclude
   * @param limit Maximum number of items to return (default 10)
   * @return List of ItemRankingResponse ordered by pick count descending
   */
  public List<ItemRankingResponse> findItemRankingsByPlayer(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<Long> itemIds,
      Set<Long> excludedItems,
      Integer limit) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // First, get total match count for the player within the date range
    Long totalMatches = getTotalMatchCount(playerId, startDate, endDate);
    if (totalMatches == null || totalMatches == 0) {
      return List.of();
    }

    // Get player name
    String playerName = getPlayerName(playerId);

    // Main query for item statistics
    CriteriaQuery<Tuple> query = cb.createTupleQuery();
    Root<ItemDomain> itemRoot = query.from(ItemDomain.class);

    // We need to join with MatchDomain and PlayerMatchStatisticDomain
    // Since ItemDomain doesn't have direct relationships, we use cross joins with predicates
    Root<MatchDomain> matchRoot = query.from(MatchDomain.class);
    Root<PlayerMatchStatisticDomain> statsRoot = query.from(PlayerMatchStatisticDomain.class);

    // Build predicates
    List<Predicate> predicates = new ArrayList<>();

    // Player filter on ItemDomain
    predicates.add(cb.equal(itemRoot.get(ItemDomain_.playerId), playerId));

    // Correlate item with match by matchId
    predicates.add(cb.equal(itemRoot.get(ItemDomain_.matchId), matchRoot.get(MatchDomain_.id)));

    // Correlate item with stats by matchId and playerSlot
    predicates.add(
        cb.equal(
            itemRoot.get(ItemDomain_.matchId), statsRoot.get(PlayerMatchStatisticDomain_.matchId)));
    predicates.add(
        cb.equal(
            itemRoot.get(ItemDomain_.playerSlot),
            statsRoot.get(PlayerMatchStatisticDomain_.playerSlot)));

    // Date filters on match
    if (startDate != null) {
      predicates.add(
          cb.greaterThanOrEqualTo(matchRoot.get(MatchDomain_.startLocalDate), startDate));
    }
    if (endDate != null) {
      predicates.add(cb.lessThanOrEqualTo(matchRoot.get(MatchDomain_.startLocalDate), endDate));
    }

    // Item ID filters
    if (itemIds != null && !itemIds.isEmpty()) {
      predicates.add(itemRoot.get(ItemDomain_.itemId).in(itemIds));
    }

    // Excluded items filter
    if (excludedItems != null && !excludedItems.isEmpty()) {
      predicates.add(cb.not(itemRoot.get(ItemDomain_.itemId).in(excludedItems)));
    }

    // Exclude neutral items from ranking (they are special items)
    predicates.add(cb.equal(itemRoot.get(ItemDomain_.isNeutral), false));

    // Select aggregated values
    query.multiselect(
        itemRoot.get(ItemDomain_.itemId).alias("itemId"),
        itemRoot.get(ItemDomain_.itemName).alias("itemName"),
        itemRoot.get(ItemDomain_.itemPrettyName).alias("itemPrettyName"),
        cb.countDistinct(itemRoot.get(ItemDomain_.matchId)).alias("pickCount"),
        cb.sum(statsRoot.get(PlayerMatchStatisticDomain_.win)).alias("winCount"),
        cb.avg(itemRoot.get(ItemDomain_.itemPurchaseTime)).alias("avgPurchaseTime"));

    query.where(predicates.toArray(new Predicate[0]));

    // Group by item
    query.groupBy(
        itemRoot.get(ItemDomain_.itemId),
        itemRoot.get(ItemDomain_.itemName),
        itemRoot.get(ItemDomain_.itemPrettyName));

    // Order by pick count descending
    query.orderBy(cb.desc(cb.countDistinct(itemRoot.get(ItemDomain_.matchId))));

    // Execute query with limit
    List<Tuple> results =
        entityManager.createQuery(query).setMaxResults(limit != null ? limit : 10).getResultList();

    // Map results to response objects
    return results.stream()
        .map(tuple -> mapToItemRankingResponse(tuple, totalMatches, playerId, playerName))
        .toList();
  }

  /** Gets the total number of matches for a player within the date range. */
  private Long getTotalMatchCount(Long playerId, LocalDate startDate, LocalDate endDate) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<PlayerMatchStatisticDomain> statsRoot = countQuery.from(PlayerMatchStatisticDomain.class);
    Root<MatchDomain> matchRoot = countQuery.from(MatchDomain.class);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(cb.equal(statsRoot.get(PlayerMatchStatisticDomain_.playerId), playerId));
    predicates.add(
        cb.equal(
            statsRoot.get(PlayerMatchStatisticDomain_.matchId), matchRoot.get(MatchDomain_.id)));

    if (startDate != null) {
      predicates.add(
          cb.greaterThanOrEqualTo(matchRoot.get(MatchDomain_.startLocalDate), startDate));
    }
    if (endDate != null) {
      predicates.add(cb.lessThanOrEqualTo(matchRoot.get(MatchDomain_.startLocalDate), endDate));
    }

    countQuery.select(cb.countDistinct(statsRoot.get(PlayerMatchStatisticDomain_.matchId)));
    countQuery.where(predicates.toArray(new Predicate[0]));

    return entityManager.createQuery(countQuery).getSingleResult();
  }

  /** Gets the player name by player ID. */
  private String getPlayerName(Long playerId) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<String> nameQuery = cb.createQuery(String.class);
    Root<PlayerDomain> playerRoot = nameQuery.from(PlayerDomain.class);

    nameQuery.select(playerRoot.get(PlayerDomain_.name));
    nameQuery.where(cb.equal(playerRoot.get(PlayerDomain_.id), playerId));

    List<String> results = entityManager.createQuery(nameQuery).setMaxResults(1).getResultList();
    return results.isEmpty() ? null : results.get(0);
  }

  /** Maps a Tuple result to ItemRankingResponse with calculated rates. */
  private ItemRankingResponse mapToItemRankingResponse(
      Tuple tuple, Long totalMatches, Long playerId, String playerName) {
    Long pickCount = tuple.get("pickCount", Long.class);
    Long winCount = tuple.get("winCount", Long.class);
    Double avgPurchaseTime = tuple.get("avgPurchaseTime", Double.class);

    // Calculate pick rate: (pickCount / totalMatches) * 100
    BigDecimal pickRate =
        BigDecimal.valueOf(pickCount)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(totalMatches), 2, RoundingMode.HALF_UP);

    // Calculate win rate: (winCount / pickCount) * 100
    BigDecimal winRate =
        pickCount > 0
            ? BigDecimal.valueOf(winCount != null ? winCount : 0)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(pickCount), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

    return ItemRankingResponse.builder()
        .itemId(tuple.get("itemId", Long.class))
        .itemName(tuple.get("itemName", String.class))
        .itemPrettyName(tuple.get("itemPrettyName", String.class))
        .playerId(playerId)
        .playerName(playerName)
        .pickCount(pickCount)
        .pickRate(pickRate)
        .winRate(winRate)
        .avgPurchaseTime(
            avgPurchaseTime != null
                ? BigDecimal.valueOf(avgPurchaseTime).setScale(2, RoundingMode.HALF_UP)
                : null)
        .build();
  }
}
