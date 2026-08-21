package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.resources.model.AbilityRankingResponse;
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
 * Integration tests for AbilityRankingRepository. Uses H2 in-memory database to verify SQL queries
 * are built correctly.
 *
 * <p>Note: These tests verify the repository layer directly with Long IDs. The conversion from
 * String ability names to Long IDs is handled by AbilityRankingService using ConstantsManagers.
 */
@DataJpaTest
@Import(AbilityRankingRepository.class)
@ActiveProfiles("test")
class AbilityRankingRepositoryTest {

  @Autowired private EntityManager entityManager;

  @Autowired private AbilityRankingRepository abilityRankingRepository;

  private static final Long PLAYER_ID = 123456L;
  private static final String PLAYER_NAME = "TestPlayer";

  // Ability IDs (these would be resolved from names via ConstantsManagers in the service layer)
  private static final Long BLINK_ABILITY_ID = 5004L;
  private static final Long STUN_ABILITY_ID = 5005L;
  private static final Long HEAL_ABILITY_ID = 5006L;
  private static final Long ULTIMATE_ABILITY_ID = 5007L;

  // Hero IDs. Matches 1-3 are Invoker games, matches 4-5 are Anti-Mage games.
  private static final Long INVOKER_HERO_ID = 74L;
  private static final Long ANTIMAGE_HERO_ID = 1L;

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
    createPlayerStats(1L, 0L, 1L, INVOKER_HERO_ID); // Win
    createPlayerStats(2L, 0L, 0L, INVOKER_HERO_ID); // Loss (Radiant lost)
    createPlayerStats(3L, 0L, 1L, INVOKER_HERO_ID); // Win
    createPlayerStats(4L, 0L, 0L, ANTIMAGE_HERO_ID); // Loss
    createPlayerStats(5L, 0L, 1L, ANTIMAGE_HERO_ID); // Win

    // Create abilities for matches
    // Ability "blink" - picked in 4 matches, 3 wins.
    // Present in all three Invoker games, so its hero-filtered pick rate is 100%.
    createAbility(BLINK_ABILITY_ID, 1L, 0L, "blink", "Blink", 10L);
    createAbility(BLINK_ABILITY_ID, 2L, 0L, "blink", "Blink", 20L);
    createAbility(BLINK_ABILITY_ID, 3L, 0L, "blink", "Blink", 30L);
    createAbility(BLINK_ABILITY_ID, 5L, 0L, "blink", "Blink", 40L);

    // Ability "stun" - picked in 3 matches, 2 wins
    createAbility(STUN_ABILITY_ID, 1L, 0L, "stun", "Stun", 2L);
    createAbility(STUN_ABILITY_ID, 3L, 0L, "stun", "Stun", 4L);
    createAbility(STUN_ABILITY_ID, 4L, 0L, "stun", "Stun", 6L);

    // Ability "heal" - picked in 2 matches, 1 win.
    // useCount is deliberately null: unparsed matches leave it unset, and the
    // average must come back null rather than 0.00.
    createAbility(HEAL_ABILITY_ID, 2L, 0L, "heal", "Heal", null);
    createAbility(HEAL_ABILITY_ID, 4L, 0L, "heal", "Heal", null);

    // Ability "ultimate" - picked in 1 match, 1 win
    createAbility(ULTIMATE_ABILITY_ID, 5L, 0L, "ultimate", "Ultimate", 1L);

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

  private void createPlayerStats(Long matchId, Long playerSlot, Long win, Long heroId) {
    PlayerMatchStatisticDomain stats =
        PlayerMatchStatisticDomain.builder()
            .matchId(matchId)
            .playerSlot(playerSlot)
            .playerId(PLAYER_ID)
            .win(win)
            .heroId(heroId)
            .build();
    entityManager.persist(stats);
  }

  private void createAbility(
      Long abilityId,
      Long matchId,
      Long playerSlot,
      String name,
      String prettyName,
      Long useCount) {
    AbilityDomain ability =
        AbilityDomain.builder()
            .abilityId(abilityId)
            .matchId(matchId)
            .playerSlot(playerSlot)
            .playerId(PLAYER_ID)
            .name(name)
            .prettyName(prettyName)
            .useCount(useCount)
            .build();
    entityManager.persist(ability);
  }

