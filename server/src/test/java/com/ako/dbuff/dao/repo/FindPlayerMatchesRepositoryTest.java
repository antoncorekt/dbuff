package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.resources.model.FindPlayerMatchesResponse;
import com.ako.dbuff.resources.model.FindPlayerMatchesResponse.PlayerMatchStatistic;
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
 * Integration tests for FindPlayerMatchesRepository. Uses H2 in-memory database to verify SQL
 * queries are built correctly.
 */
@DataJpaTest
@Import(FindPlayerMatchesRepository.class)
@ActiveProfiles("test")
class FindPlayerMatchesRepositoryTest {

  @Autowired private EntityManager entityManager;

  @Autowired private FindPlayerMatchesRepository findPlayerMatchesRepository;

  // Test player IDs
  private static final Long PLAYER_ID = 123456L;
  private static final String PLAYER_NAME = "TestPlayer";

  // Default player IDs (simulating PlayerConfiguration.DEFAULT_PLAYERS)
  private static final Long DEFAULT_PLAYER_1_ID = 204429164L;
  private static final String DEFAULT_PLAYER_1_NAME = "DefaultPlayer1";
  private static final Long DEFAULT_PLAYER_2_ID = 279195408L;
  private static final String DEFAULT_PLAYER_2_NAME = "DefaultPlayer2";

  // Hero IDs
  private static final Long ANTI_MAGE_ID = 1L;
  private static final Long PUDGE_ID = 2L;
  private static final Long INVOKER_ID = 3L;

  @BeforeEach
  void setUp() {
    // Create test players
    createPlayer(PLAYER_ID, PLAYER_NAME);
    createPlayer(DEFAULT_PLAYER_1_ID, DEFAULT_PLAYER_1_NAME);
    createPlayer(DEFAULT_PLAYER_2_ID, DEFAULT_PLAYER_2_NAME);

    // Create matches with different dates
    createMatch(1L, LocalDate.of(2024, 1, 15), true); // Oldest
    createMatch(2L, LocalDate.of(2024, 2, 10), false);
    createMatch(3L, LocalDate.of(2024, 3, 5), true);
    createMatch(4L, LocalDate.of(2024, 4, 20), false);
    createMatch(5L, LocalDate.of(2024, 5, 25), true); // Newest

    // Create player stats for each match
    // Match 1: TestPlayer and DefaultPlayer1
    createPlayerStats(
        1L, PLAYER_ID, 0L, ANTI_MAGE_ID, "Anti-Mage", new BigDecimal("8.5"), 1L, 650L);
    createPlayerStats(
        1L, DEFAULT_PLAYER_1_ID, 1L, PUDGE_ID, "Pudge", new BigDecimal("12.0"), 1L, 400L);

    // Match 2: TestPlayer and DefaultPlayer2
    createPlayerStats(2L, PLAYER_ID, 0L, PUDGE_ID, "Pudge", new BigDecimal("2.5"), 0L, 350L);
    createPlayerStats(
        2L, DEFAULT_PLAYER_2_ID, 128L, INVOKER_ID, "Invoker", new BigDecimal("5.0"), 1L, 500L);

    // Match 3: TestPlayer, DefaultPlayer1, and DefaultPlayer2
    createPlayerStats(3L, PLAYER_ID, 0L, INVOKER_ID, "Invoker", new BigDecimal("15.0"), 1L, 600L);
    createPlayerStats(
        3L, DEFAULT_PLAYER_1_ID, 1L, ANTI_MAGE_ID, "Anti-Mage", new BigDecimal("10.0"), 1L, 700L);
    createPlayerStats(
        3L, DEFAULT_PLAYER_2_ID, 2L, PUDGE_ID, "Pudge", new BigDecimal("8.0"), 1L, 380L);

    // Match 4: Only TestPlayer
    createPlayerStats(
        4L, PLAYER_ID, 0L, ANTI_MAGE_ID, "Anti-Mage", new BigDecimal("4.0"), 0L, 450L);

    // Match 5: TestPlayer and DefaultPlayer1
    createPlayerStats(5L, PLAYER_ID, 0L, INVOKER_ID, "Invoker", new BigDecimal("20.0"), 1L, 750L);
    createPlayerStats(
        5L, DEFAULT_PLAYER_1_ID, 1L, PUDGE_ID, "Pudge", new BigDecimal("6.0"), 1L, 350L);

    entityManager.flush();
    entityManager.clear();
  }

