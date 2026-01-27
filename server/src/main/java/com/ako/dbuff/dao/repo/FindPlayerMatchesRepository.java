package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchDomain_;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain_;
import com.ako.dbuff.resources.model.FindPlayerMatchesResponse;
import com.ako.dbuff.resources.model.FindPlayerMatchesResponse.PlayerMatchStatistic;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * Repository for finding player matches with statistics using Criteria Builder. Provides queries
 * for finding matches where a player participated along with statistics for default players.
 */
@Repository
public class FindPlayerMatchesRepository {

  private static final String DOTABUFF_URL_TEMPLATE = "https://www.dotabuff.com/matches/%d/builds";

  @PersistenceContext private EntityManager entityManager;

  /**
   * Finds matches for a player with statistics for the player and default players.
   *
   * @param playerId The player's account ID
   * @param playerName The player's name
   * @param defaultPlayerIds Set of default player IDs to include in statistics
   * @param limit Maximum number of matches to return
   * @return List of FindPlayerMatchesResponse ordered by match date descending
   */
  public List<FindPlayerMatchesResponse> findPlayerMatches(
      Long playerId, String playerName, Set<Long> defaultPlayerIds, Integer limit) {

    // Combine searched player with default players
    Set<Long> allPlayerIds = new java.util.HashSet<>(defaultPlayerIds);
    allPlayerIds.add(playerId);

    // Get matches for the player ordered by date descending
    List<Long> matchIds = getPlayerMatchIds(playerId, limit);

    if (matchIds.isEmpty()) {
      return List.of();
    }

    // Get match details
    Map<Long, MatchInfo> matchInfoMap = getMatchInfo(matchIds);

    // Get statistics for all players in these matches
    Map<Long, List<PlayerMatchStatistic>> matchStatistics =
        getMatchStatistics(matchIds, allPlayerIds);

    // Build response
    return matchIds.stream()
        .map(
            matchId -> {
              MatchInfo matchInfo = matchInfoMap.get(matchId);
              return FindPlayerMatchesResponse.builder()
                  .matchId(matchId)
                  .matchDate(matchInfo != null ? matchInfo.startLocalDate : null)
                  .dotabuffUrl(String.format(DOTABUFF_URL_TEMPLATE, matchId))
                  .playerId(playerId)
                  .playerName(playerName)
                  .statistics(matchStatistics.getOrDefault(matchId, List.of()))
                  .build();
            })
        .toList();
  }

  /** Gets match IDs for a player ordered by date descending. */
  private List<Long> getPlayerMatchIds(Long playerId, Integer limit) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> query = cb.createTupleQuery();
    Root<PlayerMatchStatisticDomain> statsRoot = query.from(PlayerMatchStatisticDomain.class);
    Root<MatchDomain> matchRoot = query.from(MatchDomain.class);

    // Select both matchId and date to allow ordering with distinct
    query.multiselect(
        statsRoot.get(PlayerMatchStatisticDomain_.matchId).alias("matchId"),
        matchRoot.get(MatchDomain_.startLocalDate).alias("startDate"));
    query.where(
        cb.equal(statsRoot.get(PlayerMatchStatisticDomain_.playerId), playerId),
        cb.equal(
            statsRoot.get(PlayerMatchStatisticDomain_.matchId), matchRoot.get(MatchDomain_.id)));
    query.groupBy(
        statsRoot.get(PlayerMatchStatisticDomain_.matchId),
        matchRoot.get(MatchDomain_.startLocalDate));
    query.orderBy(cb.desc(matchRoot.get(MatchDomain_.startLocalDate)));

    int effectiveLimit = limit != null && limit > 0 ? limit : 20;
    List<Tuple> results =
        entityManager.createQuery(query).setMaxResults(effectiveLimit).getResultList();