  @Nested
  @DisplayName("Basic Query Tests")
  class BasicQueryTests {

    @Test
    @DisplayName("Should return abilities ordered by pick count descending")
    void shouldReturnAbilitiesOrderedByPickCount() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, null, 10);

      assertThat(results).hasSize(4);
      assertThat(results.get(0).getAbilityId()).isEqualTo(BLINK_ABILITY_ID); // Blink - 4 picks
      assertThat(results.get(1).getAbilityId()).isEqualTo(STUN_ABILITY_ID); // Stun - 3 picks
      assertThat(results.get(2).getAbilityId()).isEqualTo(HEAL_ABILITY_ID); // Heal - 2 picks
      assertThat(results.get(3).getAbilityId()).isEqualTo(ULTIMATE_ABILITY_ID); // Ultimate - 1 pick
    }

    @Test
    @DisplayName("Should calculate pick count correctly")
    void shouldCalculatePickCountCorrectly() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, null, 10);

      AbilityRankingResponse blink =
          results.stream()
              .filter(r -> r.getAbilityId().equals(BLINK_ABILITY_ID))
              .findFirst()
              .orElseThrow();
      assertThat(blink.getPickCount()).isEqualTo(4L);

      AbilityRankingResponse stun =
          results.stream()
              .filter(r -> r.getAbilityId().equals(STUN_ABILITY_ID))
              .findFirst()
              .orElseThrow();
      assertThat(stun.getPickCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should calculate pick rate correctly")
    void shouldCalculatePickRateCorrectly() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, null, 10);

      // Total matches = 5
      AbilityRankingResponse blink =
          results.stream()
              .filter(r -> r.getAbilityId().equals(BLINK_ABILITY_ID))
              .findFirst()
              .orElseThrow();
      // 4/5 = 80%
      assertThat(blink.getPickRate()).isEqualByComparingTo(new BigDecimal("80.00"));

      AbilityRankingResponse stun =
          results.stream()
              .filter(r -> r.getAbilityId().equals(STUN_ABILITY_ID))
              .findFirst()
              .orElseThrow();
      // 3/5 = 60%
      assertThat(stun.getPickRate()).isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("Should calculate win rate correctly")
    void shouldCalculateWinRateCorrectly() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, null, 10);

      // Blink: picked in matches 1,2,3,5 - wins in 1,3,5 = 3/4 = 75%
      AbilityRankingResponse blink =
          results.stream()
              .filter(r -> r.getAbilityId().equals(BLINK_ABILITY_ID))
              .findFirst()
              .orElseThrow();
      assertThat(blink.getWinRate()).isEqualByComparingTo(new BigDecimal("75.00"));

      // Stun: picked in matches 1,3,4 - wins in 1,3 = 2/3 = 66.67%
      AbilityRankingResponse stun =
          results.stream()
              .filter(r -> r.getAbilityId().equals(STUN_ABILITY_ID))
              .findFirst()
              .orElseThrow();
      assertThat(stun.getWinRate()).isEqualByComparingTo(new BigDecimal("66.67"));

      // Ultimate: picked in match 5 - win = 1/1 = 100%
      AbilityRankingResponse ultimate =
          results.stream()
              .filter(r -> r.getAbilityId().equals(ULTIMATE_ABILITY_ID))
              .findFirst()
              .orElseThrow();
      assertThat(ultimate.getWinRate()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should include player info in response")
    void shouldIncludePlayerInfo() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, null, 10);

      assertThat(results)
          .allSatisfy(
              r -> {
                assertThat(r.getPlayerId()).isEqualTo(PLAYER_ID);
                assertThat(r.getPlayerName()).isEqualTo(PLAYER_NAME);
              });
    }

    @Test
    @DisplayName("Should include ability name and pretty name")
    void shouldIncludeAbilityNames() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, null, 10);

      AbilityRankingResponse blink =
          results.stream()
              .filter(r -> r.getAbilityId().equals(BLINK_ABILITY_ID))
              .findFirst()
              .orElseThrow();
      assertThat(blink.getAbilityName()).isEqualTo("blink");
      assertThat(blink.getAbilityPrettyName()).isEqualTo("Blink");
    }
  }

  @Nested
  @DisplayName("Date Filter Tests")
  class DateFilterTests {

    @Test
    @DisplayName("Should filter by start date")
    void shouldFilterByStartDate() {
      // Only matches from March onwards (matches 3, 4, 5)
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, LocalDate.of(2024, 3, 1), null, null, null, null, null, 10);

      // Blink in matches 3, 5 = 2 picks
      AbilityRankingResponse blink =
          results.stream()
              .filter(r -> r.getAbilityId().equals(BLINK_ABILITY_ID))
              .findFirst()
              .orElseThrow();
      assertThat(blink.getPickCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should filter by end date")
    void shouldFilterByEndDate() {
      // Only matches until February (matches 1, 2)
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, LocalDate.of(2024, 2, 28), null, null, null, null, 10);

      // Blink in matches 1, 2 = 2 picks
      AbilityRankingResponse blink =
          results.stream()
              .filter(r -> r.getAbilityId().equals(BLINK_ABILITY_ID))
              .findFirst()
              .orElseThrow();
      assertThat(blink.getPickCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should filter by date range")
    void shouldFilterByDateRange() {
      // Only matches in February and March (matches 2, 3)
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID,
              LocalDate.of(2024, 2, 1),
              LocalDate.of(2024, 3, 31),
              null,
              null,
              null,
              null,
              10);

      // Blink in matches 2, 3 = 2 picks
      AbilityRankingResponse blink =
          results.stream()
              .filter(r -> r.getAbilityId().equals(BLINK_ABILITY_ID))
              .findFirst()
              .orElseThrow();
      assertThat(blink.getPickCount()).isEqualTo(2L);

      // Stun only in match 3 = 1 pick
      AbilityRankingResponse stun =
          results.stream()
              .filter(r -> r.getAbilityId().equals(STUN_ABILITY_ID))
              .findFirst()
              .orElseThrow();
      assertThat(stun.getPickCount()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("Ability Filter Tests")
  class AbilityFilterTests {

    @Test
    @DisplayName("Should filter by specific ability IDs")
    void shouldFilterByAbilityIds() {
      // Filter by ability IDs (converted from names in service layer)
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID,
              null,
              null,
              Set.of(BLINK_ABILITY_ID, HEAL_ABILITY_ID),
              null,
              null,
              null,
              10);

      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(AbilityRankingResponse::getAbilityId)
          .containsExactly(BLINK_ABILITY_ID, HEAL_ABILITY_ID);
    }

    @Test
    @DisplayName("Should exclude specified ability IDs")
    void shouldExcludeAbilityIds() {
      // Exclude ability ID (converted from name in service layer)
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, Set.of(BLINK_ABILITY_ID), null, null, 10);

      assertThat(results).hasSize(3);
      assertThat(results)
          .extracting(AbilityRankingResponse::getAbilityId)
          .doesNotContain(BLINK_ABILITY_ID);
    }

    @Test
    @DisplayName("Should handle both include and exclude filters")
    void shouldHandleBothFilters() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID,
              null,
              null,
              Set.of(BLINK_ABILITY_ID, STUN_ABILITY_ID, HEAL_ABILITY_ID),
              Set.of(STUN_ABILITY_ID),
              null,
              null,
              10);

      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(AbilityRankingResponse::getAbilityId)
          .containsExactly(BLINK_ABILITY_ID, HEAL_ABILITY_ID);
    }
  }

  @Nested
  @DisplayName("Limit Tests")
  class LimitTests {

    @Test
    @DisplayName("Should respect limit parameter")
    void shouldRespectLimit() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, null, 2);

      assertThat(results).hasSize(2);
      // Should return top 2 by pick count
      assertThat(results.get(0).getAbilityId()).isEqualTo(BLINK_ABILITY_ID);
      assertThat(results.get(1).getAbilityId()).isEqualTo(STUN_ABILITY_ID);
    }

    @Test
    @DisplayName("Should use default limit of 10 when null")
    void shouldUseDefaultLimit() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, null, null);

      // We only have 4 abilities, so should return all 4
      assertThat(results).hasSize(4);
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should return empty list for non-existent player")
    void shouldReturnEmptyForNonExistentPlayer() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              999999L, null, null, null, null, null, null, 10);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when no matches in date range")
    void shouldReturnEmptyWhenNoMatchesInRange() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID,
              LocalDate.of(2025, 1, 1),
              LocalDate.of(2025, 12, 31),
              null,
              null,
              null,
              null,
              10);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty ability filter set")
    void shouldHandleEmptyAbilityFilterSet() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, Set.of(), null, null, null, 10);

      // Empty set should be treated as no filter
      assertThat(results).hasSize(4);
    }
  }

  @Nested
  @DisplayName("Hero Filter Tests")
  class HeroFilterTests {

    @Test
    @DisplayName("Should restrict rankings to the given hero's matches")
    void shouldRestrictToHero() {
      List<AbilityRankingResponse> invokerOnly =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(INVOKER_HERO_ID), null, 10);

      // Invoker games are 1-3: blink in all three, stun in 1 and 3, heal in 2.
      // "ultimate" is only in match 5 (Anti-Mage) so it must not appear.
      assertThat(invokerOnly)
          .extracting(AbilityRankingResponse::getAbilityId)
          .containsExactlyInAnyOrder(BLINK_ABILITY_ID, STUN_ABILITY_ID, HEAL_ABILITY_ID);

      assertThat(find(invokerOnly, BLINK_ABILITY_ID).getPickCount()).isEqualTo(3L);
      assertThat(find(invokerOnly, STUN_ABILITY_ID).getPickCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Pick rate must be relative to that hero's games only")
    void pickRateIsRelativeToHeroGamesOnly() {
      // Regression guard: getTotalMatchCount must receive the hero filter too.
      // Without it, pick rate divides by the player's 5 total games instead of
      // the 3 Invoker games, reporting 60% instead of 100%.
      List<AbilityRankingResponse> invokerOnly =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(INVOKER_HERO_ID), null, 10);

      assertThat(find(invokerOnly, BLINK_ABILITY_ID).getPickRate())
          .isEqualByComparingTo(new BigDecimal("100.00"));
      assertThat(find(invokerOnly, STUN_ABILITY_ID).getPickRate())
          .isEqualByComparingTo(new BigDecimal("66.67"));
    }

    @Test
    @DisplayName("Should return empty when the hero filter matches no games")
    void heroFilterMatchingNoGamesReturnsEmpty() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(999L), null, 10);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Empty hero filter set should be treated as no filter")
    void emptyHeroFilterSetIsNoFilter() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(), null, 10);

      assertThat(results).hasSize(4);
    }
  }

  @Nested
  @DisplayName("Average Use Count Tests")
  class AverageUseCountTests {

    @Test
    @DisplayName("Should average use count across matches")
    void shouldAverageUseCount() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, Set.of(BLINK_ABILITY_ID), null, null, null, 10);

      // Blink use counts: 10, 20, 30, 40 -> 25.00
      assertThat(results).hasSize(1);
      assertThat(results.get(0).getAvgUseCount()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    @DisplayName("Should return null when no match recorded a use count")
    void shouldReturnNullWhenNoUseData() {
      // Heal rows have useCount = null: "no data", not "never used".
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, Set.of(HEAL_ABILITY_ID), null, null, null, 10);

      assertThat(results).hasSize(1);
      assertThat(results.get(0).getAvgUseCount()).isNull();
    }

    @Test
    @DisplayName("Average use count should respect the hero filter")
    void averageUseCountRespectsHeroFilter() {
      List<AbilityRankingResponse> results =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID,
              null,
              null,
              Set.of(BLINK_ABILITY_ID),
              null,
              Set.of(INVOKER_HERO_ID),
              null,
              10);

      // Invoker games only: use counts 10, 20, 30 -> 20.00
      assertThat(results).hasSize(1);
      assertThat(results.get(0).getAvgUseCount()).isEqualByComparingTo(new BigDecimal("20.00"));
    }
  }

  private static AbilityRankingResponse find(List<AbilityRankingResponse> results, Long abilityId) {
    return results.stream()
        .filter(r -> r.getAbilityId().equals(abilityId))
        .findFirst()
        .orElseThrow();
  }
}
