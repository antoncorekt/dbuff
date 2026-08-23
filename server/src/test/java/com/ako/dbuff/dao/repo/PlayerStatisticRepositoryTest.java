package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.resources.model.MatchReference;
import com.ako.dbuff.resources.model.PlayerStatisticResponse;
import com.ako.dbuff.resources.model.PlayerStatisticResponse.HeroStatistic;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PlayerStatisticRepository. Uses H2 in-memory database to verify SQL queries
 * are built correctly.
 */
@DataJpaTest
@Import(PlayerStatisticRepository.class)
@ActiveProfiles("test")
class PlayerStatisticRepositoryTest {

  @Autowired private EntityManager entityManager;

  @Autowired private PlayerStatisticRepository playerStatisticRepository;

  private static final Long PLAYER_ID = 123456L;
  private static final String PLAYER_NAME = "TestPlayer";

  // Hero IDs
  private static final Long ANTI_MAGE_ID = 1L;
  private static final Long PUDGE_ID = 2L;
  private static final Long INVOKER_ID = 3L;

  @BeforeEach
  void setUp() {
    // Create test player
    PlayerDomain player = PlayerDomain.builder().id(PLAYER_ID).name(PLAYER_NAME).build();
    entityManager.persist(player);

    // Create matches with different dates
    createMatch(1L, LocalDate.of(2024, 1, 15), true); // Radiant win
    createMatch(2L, LocalDate.of(2024, 2, 10), false); // Dire win
    createMatch(3L, LocalDate.of(2024, 3, 5), true); // Radiant win
    createMatch(4L, LocalDate.of(2024, 4, 20), false); // Dire win
    createMatch(5L, LocalDate.of(2024, 5, 25), true); // Radiant win

    // Create player stats for each match (player on Radiant side - slot 0)
    // Match 1: Win, Anti-Mage
    createPlayerStats(
        1L,
        0L,
        1L,
        ANTI_MAGE_ID,
        "antimage",
        "Anti-Mage",
        5L, // obsPlaced
        3L, // senPlaced
        2L, // creepsStacked
        300L, // lastHits
        50L, // denies
        4L, // campsStacked
        10L, // runePickups
        2L, // towerKills
        1L, // roshanKills
        new BigDecimal("8.5"), // kda
        15L, // neutralKills
        0L, // courierKills
        new BigDecimal("0.85"), // laneEfficiency
        650L, // goldPerMin
        700L // xpPerMin
        );

    // Match 2: Loss, Anti-Mage
    createPlayerStats(
        2L,
        0L,
        0L,
        ANTI_MAGE_ID,
        "antimage",
        "Anti-Mage",
        3L, // obsPlaced
        2L, // senPlaced
        1L, // creepsStacked
        200L, // lastHits
        30L, // denies
        2L, // campsStacked
        5L, // runePickups
        0L, // towerKills
        0L, // roshanKills
        new BigDecimal("2.5"), // kda
        10L, // neutralKills
        0L, // courierKills
        new BigDecimal("0.65"), // laneEfficiency
        450L, // goldPerMin
        500L // xpPerMin
        );

    // Match 3: Win, Pudge
    createPlayerStats(
        3L,
        0L,
        1L,
        PUDGE_ID,
        "pudge",
        "Pudge",
        8L, // obsPlaced
        5L, // senPlaced
        0L, // creepsStacked
        100L, // lastHits
        20L, // denies
        1L, // campsStacked
        8L, // runePickups
        1L, // towerKills
        0L, // roshanKills
        new BigDecimal("12.0"), // kda
        5L, // neutralKills
        2L, // courierKills
        new BigDecimal("0.55"), // laneEfficiency
        400L, // goldPerMin
        550L // xpPerMin
        );

    // Match 4: Loss, Pudge
    createPlayerStats(
        4L,
        0L,
        0L,
        PUDGE_ID,
        "pudge",
        "Pudge",
        6L, // obsPlaced
        4L, // senPlaced
        0L, // creepsStacked
        80L, // lastHits
        15L, // denies
        0L, // campsStacked
        6L, // runePickups
        0L, // towerKills
        0L, // roshanKills
        new BigDecimal("4.0"), // kda
        3L, // neutralKills
        1L, // courierKills
        new BigDecimal("0.45"), // laneEfficiency
        350L, // goldPerMin
        450L // xpPerMin
        );

    // Match 5: Win, Invoker
    createPlayerStats(
        5L,
        0L,
        1L,
        INVOKER_ID,
        "invoker",
        "Invoker",
        4L, // obsPlaced
        2L, // senPlaced
        3L, // creepsStacked
        250L, // lastHits
        40L, // denies
        3L, // campsStacked
        12L, // runePickups
        3L, // towerKills
        1L, // roshanKills
        new BigDecimal("15.0"), // kda
        20L, // neutralKills
        0L, // courierKills
        new BigDecimal("0.75"), // laneEfficiency
        550L, // goldPerMin
        650L // xpPerMin
        );

    entityManager.flush();
    entityManager.clear();
  }

