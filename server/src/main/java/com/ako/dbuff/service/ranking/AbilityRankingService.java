package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.repo.AbilityRankingRepository;
import com.ako.dbuff.resources.model.AbilityComboStatisticResponse;
import com.ako.dbuff.resources.model.AbilityRankingResponse;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.GameModeResolver;
import com.ako.dbuff.service.constant.GameModeSelection;
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
  private final GameModeResolver gameModeResolver;

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
   * @param gameModeNames Optional game mode names to restrict the query to. Null or empty includes
   *     every mode.
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
      Set<String> gameModeNames,
      Integer limit) {

    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();
    int effectiveLimit = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;

    Set<Long> abilityIds = resolveAbilitiesOrThrow(abilityNames);
    Set<Long> excludedAbilityIds = resolveAbilitiesOrThrow(excludedAbilityNames);
    Set<Long> heroIds = resolveHeroesOrThrow(heroNames);
    Set<Long> gameModeIds = resolveGameModesOrThrow(gameModeNames);

    log.info(
        "Fetching ability rankings for player {}: startDate={}, endDate={}, abilities={}, excluded={}, heroes={}, gameModes={}, limit={}",
        playerId,
        startDate,
        effectiveEndDate,
        abilityIds,
        excludedAbilityIds,
        heroIds,
        gameModeIds,
        effectiveLimit);

    List<AbilityRankingResponse> rankings =
        abilityRankingRepository.findAbilityRankingsByPlayer(
            playerId,
            startDate,
            effectiveEndDate,
            abilityIds,
            excludedAbilityIds,
            heroIds,
            gameModeIds,
            effectiveLimit);

    log.info("Found {} ability rankings for player {}", rankings.size(), playerId);
    return rankings;
  }

  /**
   * Gets statistics over the games in which the player used every one of the named abilities, and —
   * when items are named too — also held every one of those items in the same game.
   *
   * @param itemNames items that must all be present alongside the abilities; null or empty applies
   *     no item restriction
   * @throws UnknownConstantNameException if any supplied name matches no known constant
   */
  @Transactional(readOnly = true)
  public AbilityComboStatisticResponse getAbilityComboStatistics(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<String> abilityNames,
      Set<String> itemNames,
      Set<String> heroNames,
      Set<String> gameModeNames) {

    Set<Long> abilityIds = resolveAbilitiesOrThrow(abilityNames);
    Set<Long> itemIds = resolveItemsOrThrow(itemNames);
    Set<Long> heroIds = resolveHeroesOrThrow(heroNames);
    Set<Long> gameModeIds = resolveGameModesOrThrow(gameModeNames);
    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

    log.info(
        "Fetching ability combo statistics for player {}: abilities={}, items={}, heroes={}, gameModes={}, {} to {}",
        playerId,
        abilityIds,
        itemIds,
        heroIds,
        gameModeIds,
        startDate,
        effectiveEndDate);

    return abilityRankingRepository.findAbilityComboStatistics(
        playerId, abilityIds, itemIds, heroIds, gameModeIds, startDate, effectiveEndDate);
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

  private Set<Long> resolveItemsOrThrow(Set<String> names) {
    NameResolution resolution = nameResolver.resolveItems(names);
    if (resolution.hasUnresolved()) {
      throw new UnknownConstantNameException("items", resolution.unresolvedNames());
    }
    return resolution.idsOrNullIfEmpty();
  }

  /** Resolves game mode names to IDs, refusing to proceed if any is unknown. */
  private Set<Long> resolveGameModesOrThrow(Set<String> names) {
    GameModeSelection modes = gameModeResolver.resolve(names);
    if (modes.hasUnresolved()) {
      throw new UnknownConstantNameException("game modes", modes.unresolvedNames());
    }
    return modes.idsOrNullIfEmpty();
  }
}