    return results.stream().map(t -> t.get("matchId", Long.class)).toList();
  }

  /** Gets match info (date) for a list of match IDs. */
  private Map<Long, MatchInfo> getMatchInfo(List<Long> matchIds) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> query = cb.createTupleQuery();
    Root<MatchDomain> matchRoot = query.from(MatchDomain.class);

    query.multiselect(
        matchRoot.get(MatchDomain_.id).alias("matchId"),
        matchRoot.get(MatchDomain_.startLocalDate).alias("startLocalDate"));
    query.where(matchRoot.get(MatchDomain_.id).in(matchIds));

    List<Tuple> results = entityManager.createQuery(query).getResultList();

    Map<Long, MatchInfo> matchInfoMap = new LinkedHashMap<>();
    for (Tuple tuple : results) {
      Long matchId = tuple.get("matchId", Long.class);
      LocalDate startLocalDate = tuple.get("startLocalDate", LocalDate.class);
      matchInfoMap.put(matchId, new MatchInfo(startLocalDate));
    }
    return matchInfoMap;
  }

  /** Gets statistics for specified players in the given matches. */
  private Map<Long, List<PlayerMatchStatistic>> getMatchStatistics(
      List<Long> matchIds, Set<Long> playerIds) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> query = cb.createTupleQuery();
    Root<PlayerMatchStatisticDomain> statsRoot = query.from(PlayerMatchStatisticDomain.class);

    query.multiselect(
        statsRoot.get(PlayerMatchStatisticDomain_.matchId).alias("matchId"),
        statsRoot.get(PlayerMatchStatisticDomain_.playerId).alias("playerId"),
        statsRoot.get(PlayerMatchStatisticDomain_.playerSlot).alias("playerSlot"),
        statsRoot.get(PlayerMatchStatisticDomain_.heroPrettyName).alias("heroPrettyName"),
        statsRoot.get(PlayerMatchStatisticDomain_.kda).alias("kda"),
        statsRoot.get(PlayerMatchStatisticDomain_.win).alias("win"),
        statsRoot.get(PlayerMatchStatisticDomain_.goldPerMin).alias("goldPerMin"));

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.matchId).in(matchIds));
    predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.playerId).in(playerIds));

    query.where(predicates.toArray(new Predicate[0]));
    query.orderBy(
        cb.asc(statsRoot.get(PlayerMatchStatisticDomain_.matchId)),
        cb.asc(statsRoot.get(PlayerMatchStatisticDomain_.playerSlot)));

    List<Tuple> results = entityManager.createQuery(query).getResultList();

    // Group by match ID
    Map<Long, List<PlayerMatchStatistic>> matchStatistics = new LinkedHashMap<>();
    for (Tuple tuple : results) {
      Long matchId = tuple.get("matchId", Long.class);
      Long statPlayerId = tuple.get("playerId", Long.class);

      PlayerMatchStatistic stat =
          PlayerMatchStatistic.builder()
              .playerId(statPlayerId)
              .playerName(getPlayerNameFromId(statPlayerId))
              .playerSlot(tuple.get("playerSlot", Long.class))
              .heroPrettyName(tuple.get("heroPrettyName", String.class))
              .kda(getAsBigDecimal(tuple.get("kda")))
              .win(tuple.get("win", Long.class))
              .goldPerMin(tuple.get("goldPerMin", Long.class))
              .build();

      matchStatistics.computeIfAbsent(matchId, k -> new ArrayList<>()).add(stat);
    }

    return matchStatistics;
  }

  /** Gets player name from player ID using a simple query. */
  private String getPlayerNameFromId(Long playerId) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<String> query = cb.createQuery(String.class);
    Root<com.ako.dbuff.dao.model.PlayerDomain> playerRoot =
        query.from(com.ako.dbuff.dao.model.PlayerDomain.class);

    query.select(playerRoot.get(com.ako.dbuff.dao.model.PlayerDomain_.name));
    query.where(cb.equal(playerRoot.get(com.ako.dbuff.dao.model.PlayerDomain_.id), playerId));

    List<String> results = entityManager.createQuery(query).setMaxResults(1).getResultList();
    return results.isEmpty() ? null : results.get(0);
  }

  /** Converts a value to BigDecimal, handling both Double and BigDecimal types. */
  private BigDecimal getAsBigDecimal(Object value) {
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

  /** Simple record to hold match info. */
  private record MatchInfo(LocalDate startLocalDate) {}
}
