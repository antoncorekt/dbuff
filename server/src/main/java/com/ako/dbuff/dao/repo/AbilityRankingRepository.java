package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.AbilityDomain_;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchDomain_;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerDomain_;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain_;
import com.ako.dbuff.resources.model.AbilityRankingResponse;
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
 * Repository for ability ranking queries using Criteria Builder. Provides complex aggregation
 * queries for ability statistics per player.
 */
@Repository
public class AbilityRankingRepository {

  @PersistenceContext private EntityManager entityManager;

  /**
   * Finds ability rankings for a specific player with optional filters.
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive)
   * @param endDate Optional end date filter (inclusive)
   * @param abilityIds Optional set of ability IDs to include (if null, returns top abilities by
   *     pick rate)
   * @param excludedAbilities Optional set of ability IDs to exclude
   * @param limit Maximum number of abilities to return (default 10)
   * @return List of AbilityRankingResponse ordered by pick count descending
   */
  public List<AbilityRankingResponse> findAbilityRankingsByPlayer(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<Long> abilityIds,
      Set<Long> excludedAbilities,
      Integer limit) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // First, get total match count for the player within the date range
    Long totalMatches = getTotalMatchCount(playerId, startDate, endDate);
    if (totalMatches == null || totalMatches == 0) {
      return List.of();
    }

    // Get player name
    String playerName = getPlayerName(playerId);

    // Main query for ability statistics
    CriteriaQuery<Tuple> query = cb.createTupleQuery();
    Root<AbilityDomain> abilityRoot = query.from(AbilityDomain.class);

    // We need to join with MatchDomain and PlayerMatchStatisticDomain
    // Since AbilityDomain doesn't have direct relationships, we use cross joins with predicates
    Root<MatchDomain> matchRoot = query.from(MatchDomain.class);
    Root<PlayerMatchStatisticDomain> statsRoot = query.from(PlayerMatchStatisticDomain.class);

    // Build predicates
    List<Predicate> predicates = new ArrayList<>();

    // Player filter on AbilityDomain
    predicates.add(cb.equal(abilityRoot.get(AbilityDomain_.playerId), playerId));

    // Correlate ability with match by matchId
    predicates.add(
        cb.equal(abilityRoot.get(AbilityDomain_.matchId), matchRoot.get(MatchDomain_.id)));

    // Correlate ability with stats by matchId and playerSlot
    predicates.add(
        cb.equal(
            abilityRoot.get(AbilityDomain_.matchId),
            statsRoot.get(PlayerMatchStatisticDomain_.matchId)));
    predicates.add(
        cb.equal(
            abilityRoot.get(AbilityDomain_.playerSlot),
            statsRoot.get(PlayerMatchStatisticDomain_.playerSlot)));

    // Date filters on match
    if (startDate != null) {
      predicates.add(
          cb.greaterThanOrEqualTo(matchRoot.get(MatchDomain_.startLocalDate), startDate));
    }
    if (endDate != null) {
      predicates.add(cb.lessThanOrEqualTo(matchRoot.get(MatchDomain_.startLocalDate), endDate));
    }

    // Ability ID filters
    if (abilityIds != null && !abilityIds.isEmpty()) {
      predicates.add(abilityRoot.get(AbilityDomain_.abilityId).in(abilityIds));
    }

    // Excluded abilities filter
    if (excludedAbilities != null && !excludedAbilities.isEmpty()) {
      predicates.add(cb.not(abilityRoot.get(AbilityDomain_.abilityId).in(excludedAbilities)));
    }

    // Select aggregated values
    query.multiselect(
        abilityRoot.get(AbilityDomain_.abilityId).alias("abilityId"),
        abilityRoot.get(AbilityDomain_.name).alias("abilityName"),
        abilityRoot.get(AbilityDomain_.prettyName).alias("abilityPrettyName"),
        cb.countDistinct(abilityRoot.get(AbilityDomain_.matchId)).alias("pickCount"),
        cb.sum(statsRoot.get(PlayerMatchStatisticDomain_.win)).alias("winCount"));

    query.where(predicates.toArray(new Predicate[0]));

    // Group by ability
    query.groupBy(
        abilityRoot.get(AbilityDomain_.abilityId),
        abilityRoot.get(AbilityDomain_.name),
        abilityRoot.get(AbilityDomain_.prettyName));

    // Order by pick count descending
    query.orderBy(cb.desc(cb.countDistinct(abilityRoot.get(AbilityDomain_.matchId))));

    // Execute query with limit
    List<Tuple> results =
        entityManager.createQuery(query).setMaxResults(limit != null ? limit : 10).getResultList();

    // Map results to response objects
    return results.stream()
        .map(tuple -> mapToAbilityRankingResponse(tuple, totalMatches, playerId, playerName))
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

  /** Maps a Tuple result to AbilityRankingResponse with calculated rates. */
  private AbilityRankingResponse mapToAbilityRankingResponse(
      Tuple tuple, Long totalMatches, Long playerId, String playerName) {
    Long pickCount = tuple.get("pickCount", Long.class);
    Long winCount = tuple.get("winCount", Long.class);

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

    return AbilityRankingResponse.builder()
        .abilityId(tuple.get("abilityId", Long.class))
        .abilityName(tuple.get("abilityName", String.class))
        .abilityPrettyName(tuple.get("abilityPrettyName", String.class))
        .playerId(playerId)
        .playerName(playerName)
        .pickCount(pickCount)
        .pickRate(pickRate)
        .winRate(winRate)
        .build();
  }
}
