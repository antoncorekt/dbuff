package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ExternalPlayerStatisticRepository;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse.HistoryEntry;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse.MatchStats;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse.WinLoseStat;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that computes the history of games in which a focus group of players played WITH (as
 * teammates) or AGAINST an external player identified by name.
 *
 * <p>Win/lose counts are computed from the focus group's perspective; the per-match hero and skills
 * describe the external player.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalPlayerStatisticService {

  private static final String DOTABUFF_URL_TEMPLATE = "https://www.dotabuff.com/matches/%d/builds";

  /** Upper bound on how many players a single name pattern may resolve to. */
  private static final int MAX_PATTERN_MATCHES = 10;

  private final PlayerRepo playerRepo;
  private final AbilityRepo abilityRepo;
  private final ExternalPlayerStatisticRepository externalPlayerStatisticRepository;
  private final DbufInstanceConfigService instanceConfigService;

  /**
   * Computes external player statistics using the focus group configured on the given instance.
   *
   * @param instanceId the instance configuration ID
   * @param playerName the external player's name to search for
   * @return statistics for the external player. Returns an empty response (null player ID, empty
   *     history) if the instance or the player is not found.
   */
  @Transactional(readOnly = true)
  public ExternalPlayerStatisticResponse getStatisticsForInstance(
      String instanceId, String playerName) {
    Optional<DbufInstanceConfigDomain> instanceOpt =
        instanceConfigService.getDomainById(instanceId);
    if (instanceOpt.isEmpty()) {
      log.warn("Instance not found: {}", instanceId);
      return emptyResponse(playerName);
    }
    return getStatistics(instanceOpt.get().getPlayerIds(), playerName);
  }

  /**
   * Computes external player statistics for the given focus group.
   *
   * @param focusPlayerIds the focus group account IDs
   * @param playerName the external player's name to search for
   * @return statistics for the external player. Returns an empty response (null player ID, empty
   *     history) if the player is not found.
   */
  @Transactional(readOnly = true)
  public ExternalPlayerStatisticResponse getStatistics(
      Collection<Long> focusPlayerIds, String playerName) {

    log.info(
        "Computing external player statistics for '{}' with {} focus players",
        playerName,
        focusPlayerIds == null ? 0 : focusPlayerIds.size());

    // Resolve external player by name. Empty response if not found.
    Optional<PlayerDomain> playerOpt = playerRepo.findByName(playerName);
    if (playerOpt.isEmpty()) {
      log.info("External player '{}' not found", playerName);
      return emptyResponse(playerName);
    }
    return getStatisticsForPlayer(focusPlayerIds, playerOpt.get());
  }

  /**
   * Computes statistics for every player whose name matches the given case-insensitive regular
   * expression (see {@link PlayerRepo#findByNameMatchingRegex}). A pattern may match more than one
   * player, so this returns a list — e.g. {@code .*MIT} or {@code termit} both match "TERMIT".
   *
   * @param focusPlayerIds the focus group account IDs
   * @param namePattern the case-insensitive regex to match player names against
   * @return statistics for each matching player (empty if the pattern is blank/invalid or no player
   *     matches), capped at {@value #MAX_PATTERN_MATCHES} players
   */
  @Transactional(readOnly = true)
  public List<ExternalPlayerStatisticResponse> getStatisticsByNamePattern(
      Collection<Long> focusPlayerIds, String namePattern) {
    if (namePattern == null || namePattern.isBlank()) {
      return List.of();
    }
    // Validate as a regex up front so a bad pattern is a no-op rather than a DB error.
    try {
      Pattern.compile(namePattern);
    } catch (PatternSyntaxException e) {
      log.info("Invalid name pattern '{}': {}", namePattern, e.getMessage());
      return List.of();
    }

    List<PlayerDomain> matches;
    try {
      matches = playerRepo.findByNameMatchingRegex(namePattern);
    } catch (RuntimeException e) {
      log.warn("Name pattern search failed for '{}': {}", namePattern, e.getMessage());
      return List.of();
    }

    log.info("Pattern '{}' matched {} players", namePattern, matches.size());
    if (matches.size() > MAX_PATTERN_MATCHES) {
      log.warn(
          "Pattern '{}' matched {} players; limiting to {}",
          namePattern,
          matches.size(),
          MAX_PATTERN_MATCHES);
    }

    return matches.stream()
        .limit(MAX_PATTERN_MATCHES)
        .map(player -> getStatisticsForPlayer(focusPlayerIds, player))
        .toList();
  }

  /**
   * Computes statistics for every player matching the given name pattern, using the focus group
   * configured on the given instance.
   *
   * @param instanceId the instance configuration ID
   * @param namePattern the case-insensitive regex to match player names against
   * @return statistics for each matching player; an empty list if the instance is not found
   */
  @Transactional(readOnly = true)
  public List<ExternalPlayerStatisticResponse> getStatisticsByNamePatternForInstance(
      String instanceId, String namePattern) {
    Optional<DbufInstanceConfigDomain> instanceOpt =
        instanceConfigService.getDomainById(instanceId);
    if (instanceOpt.isEmpty()) {
      log.warn("Instance not found: {}", instanceId);
      return List.of();
    }
    return getStatisticsByNamePattern(instanceOpt.get().getPlayerIds(), namePattern);
  }

  /** Computes statistics for a single, already-resolved external player. */
  private ExternalPlayerStatisticResponse getStatisticsForPlayer(
      Collection<Long> focusPlayerIds, PlayerDomain player) {
    Long externalPlayerId = player.getId();
    String playerName = player.getName();

    if (focusPlayerIds == null || focusPlayerIds.isEmpty()) {
      log.info("No focus players provided for '{}'", playerName);
      return emptyResponse(playerName, externalPlayerId);
    }

    // Fetch (match, focus player) pairings with the external player's hero and team.
    List<Object[]> rows =
        externalPlayerStatisticRepository.findFocusVsExternalRows(focusPlayerIds, externalPlayerId);

    if (rows.isEmpty()) {
      return emptyResponse(playerName, externalPlayerId);
    }

    return aggregate(playerName, externalPlayerId, rows);
  }

  /**
   * Aggregates the raw repository rows into the response structure.
   *
   * <p>Rows are deduplicated to exactly one history entry per match: because every focus-group
   * player always plays on the same team, all rows for a given match share the same
   * teammate/against relationship and win/loss outcome relative to the external player, so the
   * first row seen for each match is fully representative. Win/lose counts are therefore
   * incremented once per unique match, keeping them consistent with the deduplicated history.
   */
  private ExternalPlayerStatisticResponse aggregate(
      String playerName, Long externalPlayerId, List<Object[]> rows) {

    // Cache abilities per match so we only query each match once.
    Map<Long, List<AbilityDomain>> abilitiesByMatch = new HashMap<>();

    // Track which matches were already collapsed into a history entry.
    Set<Long> seenMatchIds = new HashSet<>();

    long againstWin = 0;
    long againstLose = 0;
    long teammateWin = 0;
    long teammateLose = 0;
    List<HistoryEntry> history = new ArrayList<>();

    for (Object[] row : rows) {
      Long matchId = (Long) row[0];

      // Dedup: one history entry (and one stat increment) per unique match.
      if (!seenMatchIds.add(matchId)) {
        continue;
      }

      Long win = (Long) row[1];
      Boolean focusRadiant = (Boolean) row[2];
      Boolean externalRadiant = (Boolean) row[3];
      String externalHero = (String) row[4];
      Long externalSlot = (Long) row[5];
      LocalDate matchDate = (LocalDate) row[6];

      boolean focusWon = win != null && win == 1L;
      boolean teammate = focusRadiant != null && focusRadiant.equals(externalRadiant);
      boolean against = !teammate;

      if (teammate) {
        if (focusWon) {
          teammateWin++;
        } else {
          teammateLose++;
        }
      } else {
        if (focusWon) {
          againstWin++;
        } else {
          againstLose++;
        }
      }

      List<String> skills = getExternalPlayerSkills(abilitiesByMatch, matchId, externalSlot);

      history.add(
          HistoryEntry.builder()
              .matchId(matchId)
              .matchDate(matchDate)
              .dotabuffLink(String.format(DOTABUFF_URL_TEMPLATE, matchId))
              .against(against)
              .teammate(teammate)
              .playerWon(focusWon)
              .matchStats(
                  MatchStats.builder().playerHero(externalHero).playerSkills(skills).build())
              .build());
    }

    // Most recent matches first. The query already orders by date desc, but sort defensively
    // (nulls last) to guarantee the contract regardless of DB null-ordering.
    history.sort(
        Comparator.comparing(
            HistoryEntry::getMatchDate, Comparator.nullsLast(Comparator.reverseOrder())));

    return ExternalPlayerStatisticResponse.builder()
        .playerName(playerName)
        .playerId(externalPlayerId)
        .againstStat(WinLoseStat.builder().win(againstWin).lose(againstLose).build())
        .teammateStat(WinLoseStat.builder().win(teammateWin).lose(teammateLose).build())
        .history(history)
        .build();
  }

  /** Resolves the external player's ability pretty names for a given match slot. */
  private List<String> getExternalPlayerSkills(
      Map<Long, List<AbilityDomain>> abilitiesByMatch, Long matchId, Long externalSlot) {
    List<AbilityDomain> matchAbilities =
        abilitiesByMatch.computeIfAbsent(matchId, abilityRepo::findAllByMatchId);
    return matchAbilities.stream()
        .filter(ability -> externalSlot != null && externalSlot.equals(ability.getPlayerSlot()))
        .map(AbilityDomain::getPrettyName)
        .toList();
  }

  private ExternalPlayerStatisticResponse emptyResponse(String playerName) {
    return emptyResponse(playerName, null);
  }

  private ExternalPlayerStatisticResponse emptyResponse(String playerName, Long externalPlayerId) {
    return ExternalPlayerStatisticResponse.builder()
        .playerName(playerName)
        .playerId(externalPlayerId)
        .againstStat(WinLoseStat.builder().win(0).lose(0).build())
        .teammateStat(WinLoseStat.builder().win(0).lose(0).build())
        .history(List.of())
        .build();
  }
}
