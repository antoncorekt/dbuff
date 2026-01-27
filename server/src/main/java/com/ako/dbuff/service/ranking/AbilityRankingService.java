package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.repo.AbilityRankingRepository;
import com.ako.dbuff.resources.model.AbilityRankingResponse;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
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
 * Service for calculating ability rankings per player. Provides statistics about ability usage
 * including pick rate and win rate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityRankingService {

  private static final int DEFAULT_LIMIT = 10;

  private final AbilityRankingRepository abilityRankingRepository;
  private final ConstantsManagers constantsManagers;

  /**
   * Gets ability rankings for a specific player.
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive). If null, includes all history.
   * @param endDate Optional end date filter (inclusive). If null, uses current date.
   * @param abilityNames Optional set of ability names to include. If null, returns top abilities by
   *     pick count.
   * @param excludedAbilityNames Optional set of ability names to exclude from results.
   * @param limit Maximum number of abilities to return. Defaults to 10 if null.
   * @return List of AbilityRankingResponse ordered by pick count descending
   */
  @Transactional(readOnly = true)
  public List<AbilityRankingResponse> getAbilityRankings(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<String> abilityNames,
      Set<String> excludedAbilityNames,
      Integer limit) {

    log.info(
        "Fetching ability rankings for player {} with startDate={}, endDate={}, abilities={}, excludedAbilities={}, limit={}",
        playerId,
        startDate,
        endDate,
        abilityNames,
        excludedAbilityNames,
        limit);

    // Use current date as default end date if not specified
    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

    // Use default limit if not specified
    int effectiveLimit = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;

    // Convert ability names to IDs using ConstantsManagers
    Set<Long> abilityIds = convertNamesToIds(abilityNames);
    Set<Long> excludedAbilityIds = convertNamesToIds(excludedAbilityNames);

    log.debug(
        "Converted ability names to IDs: abilities={} -> {}, excludedAbilities={} -> {}",
        abilityNames,
        abilityIds,
        excludedAbilityNames,
        excludedAbilityIds);

    List<AbilityRankingResponse> rankings =
        abilityRankingRepository.findAbilityRankingsByPlayer(
            playerId, startDate, effectiveEndDate, abilityIds, excludedAbilityIds, effectiveLimit);

    log.info("Found {} ability rankings for player {}", rankings.size(), playerId);

    return rankings;
  }

  /**
   * Converts a set of ability names to their corresponding IDs using the ability constant map.
   *
   * <p>The ability constant map is keyed by ability ID (as String), so we need to search by name.
   *
   * @param names Set of ability names
   * @return Set of ability IDs, or null if input is null or empty
   */
  private Set<Long> convertNamesToIds(Set<String> names) {
    if (names == null || names.isEmpty()) {
      return null;
    }

    // getAbilityConstantMap() returns Map<String, AbilityIdsConstant> where key is ability ID
    // We need to find by name
    Map<String, AbilityIdsConstant> abilityConstantMap = constantsManagers.getAbilityConstantMap();

    // Build a reverse map: name -> id
    Map<String, Long> nameToIdMap =
        abilityConstantMap.values().stream()
            .filter(ability -> ability.getName() != null)
            .collect(
                Collectors.toMap(
                    AbilityIdsConstant::getName,
                    AbilityIdsConstant::getId,
                    (existing, replacement) -> existing));

    Set<Long> ids =
        names.stream()
            .map(
                name -> {
                  Long id = nameToIdMap.get(name);
                  if (id == null) {
                    log.warn("Unknown ability name: {}", name);
                    return null;
                  }
                  return id;
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    return ids.isEmpty() ? null : ids;
  }
}
