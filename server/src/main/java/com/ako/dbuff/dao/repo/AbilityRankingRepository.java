package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.AbilityDomain_;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.ItemDomain_;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchDomain_;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerDomain_;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain_;
import com.ako.dbuff.resources.model.AbilityComboStatisticResponse;
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
import java.util.LinkedHashSet;
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
   * @param heroIds Optional set of hero IDs to restrict the query to
   * @param gameModeIds Optional set of game mode IDs to restrict the query to
   * @param limit Maximum number of abilities to return (default 10)
   * @return List of AbilityRankingResponse ordered by pick count descending
   */
  public List<AbilityRankingResponse> findAbilityRankingsByPlayer(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<Long> abilityIds,
      Set<Long> excludedAbilities,
      Set<Long> heroIds,
      Set<Long> gameModeIds,
      Integer limit) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // First, get total match count for the player within the date range and hero filter
    Long totalMatches = getTotalMatchCount(playerId, startDate, endDate, heroIds, gameModeIds);
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

    // Hero filter
    if (heroIds != null && !heroIds.isEmpty()) {
      predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.heroId).in(heroIds));
    }

    // Game mode filter
    if (gameModeIds != null && !gameModeIds.isEmpty()) {
      predicates.add(matchRoot.get(MatchDomain_.gameModeId).in(gameModeIds));
    }

    // Select aggregated values
    query.multiselect(
        abilityRoot.get(AbilityDomain_.abilityId).alias("abilityId"),
        abilityRoot.get(AbilityDomain_.name).alias("abilityName"),
        abilityRoot.get(AbilityDomain_.prettyName).alias("abilityPrettyName"),
        cb.countDistinct(abilityRoot.get(AbilityDomain_.matchId)).alias("pickCount"),
        cb.sum(statsRoot.get(PlayerMatchStatisticDomain_.win)).alias("winCount"),
        cb.avg(abilityRoot.get(AbilityDomain_.useCount)).alias("avgUseCount"));

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

  /**
   * Finds statistics over the games in which the player used EVERY one of {@code abilityIds} and,
   * if any are given, also held every one of {@code itemIds}.
   *
   * <p>Two-step by design: step one asks {@code ability_domain} alone which matches contain the
   * full set, step two aggregates the player's statistics over those matches with the date, hero
   * and game mode filters applied. Doing it in one query would need a correlated having-clause
   * across three cross joins, which is harder to read and less portable.
   *
   * <p>The item conjunction is an intersection of that first step with the same question asked of
   * {@code item_domain}, so "these skills and these items in one game" stays a conjunction rather
   * than degrading into "these skills, and separately these items".
   *
   * @param playerId the player's account ID
   * @param abilityIds the abilities that must ALL be present; empty or null yields zero games
   * @param itemIds items that must ALL also be present; empty or null applies no item restriction
   * @param heroIds optional hero restriction
   * @param gameModeIds optional game mode restriction
   * @param startDate optional inclusive lower bound on match date
   * @param endDate optional inclusive upper bound on match date
   * @return combo statistics, never null; {@code gamesFound} is 0 when nothing matched
   */
  public AbilityComboStatisticResponse findAbilityComboStatistics(
      Long playerId,
      Set<Long> abilityIds,
      Set<Long> itemIds,
      Set<Long> heroIds,
      Set<Long> gameModeIds,
      LocalDate startDate,
      LocalDate endDate) {

    String playerName = getPlayerName(playerId);
    AbilityComboStatisticResponse empty =
        AbilityComboStatisticResponse.builder()
            .playerId(playerId)
            .playerName(playerName)
            .gamesFound(0L)
            .matchIds(Set.of())
            .winRate(BigDecimal.ZERO)
            .members(List.of())
            .itemMembers(List.of())
            .build();

    if (abilityIds == null || abilityIds.isEmpty()) {
      return empty;
    }

    Set<Long> candidateMatchIds = findMatchesContainingAllAbilities(playerId, abilityIds);
    if (candidateMatchIds.isEmpty()) {
      return empty;
    }

    boolean itemFiltered = itemIds != null && !itemIds.isEmpty();
    if (itemFiltered) {
      candidateMatchIds.retainAll(findMatchesContainingAllItems(playerId, itemIds));
      if (candidateMatchIds.isEmpty()) {
        return empty;
      }
    }

    Set<Long> comboMatchIds =
        applyMatchFilters(playerId, candidateMatchIds, heroIds, gameModeIds, startDate, endDate);
    if (comboMatchIds.isEmpty()) {
      return empty;
    }

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // Win rate and average KDA over the combo games.
    CriteriaQuery<Tuple> statsQuery = cb.createTupleQuery();
    Root<PlayerMatchStatisticDomain> statsRoot = statsQuery.from(PlayerMatchStatisticDomain.class);
    statsQuery.multiselect(
        cb.sum(statsRoot.get(PlayerMatchStatisticDomain_.win)).alias("winCount"),
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.kda)).alias("avgKda"));
    statsQuery.where(
        cb.equal(statsRoot.get(PlayerMatchStatisticDomain_.playerId), playerId),
        statsRoot.get(PlayerMatchStatisticDomain_.matchId).in(comboMatchIds));

    Tuple stats = entityManager.createQuery(statsQuery).getSingleResult();
    Long winCount = stats.get("winCount", Long.class);
    Double avgKda = stats.get("avgKda", Double.class);

    long gamesFound = comboMatchIds.size();
    BigDecimal winRate =
        BigDecimal.valueOf(winCount != null ? winCount : 0L)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(gamesFound), 2, RoundingMode.HALF_UP);

    // Per-ability averages over the combo games only.
    CriteriaQuery<Tuple> memberQuery = cb.createTupleQuery();
    Root<AbilityDomain> abilityRoot = memberQuery.from(AbilityDomain.class);
    memberQuery.multiselect(
        abilityRoot.get(AbilityDomain_.abilityId).alias("abilityId"),
        abilityRoot.get(AbilityDomain_.name).alias("abilityName"),
        abilityRoot.get(AbilityDomain_.prettyName).alias("abilityPrettyName"),
        cb.avg(abilityRoot.get(AbilityDomain_.useCount)).alias("avgUseCount"));
    memberQuery.where(
        cb.equal(abilityRoot.get(AbilityDomain_.playerId), playerId),
        abilityRoot.get(AbilityDomain_.abilityId).in(abilityIds),
        abilityRoot.get(AbilityDomain_.matchId).in(comboMatchIds));
    memberQuery.groupBy(
        abilityRoot.get(AbilityDomain_.abilityId),
        abilityRoot.get(AbilityDomain_.name),
        abilityRoot.get(AbilityDomain_.prettyName));

    List<AbilityComboStatisticResponse.Member> members =
        entityManager.createQuery(memberQuery).getResultList().stream()
            .map(
                tuple -> {
                  Double avgUse = tuple.get("avgUseCount", Double.class);
                  return AbilityComboStatisticResponse.Member.builder()
                      .abilityId(tuple.get("abilityId", Long.class))
                      .abilityName(tuple.get("abilityName", String.class))
                      .abilityPrettyName(tuple.get("abilityPrettyName", String.class))
                      .avgUseCount(
                          avgUse != null
                              ? BigDecimal.valueOf(avgUse).setScale(2, RoundingMode.HALF_UP)
                              : null)
                      .build();
                })
            .toList();

    return AbilityComboStatisticResponse.builder()
        .playerId(playerId)
        .playerName(playerName)
        .gamesFound(gamesFound)
        .matchIds(comboMatchIds)
        .winRate(winRate)
        .avgKda(
            avgKda != null ? BigDecimal.valueOf(avgKda).setScale(2, RoundingMode.HALF_UP) : null)
        .members(members)
        .itemMembers(itemFiltered ? itemMembers(playerId, itemIds, comboMatchIds) : List.of())
        .build();
  }

  /**
   * Per-item averages over the combo games, for a request that named items as well as skills.
   *
   * <p>Averaged over the games that satisfied <em>both</em> conjunctions, not over every game the
   * item appears in — the question asked was about the combination.
   */
  private List<AbilityComboStatisticResponse.ItemMember> itemMembers(
      Long playerId, Set<Long> itemIds, Set<Long> comboMatchIds) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> memberQuery = cb.createTupleQuery();
    Root<ItemDomain> itemRoot = memberQuery.from(ItemDomain.class);

    memberQuery.multiselect(
        itemRoot.get(ItemDomain_.itemId).alias("itemId"),
        itemRoot.get(ItemDomain_.itemName).alias("itemName"),
        itemRoot.get(ItemDomain_.itemPrettyName).alias("itemPrettyName"),
        cb.avg(itemRoot.get(ItemDomain_.itemPurchaseTime)).alias("avgPurchaseTime"),
        cb.avg(itemRoot.get(ItemDomain_.useCount)).alias("avgUseCount"));
    memberQuery.where(
        cb.equal(itemRoot.get(ItemDomain_.playerId), playerId),
        cb.equal(itemRoot.get(ItemDomain_.isNeutral), false),
        itemRoot.get(ItemDomain_.itemId).in(itemIds),
        itemRoot.get(ItemDomain_.matchId).in(comboMatchIds));
    memberQuery.groupBy(
        itemRoot.get(ItemDomain_.itemId),
        itemRoot.get(ItemDomain_.itemName),
        itemRoot.get(ItemDomain_.itemPrettyName));

    return entityManager.createQuery(memberQuery).getResultList().stream()
        .map(
            tuple -> {
              Double avgPurchase = tuple.get("avgPurchaseTime", Double.class);
              Double avgUse = tuple.get("avgUseCount", Double.class);
              return AbilityComboStatisticResponse.ItemMember.builder()
                  .itemId(tuple.get("itemId", Long.class))
                  .itemName(tuple.get("itemName", String.class))
                  .itemPrettyName(tuple.get("itemPrettyName", String.class))
                  .avgPurchaseTime(
                      avgPurchase != null
                          ? BigDecimal.valueOf(avgPurchase).setScale(2, RoundingMode.HALF_UP)
                          : null)
                  .avgUseCount(
                      avgUse != null
                          ? BigDecimal.valueOf(avgUse).setScale(2, RoundingMode.HALF_UP)
                          : null)
                  .build();
            })
        .toList();
  }

  /**
   * Match IDs where the player held every one of {@code itemIds}.
   *
   * <p>The sibling of {@code ItemRankingRepository}'s query of the same name, kept here so the
   * skill-plus-item conjunction does not need a repository-to-repository dependency. {@code
   * countDistinct} rather than {@code count} is essential: a game with two rows for the same item
   * would otherwise satisfy a two-item request.
   */
  private Set<Long> findMatchesContainingAllItems(Long playerId, Set<Long> itemIds) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<ItemDomain> itemRoot = query.from(ItemDomain.class);

    query.select(itemRoot.get(ItemDomain_.matchId));
    query.where(
        cb.equal(itemRoot.get(ItemDomain_.playerId), playerId),
        cb.equal(itemRoot.get(ItemDomain_.isNeutral), false),
        itemRoot.get(ItemDomain_.itemId).in(itemIds));
    query.groupBy(itemRoot.get(ItemDomain_.matchId));
    query.having(
        cb.equal(cb.countDistinct(itemRoot.get(ItemDomain_.itemId)), Long.valueOf(itemIds.size())));

    return new LinkedHashSet<>(entityManager.createQuery(query).getResultList());
  }

  /**
   * Match IDs where the player used every one of {@code abilityIds}.
   *
   * <p>{@code countDistinct(abilityId)} rather than {@code count(abilityId)} keeps the conjunction
   * honest even if the primary key ever widens to allow more than one row per ability per game.
   */
  private Set<Long> findMatchesContainingAllAbilities(Long playerId, Set<Long> abilityIds) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<AbilityDomain> abilityRoot = query.from(AbilityDomain.class);

    query.select(abilityRoot.get(AbilityDomain_.matchId));
    query.where(
        cb.equal(abilityRoot.get(AbilityDomain_.playerId), playerId),
        abilityRoot.get(AbilityDomain_.abilityId).in(abilityIds));
    query.groupBy(abilityRoot.get(AbilityDomain_.matchId));
    query.having(
        cb.equal(
            cb.countDistinct(abilityRoot.get(AbilityDomain_.abilityId)),
            Long.valueOf(abilityIds.size())));

    return new LinkedHashSet<>(entityManager.createQuery(query).getResultList());
  }

  /** Narrows candidate match IDs by match date, hero and game mode. */
  private Set<Long> applyMatchFilters(
      Long playerId,
      Set<Long> candidateMatchIds,
      Set<Long> heroIds,
      Set<Long> gameModeIds,
      LocalDate startDate,
      LocalDate endDate) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<PlayerMatchStatisticDomain> statsRoot = query.from(PlayerMatchStatisticDomain.class);
    Root<MatchDomain> matchRoot = query.from(MatchDomain.class);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(cb.equal(statsRoot.get(PlayerMatchStatisticDomain_.playerId), playerId));
    predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.matchId).in(candidateMatchIds));
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
    if (heroIds != null && !heroIds.isEmpty()) {
      predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.heroId).in(heroIds));
    }
    if (gameModeIds != null && !gameModeIds.isEmpty()) {
      predicates.add(matchRoot.get(MatchDomain_.gameModeId).in(gameModeIds));
    }

    query.select(statsRoot.get(PlayerMatchStatisticDomain_.matchId)).distinct(true);
    query.where(predicates.toArray(new Predicate[0]));

    return new LinkedHashSet<>(entityManager.createQuery(query).getResultList());
  }

  /** Gets the total number of matches for a player within the date range and hero filter. */
  private Long getTotalMatchCount(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<Long> heroIds,
      Set<Long> gameModeIds) {
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
    // Must mirror the main query's hero filter. Filtering only the numerator would
    // divide hero-specific pick counts by the player's games across ALL heroes and
    // report pick rates several times too low, with no error. The same applies to
    // the game mode filter.
    if (heroIds != null && !heroIds.isEmpty()) {
      predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.heroId).in(heroIds));
    }
    if (gameModeIds != null && !gameModeIds.isEmpty()) {
      predicates.add(matchRoot.get(MatchDomain_.gameModeId).in(gameModeIds));
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
    Double avgUseCount = tuple.get("avgUseCount", Double.class);

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
        .avgUseCount(
            avgUseCount != null
                ? BigDecimal.valueOf(avgUseCount).setScale(2, RoundingMode.HALF_UP)
                : null)
        .build();
  }
}
