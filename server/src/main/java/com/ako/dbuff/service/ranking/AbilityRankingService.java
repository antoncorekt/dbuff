package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.repo.AbilityRankingRepository;
import com.ako.dbuff.resources.model.AbilityComboStatisticResponse;
import com.ako.dbuff.resources.model.AbilityRankingResponse;
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
 * Service for calculating ability rankings per player. Provides statistics about ability usage
 * including pick rate, win rate, and average uses per game.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityRankingService {

  private static final int DEFAULT_LIMIT = 10;

  private final AbilityRankingRepository abilityRankingRepository;
  private final ConstantNameResolver nameResolver;

  /**
   * Gets ability rankings for a specific player.
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive). If null, includes all history.
   * @param endDate Optional end date filter (inclusive). If null, uses current date.
   * @param abilityNames Optional ability names to include. If null or empty, returns top abilities
   *     by pick count.
   * @param excludedAbilityNames Optional ability names to exclude from results.
   * @param heroNames Optional hero names to restrict the query to.
   * @param limit Maximum number of abilities to return. Defaults to 10 if null.
   * @return List of AbilityRankingResponse ordered by pick count descending
   * @throws UnknownConstantNameException if any supplied name matches no known constant
   */
  @Transactional(readOnly = true)
  public List<AbilityRankingResponse> getAbilityRankings(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<String> abilityNames,
      Set<String> excludedAbilityNames,
      Set<String> heroNames,
      Integer limit) {

    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();
    int effectiveLimit = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;

    Set<Long> abilityIds = resolveAbilitiesOrThrow(abilityNames);
    Set<Long> excludedAbilityIds = resolveAbilitiesOrThrow(excludedAbilityNames);
    Set<Long> heroIds = resolveHeroesOrThrow(heroNames);

    log.info(
        "Fetching ability rankings for player {}: startDate={}, endDate={}, abilities={}, excluded={}, heroes={}, limit={}",
        playerId,
        startDate,
        effectiveEndDate,
        abilityIds,
        excludedAbilityIds,
        heroIds,
        effectiveLimit);

    List<AbilityRankingResponse> rankings =
        abilityRankingRepository.findAbilityRankingsByPlayer(
            playerId,
            startDate,
            effectiveEndDate,
            abilityIds,
            excludedAbilityIds,
            heroIds,
            effectiveLimit);

    log.info("Found {} ability rankings for player {}", rankings.size(), playerId);
    return rankings;
  }

  /**
   * Gets statistics over the games in which the player used every one of the named abilities.
   *
   * @throws UnknownConstantNameException if any supplied name matches no known constant
   */
  @Transactional(readOnly = true)
  public AbilityComboStatisticResponse getAbilityComboStatistics(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<String> abilityNames,
      Set<String> heroNames) {

    Set<Long> abilityIds = resolveAbilitiesOrThrow(abilityNames);
    Set<Long> heroIds = resolveHeroesOrThrow(heroNames);
    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

    log.info(
        "Fetching ability combo statistics for player {}: abilities={}, heroes={}, {} to {}",
        playerId,
        abilityIds,
        heroIds,
        startDate,
        effectiveEndDate);

    return abilityRankingRepository.findAbilityComboStatistics(
        playerId, abilityIds, heroIds, startDate, effectiveEndDate);
  }

  /**
   * Resolves ability names to IDs, refusing to proceed if any name is unknown.
   *
   * <p>Throwing rather than dropping is deliberate. A null ID set means "no filter" to the
   * repository, so silently discarding unknown names turns a filtered query into an unfiltered
   * top-N ranking and answers a different question than the caller asked.
   */
  private Set<Long> resolveAbilitiesOrThrow(Set<String> names) {
    NameResolution resolution = nameResolver.resolveAbilities(names);
    if (resolution.hasUnresolved()) {
      throw new UnknownConstantNameException("abilities", resolution.unresolvedNames());
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