  private void createMatch(Long matchId, LocalDate date, boolean radiantWin) {
    MatchDomain match =
        MatchDomain.builder()
            .id(matchId)
            .startLocalDate(date)
            .startMonth(date.getMonthValue())
            .startYear(date.getYear())
            .radiantWin(radiantWin)
            .build();
    entityManager.persist(match);
  }

  private void createPlayerStats(
      Long matchId,
      Long playerSlot,
      Long win,
      Long heroId,
      String heroName,
      String heroPrettyName,
      Long obsPlaced,
      Long senPlaced,
      Long creepsStacked,
      Long lastHits,
      Long denies,
      Long campsStacked,
      Long runePickups,
      Long towerKills,
      Long roshanKills,
      BigDecimal kda,
      Long neutralKills,
      Long courierKills,
      BigDecimal laneEfficiency,
      Long goldPerMin,
      Long xpPerMin) {
    PlayerMatchStatisticDomain stats =
        PlayerMatchStatisticDomain.builder()
            .matchId(matchId)
            .playerSlot(playerSlot)
            .playerId(PLAYER_ID)
            .win(win)
            .heroId(heroId)
            .heroName(heroName)
            .heroPrettyName(heroPrettyName)
            .obsPlaced(obsPlaced)
            .senPlaced(senPlaced)
            .creepsStacked(creepsStacked)
            .lastHits(lastHits)
            .denies(denies)
            .campsStacked(campsStacked)
            .runePickups(runePickups)
            .towerKills(towerKills)
            .roshanKills(roshanKills)
            .kda(kda)
            .neutralKills(neutralKills)
            .courierKills(courierKills)
            .laneEfficiency(laneEfficiency)
            .goldPerMin(goldPerMin)
            .xpPerMin(xpPerMin)
            .build();
    entityManager.persist(stats);
  }

  @Nested
  @DisplayName("Basic Query Tests")
  class BasicQueryTests {

    @Test
    @DisplayName("Should return player statistics with correct player info")
    void shouldReturnPlayerStatisticsWithPlayerInfo() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      assertThat(result.getPlayerId()).isEqualTo(PLAYER_ID);
      assertThat(result.getPlayerName()).isEqualTo(PLAYER_NAME);
    }

    @Test
    @DisplayName("Should return correct total match count")
    void shouldReturnCorrectTotalMatchCount() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      assertThat(result.getTotalMatches()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Should return popular heroes ordered by pick count")
    void shouldReturnPopularHeroesOrderedByPickCount() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      assertThat(result.getPopularHeroes()).hasSize(3);
      // Anti-Mage: 2 picks, Pudge: 2 picks, Invoker: 1 pick
      // Order may vary for ties, but Invoker should be last
      assertThat(result.getPopularHeroes().get(2).getHeroId()).isEqualTo(INVOKER_ID);
    }

    @Test
    @DisplayName("Should calculate hero win rate correctly")
    void shouldCalculateHeroWinRateCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      // Anti-Mage: 1 win out of 2 = 50%
      HeroStatistic antiMage =
          result.getPopularHeroes().stream()
              .filter(h -> h.getHeroId().equals(ANTI_MAGE_ID))
              .findFirst()
              .orElseThrow();
      assertThat(antiMage.getWinRate()).isEqualByComparingTo(new BigDecimal("50.00"));