  private void createPlayer(Long playerId, String name) {
    PlayerDomain player = PlayerDomain.builder().id(playerId).name(name).build();
    entityManager.persist(player);
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
      Long playerId,
      Long playerSlot,
      Long heroId,
      String heroPrettyName,
      BigDecimal kda,
      Long win,
      Long goldPerMin) {
    PlayerMatchStatisticDomain stats =
        PlayerMatchStatisticDomain.builder()
            .matchId(matchId)
            .playerSlot(playerSlot)
            .playerId(playerId)
            .heroId(heroId)
            .heroName(heroPrettyName.toLowerCase().replace("-", ""))
            .heroPrettyName(heroPrettyName)
            .kda(kda)
            .win(win)
            .goldPerMin(goldPerMin)
            .build();
    entityManager.persist(stats);
  }

  @Nested
  @DisplayName("Basic Query Tests")
  class BasicQueryTests {

    @Test
    @DisplayName("Should return matches ordered by date descending")
    void shouldReturnMatchesOrderedByDateDescending() {
      Set<Long> defaultPlayerIds = Set.of(DEFAULT_PLAYER_1_ID, DEFAULT_PLAYER_2_ID);

      List<FindPlayerMatchesResponse> results =
          findPlayerMatchesRepository.findPlayerMatches(
              PLAYER_ID, PLAYER_NAME, defaultPlayerIds, 10);

      assertThat(results).hasSize(5);
      // Should be ordered by date descending (newest first)
      assertThat(results.get(0).getMatchId()).isEqualTo(5L); // May 25
      assertThat(results.get(1).getMatchId()).isEqualTo(4L); // April 20
      assertThat(results.get(2).getMatchId()).isEqualTo(3L); // March 5
      assertThat(results.get(3).getMatchId()).isEqualTo(2L); // February 10
      assertThat(results.get(4).getMatchId()).isEqualTo(1L); // January 15
    }

    @Test
    @DisplayName("Should include correct match info")
    void shouldIncludeCorrectMatchInfo() {
      Set<Long> defaultPlayerIds = Set.of(DEFAULT_PLAYER_1_ID, DEFAULT_PLAYER_2_ID);

      List<FindPlayerMatchesResponse> results =
          findPlayerMatchesRepository.findPlayerMatches(
              PLAYER_ID, PLAYER_NAME, defaultPlayerIds, 10);

      FindPlayerMatchesResponse match5 = results.get(0);
      assertThat(match5.getMatchId()).isEqualTo(5L);
      assertThat(match5.getMatchDate()).isEqualTo(LocalDate.of(2024, 5, 25));
      assertThat(match5.getDotabuffUrl()).isEqualTo("https://www.dotabuff.com/matches/5/builds");
      assertThat(match5.getPlayerId()).isEqualTo(PLAYER_ID);
      assertThat(match5.getPlayerName()).isEqualTo(PLAYER_NAME);
    }

    @Test
    @DisplayName("Should include statistics for searched player and default players")
    void shouldIncludeStatisticsForSearchedPlayerAndDefaultPlayers() {
      Set<Long> defaultPlayerIds = Set.of(DEFAULT_PLAYER_1_ID, DEFAULT_PLAYER_2_ID);

      List<FindPlayerMatchesResponse> results =
          findPlayerMatchesRepository.findPlayerMatches(
              PLAYER_ID, PLAYER_NAME, defaultPlayerIds, 10);

      // Match 3 has all three players
      FindPlayerMatchesResponse match3 =
          results.stream().filter(r -> r.getMatchId().equals(3L)).findFirst().orElseThrow();

      assertThat(match3.getStatistics()).hasSize(3);
      assertThat(match3.getStatistics())
          .extracting(PlayerMatchStatistic::getPlayerId)
          .containsExactlyInAnyOrder(PLAYER_ID, DEFAULT_PLAYER_1_ID, DEFAULT_PLAYER_2_ID);
    }

    @Test
    @DisplayName("Should include correct player statistics")
    void shouldIncludeCorrectPlayerStatistics() {
      Set<Long> defaultPlayerIds = Set.of(DEFAULT_PLAYER_1_ID);

      List<FindPlayerMatchesResponse> results =
          findPlayerMatchesRepository.findPlayerMatches(
              PLAYER_ID, PLAYER_NAME, defaultPlayerIds, 10);

      // Check match 5 statistics
      FindPlayerMatchesResponse match5 = results.get(0);
      assertThat(match5.getStatistics()).hasSize(2);

      PlayerMatchStatistic testPlayerStat =
          match5.getStatistics().stream()
              .filter(s -> s.getPlayerId().equals(PLAYER_ID))
              .findFirst()
              .orElseThrow();

      assertThat(testPlayerStat.getPlayerName()).isEqualTo(PLAYER_NAME);
      assertThat(testPlayerStat.getPlayerSlot()).isEqualTo(0L);
      assertThat(testPlayerStat.getHeroPrettyName()).isEqualTo("Invoker");
      assertThat(testPlayerStat.getKda()).isEqualByComparingTo(new BigDecimal("20.00"));
      assertThat(testPlayerStat.getWin()).isEqualTo(1L);
      assertThat(testPlayerStat.getGoldPerMin()).isEqualTo(750L);
    }
  }

