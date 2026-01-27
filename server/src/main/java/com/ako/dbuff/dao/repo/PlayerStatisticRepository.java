package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchDomain_;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerDomain_;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain_;
import com.ako.dbuff.resources.model.PlayerStatisticResponse;
import com.ako.dbuff.resources.model.PlayerStatisticResponse.HeroStatistic;
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
import org.springframework.stereotype.Repository;

/**
 * Repository for player statistics queries using Criteria Builder. Provides complex aggregation
 * queries for player match statistics.
 */
@Repository
public class PlayerStatisticRepository {

  @PersistenceContext private EntityManager entityManager;

  /**
   * Finds aggregated player statistics for a specific player within a date range.
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive)
   * @param endDate Optional end date filter (inclusive)
   * @param heroLimit Number of popular heroes to return (default 3)
   * @return PlayerStatisticResponse with aggregated statistics
   */
  public PlayerStatisticResponse findPlayerStatistics(
      Long playerId, LocalDate startDate, LocalDate endDate, Integer heroLimit) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // Get player name
    String playerName = getPlayerName(playerId);

    // Get total match count
    Long totalMatches = getTotalMatchCount(playerId, startDate, endDate);
    if (totalMatches == null || totalMatches == 0) {
      return PlayerStatisticResponse.builder()
          .playerId(playerId)
          .playerName(playerName)
          .startDate(startDate)
          .endDate(endDate)
          .totalMatches(0L)
          .popularHeroes(List.of())
          .build();
    }

    // Get popular heroes
    List<HeroStatistic> popularHeroes =
        getPopularHeroes(playerId, startDate, endDate, heroLimit != null ? heroLimit : 3);

    // Get aggregated statistics
    Tuple stats = getAggregatedStats(playerId, startDate, endDate);

    return buildResponse(
        playerId, playerName, startDate, endDate, totalMatches, popularHeroes, stats);
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

  /** Gets the total number of matches for a player within the date range. */
  private Long getTotalMatchCount(Long playerId, LocalDate startDate, LocalDate endDate) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<PlayerMatchStatisticDomain> statsRoot = countQuery.from(PlayerMatchStatisticDomain.class);
    Root<MatchDomain> matchRoot = countQuery.from(MatchDomain.class);

    List<Predicate> predicates =
        buildDatePredicates(cb, statsRoot, matchRoot, playerId, startDate, endDate);

    countQuery.select(cb.countDistinct(statsRoot.get(PlayerMatchStatisticDomain_.matchId)));
    countQuery.where(predicates.toArray(new Predicate[0]));