      // Invoker: 1 win out of 1 = 100%
      HeroStatistic invoker =
          result.getPopularHeroes().stream()
              .filter(h -> h.getHeroId().equals(INVOKER_ID))
              .findFirst()
              .orElseThrow();
      assertThat(invoker.getWinRate()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should respect hero limit parameter")
    void shouldRespectHeroLimitParameter() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 2, null, null);

      assertThat(result.getPopularHeroes()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("Aggregation Tests")
  class AggregationTests {

    @Test
    @DisplayName("Should calculate average obs placed correctly")
    void shouldCalculateAvgObsPlacedCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      // (5 + 3 + 8 + 6 + 4) / 5 = 5.2
      assertThat(result.getAvgObsPlaced()).isEqualByComparingTo(new BigDecimal("5.20"));
    }

    @Test
    @DisplayName("Should calculate average sen placed correctly")
    void shouldCalculateAvgSenPlacedCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      // (3 + 2 + 5 + 4 + 2) / 5 = 3.2
      assertThat(result.getAvgSenPlaced()).isEqualByComparingTo(new BigDecimal("3.20"));
    }

    @Test
    @DisplayName("Should calculate last hits statistics correctly")
    void shouldCalculateLastHitsStatisticsCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      // Avg: (300 + 200 + 100 + 80 + 250) / 5 = 186
      assertThat(result.getAvgLastHits()).isEqualByComparingTo(new BigDecimal("186.00"));
      // Max: 300
      assertThat(result.getMaxLastHits()).isEqualTo(300L);
      // Min: 80
      assertThat(result.getMinLastHits()).isEqualTo(80L);
    }

    @Test
    @DisplayName("Should calculate KDA statistics correctly")
    void shouldCalculateKdaStatisticsCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      // Avg: (8.5 + 2.5 + 12.0 + 4.0 + 15.0) / 5 = 8.4
      assertThat(result.getAvgKda()).isEqualByComparingTo(new BigDecimal("8.40"));
      // Max: 15.0
      assertThat(result.getMaxKda()).isEqualByComparingTo(new BigDecimal("15.00"));
      // Min: 2.5
      assertThat(result.getMinKda()).isEqualByComparingTo(new BigDecimal("2.50"));
    }

    @Test
    @DisplayName("Should calculate gold per min statistics correctly")
    void shouldCalculateGoldPerMinStatisticsCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      // Avg: (650 + 450 + 400 + 350 + 550) / 5 = 480
      assertThat(result.getAvgGoldPerMin()).isEqualByComparingTo(new BigDecimal("480.00"));
      // Max: 650
      assertThat(result.getMaxGoldPerMin()).isEqualTo(650L);
      // Min: 350
      assertThat(result.getMinGoldPerMin()).isEqualTo(350L);
    }

    @Test
    @DisplayName("Should calculate win/loss statistics correctly")
    void shouldCalculateWinLossStatisticsCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      // 3 wins, 2 losses
      assertThat(result.getWins()).isEqualTo(3L);
      assertThat(result.getLosses()).isEqualTo(2L);
      // Win rate: 3/5 = 60%
      assertThat(result.getAvgWinRate()).isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("Should calculate objective statistics correctly")
    void shouldCalculateObjectiveStatisticsCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      // Avg tower kills: (2 + 0 + 1 + 0 + 3) / 5 = 1.2
      assertThat(result.getAvgTowerKills()).isEqualByComparingTo(new BigDecimal("1.20"));
      // Avg roshan kills: (1 + 0 + 0 + 0 + 1) / 5 = 0.4
      assertThat(result.getAvgRoshanKills()).isEqualByComparingTo(new BigDecimal("0.40"));
    }

    @Test
    @DisplayName("Should calculate farming statistics correctly")
    void shouldCalculateFarmingStatisticsCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      // Avg denies: (50 + 30 + 20 + 15 + 40) / 5 = 31
      assertThat(result.getAvgDenies()).isEqualByComparingTo(new BigDecimal("31.00"));
      // Avg camps stacked: (4 + 2 + 1 + 0 + 3) / 5 = 2
      assertThat(result.getAvgCampsStacked()).isEqualByComparingTo(new BigDecimal("2.00"));
      // Avg rune pickups: (10 + 5 + 8 + 6 + 12) / 5 = 8.2
      assertThat(result.getAvgRunePickups()).isEqualByComparingTo(new BigDecimal("8.20"));
    }
  }

  @Nested
  @DisplayName("Date Filter Tests")
  class DateFilterTests {

    @Test
    @DisplayName("Should filter by start date")
    void shouldFilterByStartDate() {
      // Only matches from March onwards (matches 3, 4, 5)
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, LocalDate.of(2024, 3, 1), null, 3, null, null);

      assertThat(result.getTotalMatches()).isEqualTo(3L);
      assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2024, 3, 1));
    }

    @Test
    @DisplayName("Should filter by end date")
    void shouldFilterByEndDate() {
      // Only matches until February (matches 1, 2)
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, LocalDate.of(2024, 2, 28), 3, null, null);

      assertThat(result.getTotalMatches()).isEqualTo(2L);
      assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2024, 2, 28));
    }

    @Test
    @DisplayName("Should filter by date range")
    void shouldFilterByDateRange() {
      // Only matches in February and March (matches 2, 3)
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 31), 3, null, null);

      assertThat(result.getTotalMatches()).isEqualTo(2L);
      // 1 win (match 3), 1 loss (match 2)
      assertThat(result.getWins()).isEqualTo(1L);
      assertThat(result.getLosses()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should calculate statistics correctly for filtered date range")
    void shouldCalculateStatisticsCorrectlyForFilteredDateRange() {
      // Only matches in February and March (matches 2, 3)
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 31), 3, null, null);

      // Avg last hits: (200 + 100) / 2 = 150
      assertThat(result.getAvgLastHits()).isEqualByComparingTo(new BigDecimal("150.00"));
      // Max last hits: 200
      assertThat(result.getMaxLastHits()).isEqualTo(200L);
      // Min last hits: 100
      assertThat(result.getMinLastHits()).isEqualTo(100L);
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should return empty response for non-existent player")
    void shouldReturnEmptyResponseForNonExistentPlayer() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(999999L, null, null, 3, null, null);

      assertThat(result.getPlayerId()).isEqualTo(999999L);
      assertThat(result.getTotalMatches()).isEqualTo(0L);
      assertThat(result.getPopularHeroes()).isEmpty();
    }

    @Test
    @DisplayName("Should return empty response when no matches in date range")
    void shouldReturnEmptyResponseWhenNoMatchesInRange() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 3, null, null);

      assertThat(result.getTotalMatches()).isEqualTo(0L);
      assertThat(result.getPopularHeroes()).isEmpty();
    }

    @Test
    @DisplayName("Should handle hero limit of 1")
    void shouldHandleHeroLimitOfOne() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 1, null, null);

      assertThat(result.getPopularHeroes()).hasSize(1);
      // Should be either Anti-Mage or Pudge (both have 2 picks)
      assertThat(result.getPopularHeroes().get(0).getPickCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should handle hero limit larger than available heroes")
    void shouldHandleHeroLimitLargerThanAvailable() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 10, null, null);

      // Only 3 unique heroes in test data
      assertThat(result.getPopularHeroes()).hasSize(3);
    }
  }

  @Nested
  @DisplayName("Hero Filter Tests")
  class HeroFilterTests {

    @Test
    @DisplayName("Should count only the filtered hero's matches")
    void shouldCountOnlyFilteredHeroMatches() {
      // Anti-Mage games are matches 1 (win) and 2 (loss).
      PlayerStatisticResponse antiMage =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, null, 3, Set.of(ANTI_MAGE_ID), null);

      assertThat(antiMage.getTotalMatches()).isEqualTo(2L);
      assertThat(antiMage.getWins()).isEqualTo(1L);
      assertThat(antiMage.getLosses()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Win rate must be computed over the filtered hero's games only")
    void winRateIsComputedOverFilteredGames() {
      // Invoker: one game, won -> 100%.
      PlayerStatisticResponse invoker =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, null, 3, Set.of(INVOKER_ID), null);

      assertThat(invoker.getTotalMatches()).isEqualTo(1L);
      assertThat(invoker.getAvgWinRate()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should set heroFiltered when a hero filter is applied")
    void shouldSetHeroFilteredFlag() {
      PlayerStatisticResponse filtered =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, null, 3, Set.of(ANTI_MAGE_ID), null);

      assertThat(filtered.getHeroFiltered()).isTrue();
    }

    @Test
    @DisplayName("Should leave heroFiltered false when no hero filter is applied")
    void shouldLeaveHeroFilteredFalse() {
      PlayerStatisticResponse unfiltered =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      assertThat(unfiltered.getHeroFiltered()).isFalse();
    }

    @Test
    @DisplayName("Popular heroes should degenerate to the single filtered hero")
    void popularHeroesDegeneratesToOneEntry() {
      PlayerStatisticResponse antiMage =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, null, 3, Set.of(ANTI_MAGE_ID), null);

      // This is why heroFiltered exists: the section is meaningless when filtered.
      assertThat(antiMage.getPopularHeroes()).hasSize(1);
      assertThat(antiMage.getPopularHeroes().get(0).getHeroId()).isEqualTo(ANTI_MAGE_ID);
    }

    @Test
    @DisplayName("Should return zero matches when the hero filter matches no games")
    void heroFilterMatchingNoGamesReturnsZero() {
      PlayerStatisticResponse none =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, null, 3, Set.of(999L), null);

      assertThat(none.getTotalMatches()).isZero();
      assertThat(none.getPopularHeroes()).isEmpty();
      assertThat(none.getHeroFiltered()).isTrue();
    }

    @Test
    @DisplayName("Empty hero filter set should be treated as no filter")
    void emptyHeroFilterSetIsNoFilter() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, Set.of(), null);

      assertThat(result.getTotalMatches()).isEqualTo(5L);
      assertThat(result.getHeroFiltered()).isFalse();
    }

    @Test
    @DisplayName("Hero filter should combine with a date filter")
    void heroFilterCombinesWithDateFilter() {
      // Anti-Mage games from February onwards: only match 2.
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, LocalDate.of(2024, 2, 1), null, 3, Set.of(ANTI_MAGE_ID), null);

      assertThat(result.getTotalMatches()).isEqualTo(1L);
      assertThat(result.getWins()).isZero();
    }
  }

  @Nested
  @DisplayName("Game Mode Filter Tests")
  class GameModeFilterTests {

    private static final Long ABILITY_DRAFT = 18L;
    private static final Long ALL_DRAFT = 22L;

    /** Matches 1 and 2 Ability Draft, 3 and 4 All Draft, 5 left with no recorded mode. */
    @BeforeEach
    void assignGameModes() {
      setGameMode(1L, ABILITY_DRAFT);
      setGameMode(2L, ABILITY_DRAFT);
      setGameMode(3L, ALL_DRAFT);
      setGameMode(4L, ALL_DRAFT);
      entityManager.flush();
      entityManager.clear();
    }

    private void setGameMode(Long matchId, Long gameModeId) {
      entityManager.find(MatchDomain.class, matchId).setGameModeId(gameModeId);
    }

    @Test
    @DisplayName("Single game mode should count only that mode's matches")
    void singleGameModeIsCounted() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, null, 3, null, Set.of(ABILITY_DRAFT));

      assertThat(result.getTotalMatches()).isEqualTo(2L);
    }

    @Test
    @DisplayName("A list of game modes should count matches from all of them")
    void severalGameModesAreUnioned() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, null, 3, null, Set.of(ABILITY_DRAFT, ALL_DRAFT));

      assertThat(result.getTotalMatches()).isEqualTo(4L);
    }

    @Test
    @DisplayName("No game mode filter should count every match")
    void nullGameModeFilterCountsEverything() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, null);

      assertThat(result.getTotalMatches()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Empty game mode filter set should be treated as no filter")
    void emptyGameModeFilterSetIsNoFilter() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null, Set.of());

      assertThat(result.getTotalMatches()).isEqualTo(5L);
    }

    /**
     * Match 5 has no recorded mode, and an unknown mode is not Ability Draft. Counting it would put
     * games of unknown provenance into a mode-scoped answer.
     */
    @Test
    @DisplayName("Matches with no recorded game mode are excluded by any filter")
    void matchesWithoutAGameModeAreExcluded() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, null, 3, null, Set.of(ABILITY_DRAFT, ALL_DRAFT));

      assertThat(result.getTotalMatches()).isEqualTo(4L);
      assertThat(result.getWins() + result.getLosses()).isEqualTo(4L);
    }

    /**
     * The aggregate, the match count and the popular-heroes list must all see the same filter, or
     * the embed shows a match count that disagrees with its own win/loss split.
     */
    @Test
    @DisplayName("Game mode filter applies consistently across every sub-query")
    void filterAppliesToTheAggregateAndTheHeroList() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, null, 5, null, Set.of(ABILITY_DRAFT));

      assertThat(result.getTotalMatches()).isEqualTo(2L);
      assertThat(result.getWins() + result.getLosses()).isEqualTo(2L);
      assertThat(result.getPopularHeroes().stream().mapToLong(HeroStatistic::getPickCount).sum())
          .isEqualTo(2L);
    }

    @Test
    @DisplayName("Game mode filter should combine with a date filter")
    void gameModeCombinesWithDateFilter() {
      // Ability Draft games from February onwards: only match 2.
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, LocalDate.of(2024, 2, 1), null, 3, null, Set.of(ABILITY_DRAFT));

      assertThat(result.getTotalMatches()).isEqualTo(1L);
    }

    @Test
    @DisplayName("A mode with no matches should report zero rather than falling back")
    void unmatchedGameModeReportsZero() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, null, 3, null, Set.of(1L));

      assertThat(result.getTotalMatches()).isZero();
      assertThat(result.getPopularHeroes()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Match Trace Tests")
  class MatchTraceTests {

    @Test
    @DisplayName("Should list every match with its date, most recent first")
    void listsMatchesNewestFirst() {
      List<MatchReference> matches =
          playerStatisticRepository.findPlayerMatches(PLAYER_ID, null, null, null, null, null, 50);

      assertThat(matches).hasSize(5);
      assertThat(matches).extracting(MatchReference::matchId).containsExactly(5L, 4L, 3L, 2L, 1L);
      assertThat(matches.get(0).startDate()).isEqualTo(LocalDate.of(2024, 5, 25));
    }

    /**
     * The trace exists to explain a number, so it must be built from the same filters that produced
     * it — a list of games the aggregate did not cover would be worse than no list.
     */
    @Test
    @DisplayName("Should apply the same date and hero filters as the aggregations")
    void appliesTheSameFiltersAsTheStatistics() {
      List<MatchReference> antiMage =
          playerStatisticRepository.findPlayerMatches(
              PLAYER_ID, null, null, Set.of(ANTI_MAGE_ID), null, null, 50);
      assertThat(antiMage).extracting(MatchReference::matchId).containsExactly(2L, 1L);

      List<MatchReference> fromMarch =
          playerStatisticRepository.findPlayerMatches(
              PLAYER_ID, LocalDate.of(2024, 3, 1), null, null, null, null, 50);
      assertThat(fromMarch).extracting(MatchReference::matchId).containsExactly(5L, 4L, 3L);
    }

    @Test
    @DisplayName("Should restrict to an explicit match set when given one")
    void restrictsToTheGivenMatchIds() {
      List<MatchReference> matches =
          playerStatisticRepository.findPlayerMatches(
              PLAYER_ID, null, null, null, null, Set.of(2L, 4L), 50);

      assertThat(matches).extracting(MatchReference::matchId).containsExactly(4L, 2L);
    }

    @Test
    @DisplayName("Should respect the limit, keeping the most recent")
    void respectsTheLimit() {
      List<MatchReference> matches =
          playerStatisticRepository.findPlayerMatches(PLAYER_ID, null, null, null, null, null, 2);

      assertThat(matches).extracting(MatchReference::matchId).containsExactly(5L, 4L);
    }

    @Test
    @DisplayName("Should return nothing for a player with no matches")
    void unknownPlayerHasNoMatches() {
      assertThat(
              playerStatisticRepository.findPlayerMatches(
                  999999L, null, null, null, null, null, 50))
          .isEmpty();
    }
  }
}