  @Nested
  @DisplayName("Limit Tests")
  class LimitTests {

    @Test
    @DisplayName("Should respect limit parameter")
    void shouldRespectLimitParameter() {
      Set<Long> defaultPlayerIds = Set.of(DEFAULT_PLAYER_1_ID);

      List<FindPlayerMatchesResponse> results =
          findPlayerMatchesRepository.findPlayerMatches(
              PLAYER_ID, PLAYER_NAME, defaultPlayerIds, 3);

      assertThat(results).hasSize(3);
      // Should return the 3 most recent matches
      assertThat(results.get(0).getMatchId()).isEqualTo(5L);
      assertThat(results.get(1).getMatchId()).isEqualTo(4L);
      assertThat(results.get(2).getMatchId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should use default limit when null")
    void shouldUseDefaultLimitWhenNull() {
      Set<Long> defaultPlayerIds = Set.of(DEFAULT_PLAYER_1_ID);

      List<FindPlayerMatchesResponse> results =
          findPlayerMatchesRepository.findPlayerMatches(
              PLAYER_ID, PLAYER_NAME, defaultPlayerIds, null);

      // Default limit is 20, but we only have 5 matches
      assertThat(results).hasSize(5);
    }

    @Test
    @DisplayName("Should use default limit when zero or negative")
    void shouldUseDefaultLimitWhenZeroOrNegative() {
      Set<Long> defaultPlayerIds = Set.of(DEFAULT_PLAYER_1_ID);

      List<FindPlayerMatchesResponse> results =
          findPlayerMatchesRepository.findPlayerMatches(
              PLAYER_ID, PLAYER_NAME, defaultPlayerIds, 0);

      assertThat(results).hasSize(5);

      results =
          findPlayerMatchesRepository.findPlayerMatches(
              PLAYER_ID, PLAYER_NAME, defaultPlayerIds, -5);

      assertThat(results).hasSize(5);
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should return empty list for non-existent player")
    void shouldReturnEmptyListForNonExistentPlayer() {
      Set<Long> defaultPlayerIds = Set.of(DEFAULT_PLAYER_1_ID);

      List<FindPlayerMatchesResponse> results =
          findPlayerMatchesRepository.findPlayerMatches(
              999999L, "NonExistent", defaultPlayerIds, 10);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty default players set")
    void shouldHandleEmptyDefaultPlayersSet() {
      Set<Long> defaultPlayerIds = Set.of();

      List<FindPlayerMatchesResponse> results =
          findPlayerMatchesRepository.findPlayerMatches(
              PLAYER_ID, PLAYER_NAME, defaultPlayerIds, 10);

      assertThat(results).hasSize(5);
      // Each match should only have the searched player's statistics
      results.forEach(
          match -> {
            assertThat(match.getStatistics())
                .extracting(PlayerMatchStatistic::getPlayerId)
                .containsOnly(PLAYER_ID);
          });
    }

    @Test
    @DisplayName("Should only include statistics for players in the match")
    void shouldOnlyIncludeStatisticsForPlayersInMatch() {
      Set<Long> defaultPlayerIds = Set.of(DEFAULT_PLAYER_1_ID, DEFAULT_PLAYER_2_ID);

      List<FindPlayerMatchesResponse> results =
          findPlayerMatchesRepository.findPlayerMatches(
              PLAYER_ID, PLAYER_NAME, defaultPlayerIds, 10);

      // Match 4 only has TestPlayer
      FindPlayerMatchesResponse match4 =
          results.stream().filter(r -> r.getMatchId().equals(4L)).findFirst().orElseThrow();

      assertThat(match4.getStatistics()).hasSize(1);
      assertThat(match4.getStatistics().get(0).getPlayerId()).isEqualTo(PLAYER_ID);
    }

    @Test
    @DisplayName("Should handle player on Dire side (slot >= 128)")
    void shouldHandlePlayerOnDireSide() {
      Set<Long> defaultPlayerIds = Set.of(DEFAULT_PLAYER_2_ID);

      List<FindPlayerMatchesResponse> results =
          findPlayerMatchesRepository.findPlayerMatches(
              PLAYER_ID, PLAYER_NAME, defaultPlayerIds, 10);

      // Match 2 has DefaultPlayer2 on Dire side (slot 128)
      FindPlayerMatchesResponse match2 =
          results.stream().filter(r -> r.getMatchId().equals(2L)).findFirst().orElseThrow();

      PlayerMatchStatistic direStat =
          match2.getStatistics().stream()
              .filter(s -> s.getPlayerId().equals(DEFAULT_PLAYER_2_ID))
              .findFirst()
              .orElseThrow();

      assertThat(direStat.getPlayerSlot()).isEqualTo(128L);
    }
  }
}