    return entityManager.createQuery(countQuery).getSingleResult();
  }

  /** Gets the N most popular heroes for a player. */
  private List<HeroStatistic> getPopularHeroes(
      Long playerId, LocalDate startDate, LocalDate endDate, int limit) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> query = cb.createTupleQuery();
    Root<PlayerMatchStatisticDomain> statsRoot = query.from(PlayerMatchStatisticDomain.class);
    Root<MatchDomain> matchRoot = query.from(MatchDomain.class);

    List<Predicate> predicates =
        buildDatePredicates(cb, statsRoot, matchRoot, playerId, startDate, endDate);

    query.multiselect(
        statsRoot.get(PlayerMatchStatisticDomain_.heroId).alias("heroId"),
        statsRoot.get(PlayerMatchStatisticDomain_.heroName).alias("heroName"),
        statsRoot.get(PlayerMatchStatisticDomain_.heroPrettyName).alias("heroPrettyName"),
        cb.count(statsRoot.get(PlayerMatchStatisticDomain_.matchId)).alias("pickCount"),
        cb.sum(statsRoot.get(PlayerMatchStatisticDomain_.win)).alias("wins"));

    query.where(predicates.toArray(new Predicate[0]));
    query.groupBy(
        statsRoot.get(PlayerMatchStatisticDomain_.heroId),
        statsRoot.get(PlayerMatchStatisticDomain_.heroName),
        statsRoot.get(PlayerMatchStatisticDomain_.heroPrettyName));
    query.orderBy(cb.desc(cb.count(statsRoot.get(PlayerMatchStatisticDomain_.matchId))));

    List<Tuple> results = entityManager.createQuery(query).setMaxResults(limit).getResultList();

    return results.stream()
        .map(
            tuple -> {
              Long pickCount = tuple.get("pickCount", Long.class);
              Long wins = tuple.get("wins", Long.class);
              BigDecimal winRate =
                  pickCount > 0
                      ? BigDecimal.valueOf(wins != null ? wins : 0)
                          .multiply(BigDecimal.valueOf(100))
                          .divide(BigDecimal.valueOf(pickCount), 2, RoundingMode.HALF_UP)
                      : BigDecimal.ZERO;

              return HeroStatistic.builder()
                  .heroId(tuple.get("heroId", Long.class))
                  .heroName(tuple.get("heroName", String.class))
                  .heroPrettyName(tuple.get("heroPrettyName", String.class))
                  .pickCount(pickCount)
                  .winRate(winRate)
                  .build();
            })
        .toList();
  }

  /** Gets all aggregated statistics for a player. */
  private Tuple getAggregatedStats(Long playerId, LocalDate startDate, LocalDate endDate) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> query = cb.createTupleQuery();
    Root<PlayerMatchStatisticDomain> statsRoot = query.from(PlayerMatchStatisticDomain.class);
    Root<MatchDomain> matchRoot = query.from(MatchDomain.class);

    List<Predicate> predicates =
        buildDatePredicates(cb, statsRoot, matchRoot, playerId, startDate, endDate);

    query.multiselect(
        // Observer and Sentry wards
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.obsPlaced)).alias("avgObsPlaced"),
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.senPlaced)).alias("avgSenPlaced"),
        // Farming statistics
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.creepsStacked)).alias("avgCreepsStacked"),
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.lastHits)).alias("avgLastHits"),
        cb.max(statsRoot.get(PlayerMatchStatisticDomain_.lastHits)).alias("maxLastHits"),
        cb.min(statsRoot.get(PlayerMatchStatisticDomain_.lastHits)).alias("minLastHits"),
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.denies)).alias("avgDenies"),
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.campsStacked)).alias("avgCampsStacked"),
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.runePickups)).alias("avgRunePickups"),
        // Objective statistics
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.towerKills)).alias("avgTowerKills"),
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.roshanKills)).alias("avgRoshanKills"),
        // Win/Loss statistics
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.win)).alias("avgWin"),
        cb.sum(statsRoot.get(PlayerMatchStatisticDomain_.win)).alias("wins"),
        cb.count(statsRoot.get(PlayerMatchStatisticDomain_.matchId)).alias("totalGames"),
        // KDA statistics
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.kda)).alias("avgKda"),
        cb.max(statsRoot.get(PlayerMatchStatisticDomain_.kda)).alias("maxKda"),
        cb.min(statsRoot.get(PlayerMatchStatisticDomain_.kda)).alias("minKda"),
        // Kill statistics
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.neutralKills)).alias("avgNeutralKills"),
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.courierKills)).alias("avgCourierKills"),
        // Efficiency statistics
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.laneEfficiency))
            .alias("avgLaneEfficiency"),
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.goldPerMin)).alias("avgGoldPerMin"),
        cb.max(statsRoot.get(PlayerMatchStatisticDomain_.goldPerMin)).alias("maxGoldPerMin"),
        cb.min(statsRoot.get(PlayerMatchStatisticDomain_.goldPerMin)).alias("minGoldPerMin"),
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.xpPerMin)).alias("avgXpPerMin"));

    query.where(predicates.toArray(new Predicate[0]));

    return entityManager.createQuery(query).getSingleResult();
  }

  /** Builds common date predicates for queries. */
  private List<Predicate> buildDatePredicates(
      CriteriaBuilder cb,
      Root<PlayerMatchStatisticDomain> statsRoot,
      Root<MatchDomain> matchRoot,
      Long playerId,
      LocalDate startDate,
      LocalDate endDate) {

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

    return predicates;
  }

  /** Builds the response from aggregated data. */
  private PlayerStatisticResponse buildResponse(
      Long playerId,
      String playerName,
      LocalDate startDate,
      LocalDate endDate,
      Long totalMatches,
      List<HeroStatistic> popularHeroes,
      Tuple stats) {

    Long wins = stats.get("wins", Long.class);
    Long totalGames = stats.get("totalGames", Long.class);
    Long losses = totalGames - (wins != null ? wins : 0);

    return PlayerStatisticResponse.builder()
        .playerId(playerId)
        .playerName(playerName)
        .startDate(startDate)
        .endDate(endDate)
        .totalMatches(totalMatches)
        .popularHeroes(popularHeroes)
        // Observer and Sentry wards
        .avgObsPlaced(getAsBigDecimal(stats, "avgObsPlaced"))
        .avgSenPlaced(getAsBigDecimal(stats, "avgSenPlaced"))
        // Farming statistics
        .avgCreepsStacked(getAsBigDecimal(stats, "avgCreepsStacked"))
        .avgLastHits(getAsBigDecimal(stats, "avgLastHits"))
        .maxLastHits(stats.get("maxLastHits", Long.class))
        .minLastHits(stats.get("minLastHits", Long.class))
        .avgDenies(getAsBigDecimal(stats, "avgDenies"))
        .avgCampsStacked(getAsBigDecimal(stats, "avgCampsStacked"))
        .avgRunePickups(getAsBigDecimal(stats, "avgRunePickups"))
        // Objective statistics
        .avgTowerKills(getAsBigDecimal(stats, "avgTowerKills"))
        .avgRoshanKills(getAsBigDecimal(stats, "avgRoshanKills"))
        // Win/Loss statistics
        .avgWinRate(
            getAsBigDecimal(stats, "avgWin")
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP))
        .wins(wins != null ? wins : 0L)
        .losses(losses)
        // KDA statistics
        .avgKda(getAsBigDecimal(stats, "avgKda"))
        .maxKda(getAsBigDecimal(stats, "maxKda"))
        .minKda(getAsBigDecimal(stats, "minKda"))
        // Kill statistics
        .avgNeutralKills(getAsBigDecimal(stats, "avgNeutralKills"))
        .avgCourierKills(getAsBigDecimal(stats, "avgCourierKills"))
        // Efficiency statistics
        .avgLaneEfficiency(getAsBigDecimal(stats, "avgLaneEfficiency"))
        .avgGoldPerMin(getAsBigDecimal(stats, "avgGoldPerMin"))
        .maxGoldPerMin(stats.get("maxGoldPerMin", Long.class))
        .minGoldPerMin(stats.get("minGoldPerMin", Long.class))
        .avgXpPerMin(getAsBigDecimal(stats, "avgXpPerMin"))
        .build();
  }

  /**
   * Gets a value from the tuple as BigDecimal, handling both Double and BigDecimal types. H2
   * returns Double for avg() on BigDecimal fields, while PostgreSQL returns BigDecimal.
   */
  private BigDecimal getAsBigDecimal(Tuple tuple, String alias) {
    Object value = tuple.get(alias);
    if (value == null) {
      return BigDecimal.ZERO;
    }
    if (value instanceof BigDecimal bd) {
      return bd.setScale(2, RoundingMode.HALF_UP);
    }
    if (value instanceof Double d) {
      return BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP);
    }
    if (value instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }
    return BigDecimal.ZERO;
  }
}
