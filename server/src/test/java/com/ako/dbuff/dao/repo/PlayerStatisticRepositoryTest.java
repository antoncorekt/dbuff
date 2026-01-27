package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.resources.model.PlayerStatisticResponse;
import com.ako.dbuff.resources.model.PlayerStatisticResponse.HeroStatistic;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
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
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3);

      assertThat(result.getPlayerId()).isEqualTo(PLAYER_ID);
      assertThat(result.getPlayerName()).isEqualTo(PLAYER_NAME);
    }

    @Test
    @DisplayName("Should return correct total match count")
    void shouldReturnCorrectTotalMatchCount() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3);

      assertThat(result.getTotalMatches()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Should return popular heroes ordered by pick count")
    void shouldReturnPopularHeroesOrderedByPickCount() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3);

      assertThat(result.getPopularHeroes()).hasSize(3);
      // Anti-Mage: 2 picks, Pudge: 2 picks, Invoker: 1 pick
      // Order may vary for ties, but Invoker should be last
      assertThat(result.getPopularHeroes().get(2).getHeroId()).isEqualTo(INVOKER_ID);
    }

    @Test
    @DisplayName("Should calculate hero win rate correctly")
    void shouldCalculateHeroWinRateCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3);

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
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 2);

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
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3);

      // (5 + 3 + 8 + 6 + 4) / 5 = 5.2
      assertThat(result.getAvgObsPlaced()).isEqualByComparingTo(new BigDecimal("5.20"));
    }

    @Test
    @DisplayName("Should calculate average sen placed correctly")
    void shouldCalculateAvgSenPlacedCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3);

      // (3 + 2 + 5 + 4 + 2) / 5 = 3.2
      assertThat(result.getAvgSenPlaced()).isEqualByComparingTo(new BigDecimal("3.20"));
    }

    @Test
    @DisplayName("Should calculate last hits statistics correctly")
    void shouldCalculateLastHitsStatisticsCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3);

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
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3);

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
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3);

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
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3);

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
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3);

      // Avg tower kills: (2 + 0 + 1 + 0 + 3) / 5 = 1.2
      assertThat(result.getAvgTowerKills()).isEqualByComparingTo(new BigDecimal("1.20"));
      // Avg roshan kills: (1 + 0 + 0 + 0 + 1) / 5 = 0.4
      assertThat(result.getAvgRoshanKills()).isEqualByComparingTo(new BigDecimal("0.40"));
    }

    @Test
    @DisplayName("Should calculate farming statistics correctly")
    void shouldCalculateFarmingStatisticsCorrectly() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3);

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
              PLAYER_ID, LocalDate.of(2024, 3, 1), null, 3);

      assertThat(result.getTotalMatches()).isEqualTo(3L);
      assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2024, 3, 1));
    }

    @Test
    @DisplayName("Should filter by end date")
    void shouldFilterByEndDate() {
      // Only matches until February (matches 1, 2)
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, LocalDate.of(2024, 2, 28), 3);

      assertThat(result.getTotalMatches()).isEqualTo(2L);
      assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2024, 2, 28));
    }

    @Test
    @DisplayName("Should filter by date range")
    void shouldFilterByDateRange() {
      // Only matches in February and March (matches 2, 3)
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 31), 3);

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
              PLAYER_ID, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 31), 3);

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
          playerStatisticRepository.findPlayerStatistics(999999L, null, null, 3);

      assertThat(result.getPlayerId()).isEqualTo(999999L);
      assertThat(result.getTotalMatches()).isEqualTo(0L);
      assertThat(result.getPopularHeroes()).isEmpty();
    }

    @Test
    @DisplayName("Should return empty response when no matches in date range")
    void shouldReturnEmptyResponseWhenNoMatchesInRange() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 3);

      assertThat(result.getTotalMatches()).isEqualTo(0L);
      assertThat(result.getPopularHeroes()).isEmpty();
    }

    @Test
    @DisplayName("Should handle hero limit of 1")
    void shouldHandleHeroLimitOfOne() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 1);

      assertThat(result.getPopularHeroes()).hasSize(1);
      // Should be either Anti-Mage or Pudge (both have 2 picks)
      assertThat(result.getPopularHeroes().get(0).getPickCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should handle hero limit larger than available heroes")
    void shouldHandleHeroLimitLargerThanAvailable() {
      PlayerStatisticResponse result =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 10);

      // Only 3 unique heroes in test data
      assertThat(result.getPopularHeroes()).hasSize(3);
    }
  }
}
