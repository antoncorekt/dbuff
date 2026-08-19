package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.resources.model.ItemRankingResponse;
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
 * Integration tests for ItemRankingRepository. Uses H2 in-memory database to verify SQL queries are
 * built correctly.
 *
 * <p>Note: These tests verify the repository layer directly with Long IDs. The conversion from
 * String dnames to Long IDs is handled by ItemRankingService using ConstantsManagers.
 */
@DataJpaTest
@Import(ItemRankingRepository.class)
@ActiveProfiles("test")
class ItemRankingRepositoryTest {

  @Autowired private EntityManager entityManager;

  @Autowired private ItemRankingRepository itemRankingRepository;

  private static final Long PLAYER_ID = 123456L;
  private static final String PLAYER_NAME = "TestPlayer";

  // Item IDs (these would be resolved from dnames via ConstantsManagers in the service layer)
  private static final Long BLINK_ITEM_ID = 100L;
  private static final Long BKB_ITEM_ID = 200L;
  private static final Long NEUTRAL_ITEM_ID = 300L;
  private static final Long BOOTS_ITEM_ID = 400L;

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

    // Create items for matches
    // Item "blink" (Blink Dagger) - picked in 4 matches, 3 wins.
    // Present in all three Invoker games, so its hero-filtered pick rate is 100%.
    createItem(BLINK_ITEM_ID, 1L, 0L, "blink", "Blink Dagger", 600L, false, 3L);
    createItem(BLINK_ITEM_ID, 2L, 0L, "blink", "Blink Dagger", 720L, false, 2L);
    createItem(BLINK_ITEM_ID, 3L, 0L, "blink", "Blink Dagger", 540L, false, 4L);
    createItem(BLINK_ITEM_ID, 5L, 0L, "blink", "Blink Dagger", 660L, false, 1L);

    // Item "black_king_bar" (BKB) - picked in 3 matches, 2 wins
    createItem(BKB_ITEM_ID, 1L, 0L, "black_king_bar", "Black King Bar", 1200L, false, 1L);
    createItem(BKB_ITEM_ID, 3L, 0L, "black_king_bar", "Black King Bar", 1100L, false, 1L);
    createItem(BKB_ITEM_ID, 4L, 0L, "black_king_bar", "Black King Bar", 1300L, false, 2L);

    // Item "trusty_shovel" (Neutral item) - should be excluded
    createItem(NEUTRAL_ITEM_ID, 1L, 0L, "trusty_shovel", "Trusty Shovel", 0L, true, null);
    createItem(NEUTRAL_ITEM_ID, 2L, 0L, "trusty_shovel", "Trusty Shovel", 0L, true, null);

    // Item "boots" (Boots) - picked in 2 matches, 1 win.
    // useCount is deliberately null: unparsed matches leave it unset, and the
    // average must come back null rather than 0.00.
    createItem(BOOTS_ITEM_ID, 2L, 0L, "boots", "Boots of Speed", 60L, false, null);
    createItem(BOOTS_ITEM_ID, 4L, 0L, "boots", "Boots of Speed", 45L, false, null);

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

  private void createItem(
      Long itemId,
      Long matchId,
      Long playerSlot,
      String itemName,
      String itemPrettyName,
      Long purchaseTime,
      boolean isNeutral,
      Long useCount) {
    ItemDomain item =
        ItemDomain.builder()
            .itemId(itemId)
            .matchId(matchId)
            .playerSlot(playerSlot)
            .playerId(PLAYER_ID)
            .itemName(itemName)
            .itemPrettyName(itemPrettyName)
            .itemPurchaseTime(purchaseTime)
            .isNeutral(isNeutral)
            .useCount(useCount)
            .build();
    entityManager.persist(item);
  }

  @Nested
  @DisplayName("Basic Query Tests")
  class BasicQueryTests {

    @Test
    @DisplayName("Should return items ordered by pick count descending")
    void shouldReturnItemsOrderedByPickCount() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, 10);

      assertThat(results).hasSize(3);
      assertThat(results.get(0).getItemId()).isEqualTo(BLINK_ITEM_ID); // Blink - 4 picks
      assertThat(results.get(1).getItemId()).isEqualTo(BKB_ITEM_ID); // BKB - 3 picks
      assertThat(results.get(2).getItemId()).isEqualTo(BOOTS_ITEM_ID); // Boots - 2 picks
    }

    @Test
    @DisplayName("Should exclude neutral items from results")
    void shouldExcludeNeutralItems() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, 10);

      assertThat(results)
          .extracting(ItemRankingResponse::getItemId)
          .doesNotContain(NEUTRAL_ITEM_ID);
    }

    @Test
    @DisplayName("Should calculate pick count correctly")
    void shouldCalculatePickCountCorrectly() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, 10);

      ItemRankingResponse blink =
          results.stream()
              .filter(r -> r.getItemId().equals(BLINK_ITEM_ID))
              .findFirst()
              .orElseThrow();
      assertThat(blink.getPickCount()).isEqualTo(4L);

      ItemRankingResponse bkb =
          results.stream().filter(r -> r.getItemId().equals(BKB_ITEM_ID)).findFirst().orElseThrow();
      assertThat(bkb.getPickCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should calculate pick rate correctly")
    void shouldCalculatePickRateCorrectly() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, 10);

      // Total matches = 5
      ItemRankingResponse blink =
          results.stream()
              .filter(r -> r.getItemId().equals(BLINK_ITEM_ID))
              .findFirst()
              .orElseThrow();
      // 4/5 = 80%
      assertThat(blink.getPickRate()).isEqualByComparingTo(new BigDecimal("80.00"));

      ItemRankingResponse bkb =
          results.stream().filter(r -> r.getItemId().equals(BKB_ITEM_ID)).findFirst().orElseThrow();
      // 3/5 = 60%
      assertThat(bkb.getPickRate()).isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("Should calculate win rate correctly")
    void shouldCalculateWinRateCorrectly() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, 10);

      // Blink: picked in matches 1,2,3,5 - wins in 1,3,5 = 3/4 = 75%
      ItemRankingResponse blink =
          results.stream()
              .filter(r -> r.getItemId().equals(BLINK_ITEM_ID))
              .findFirst()
              .orElseThrow();
      assertThat(blink.getWinRate()).isEqualByComparingTo(new BigDecimal("75.00"));

      // BKB: picked in matches 1,3,4 - wins in 1,3 = 2/3 = 66.67%
      ItemRankingResponse bkb =
          results.stream().filter(r -> r.getItemId().equals(BKB_ITEM_ID)).findFirst().orElseThrow();
      assertThat(bkb.getWinRate()).isEqualByComparingTo(new BigDecimal("66.67"));
    }

    @Test
    @DisplayName("Should calculate average purchase time correctly")
    void shouldCalculateAvgPurchaseTimeCorrectly() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, 10);

      // Blink: (600 + 720 + 540 + 660) / 4 = 630
      ItemRankingResponse blink =
          results.stream()
              .filter(r -> r.getItemId().equals(BLINK_ITEM_ID))
              .findFirst()
              .orElseThrow();
      assertThat(blink.getAvgPurchaseTime()).isEqualByComparingTo(new BigDecimal("630.00"));

      // Boots: (60 + 45) / 2 = 52.5
      ItemRankingResponse boots =
          results.stream()
              .filter(r -> r.getItemId().equals(BOOTS_ITEM_ID))
              .findFirst()
              .orElseThrow();
      assertThat(boots.getAvgPurchaseTime()).isEqualByComparingTo(new BigDecimal("52.50"));
    }

    @Test
    @DisplayName("Should include player info in response")
    void shouldIncludePlayerInfo() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, 10);

      assertThat(results)
          .allSatisfy(
              r -> {
                assertThat(r.getPlayerId()).isEqualTo(PLAYER_ID);
                assertThat(r.getPlayerName()).isEqualTo(PLAYER_NAME);
              });
    }
  }

  @Nested
  @DisplayName("Date Filter Tests")
  class DateFilterTests {

    @Test
    @DisplayName("Should filter by start date")
    void shouldFilterByStartDate() {
      // Only matches from March onwards (matches 3, 4, 5)
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, LocalDate.of(2024, 3, 1), null, null, null, null, 10);

      // Blink in matches 3, 5 = 2 picks
      ItemRankingResponse blink =
          results.stream()
              .filter(r -> r.getItemId().equals(BLINK_ITEM_ID))
              .findFirst()
              .orElseThrow();
      assertThat(blink.getPickCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should filter by end date")
    void shouldFilterByEndDate() {
      // Only matches until February (matches 1, 2)
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, LocalDate.of(2024, 2, 28), null, null, null, 10);

      // Blink in matches 1, 2 = 2 picks
      ItemRankingResponse blink =
          results.stream()
              .filter(r -> r.getItemId().equals(BLINK_ITEM_ID))
              .findFirst()
              .orElseThrow();
      assertThat(blink.getPickCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should filter by date range")
    void shouldFilterByDateRange() {
      // Only matches in February and March (matches 2, 3)
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 31), null, null, null, 10);

      // Blink in matches 2, 3 = 2 picks
      ItemRankingResponse blink =
          results.stream()
              .filter(r -> r.getItemId().equals(BLINK_ITEM_ID))
              .findFirst()
              .orElseThrow();
      assertThat(blink.getPickCount()).isEqualTo(2L);

      // BKB only in match 3 = 1 pick
      ItemRankingResponse bkb =
          results.stream().filter(r -> r.getItemId().equals(BKB_ITEM_ID)).findFirst().orElseThrow();
      assertThat(bkb.getPickCount()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("Item Filter Tests")
  class ItemFilterTests {

    @Test
    @DisplayName("Should filter by specific item IDs")
    void shouldFilterByItemIds() {
      // Filter by item IDs (converted from dnames "blink" and "boots" in service layer)
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, Set.of(BLINK_ITEM_ID, BOOTS_ITEM_ID), null, null, 10);

      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(ItemRankingResponse::getItemId)
          .containsExactly(BLINK_ITEM_ID, BOOTS_ITEM_ID);
    }

    @Test
    @DisplayName("Should exclude specified item IDs")
    void shouldExcludeItemIds() {
      // Exclude item ID (converted from dname "blink" in service layer)
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, Set.of(BLINK_ITEM_ID), null, 10);

      assertThat(results).hasSize(2);
      assertThat(results).extracting(ItemRankingResponse::getItemId).doesNotContain(BLINK_ITEM_ID);
    }

    @Test
    @DisplayName("Should handle both include and exclude filters")
    void shouldHandleBothFilters() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID,
              null,
              null,
              Set.of(BLINK_ITEM_ID, BKB_ITEM_ID, BOOTS_ITEM_ID),
              Set.of(BKB_ITEM_ID),
              null,
              10);

      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(ItemRankingResponse::getItemId)
          .containsExactly(BLINK_ITEM_ID, BOOTS_ITEM_ID);
    }
  }

  @Nested
  @DisplayName("Limit Tests")
  class LimitTests {

    @Test
    @DisplayName("Should respect limit parameter")
    void shouldRespectLimit() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, 2);

      assertThat(results).hasSize(2);
      // Should return top 2 by pick count
      assertThat(results.get(0).getItemId()).isEqualTo(BLINK_ITEM_ID);
      assertThat(results.get(1).getItemId()).isEqualTo(BKB_ITEM_ID);
    }

    @Test
    @DisplayName("Should use default limit of 10 when null")
    void shouldUseDefaultLimit() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, null);

      // We only have 3 non-neutral items, so should return all 3
      assertThat(results).hasSize(3);
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should return empty list for non-existent player")
    void shouldReturnEmptyForNonExistentPlayer() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(999999L, null, null, null, null, null, 10);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when no matches in date range")
    void shouldReturnEmptyWhenNoMatchesInRange() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID,
              LocalDate.of(2025, 1, 1),
              LocalDate.of(2025, 12, 31),
              null,
              null,
              null,
              10);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty item filter set")
    void shouldHandleEmptyItemFilterSet() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, Set.of(), null, null, 10);

      // Empty set should be treated as no filter
      assertThat(results).hasSize(3);
    }
  }

  @Nested
  @DisplayName("Hero Filter Tests")
  class HeroFilterTests {

    @Test
    @DisplayName("Should restrict rankings to the given hero's matches")
    void shouldRestrictToHero() {
      List<ItemRankingResponse> invokerOnly =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(INVOKER_HERO_ID), 10);

      // Invoker games are 1-3. Blink appears in all three, BKB in 1 and 3.
      // Boots appears only in match 2 among Invoker games.
      assertThat(invokerOnly)
          .extracting(ItemRankingResponse::getItemId)
          .containsExactlyInAnyOrder(BLINK_ITEM_ID, BKB_ITEM_ID, BOOTS_ITEM_ID);

      ItemRankingResponse blink = find(invokerOnly, BLINK_ITEM_ID);
      assertThat(blink.getPickCount()).isEqualTo(3L);

      ItemRankingResponse bkb = find(invokerOnly, BKB_ITEM_ID);
      assertThat(bkb.getPickCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Pick rate must be relative to that hero's games only")
    void pickRateIsRelativeToHeroGamesOnly() {
      // Regression guard: getTotalMatchCount must receive the hero filter too.
      // Without it, pick rate divides by the player's 5 total games instead of
      // the 3 Invoker games, reporting 60% instead of 100%.
      List<ItemRankingResponse> invokerOnly =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(INVOKER_HERO_ID), 10);

      assertThat(find(invokerOnly, BLINK_ITEM_ID).getPickRate())
          .isEqualByComparingTo(new BigDecimal("100.00"));

      // BKB: 2 of 3 Invoker games
      assertThat(find(invokerOnly, BKB_ITEM_ID).getPickRate())
          .isEqualByComparingTo(new BigDecimal("66.67"));
    }

    @Test
    @DisplayName("Win rate should reflect only the filtered hero's games")
    void winRateReflectsFilteredGames() {
      List<ItemRankingResponse> antimageOnly =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(ANTIMAGE_HERO_ID), 10);

      // Anti-Mage games are 4 (loss) and 5 (win). Blink only in match 5 -> 100%.
      assertThat(find(antimageOnly, BLINK_ITEM_ID).getWinRate())
          .isEqualByComparingTo(new BigDecimal("100.00"));

      // BKB only in match 4 -> 0%.
      assertThat(find(antimageOnly, BKB_ITEM_ID).getWinRate())
          .isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Should return empty when the hero filter matches no games")
    void heroFilterMatchingNoGamesReturnsEmpty() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(999L), 10);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Empty hero filter set should be treated as no filter")
    void emptyHeroFilterSetIsNoFilter() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(), 10);

      assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("Hero filter should combine with a date filter")
    void heroFilterCombinesWithDateFilter() {
      // Invoker games from February onwards: matches 2 and 3.
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, LocalDate.of(2024, 2, 1), null, null, null, Set.of(INVOKER_HERO_ID), 10);

      assertThat(find(results, BLINK_ITEM_ID).getPickCount()).isEqualTo(2L);
      // 2 of 2 games in range -> 100%
      assertThat(find(results, BLINK_ITEM_ID).getPickRate())
          .isEqualByComparingTo(new BigDecimal("100.00"));
    }
  }

  @Nested
  @DisplayName("Average Use Count Tests")
  class AverageUseCountTests {

    @Test
    @DisplayName("Should average use count across matches")
    void shouldAverageUseCount() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, Set.of(BLINK_ITEM_ID), null, null, 10);

      // Blink use counts: 3, 2, 4, 1 -> 2.50
      assertThat(results).hasSize(1);
      assertThat(results.get(0).getAvgUseCount()).isEqualByComparingTo(new BigDecimal("2.50"));
    }

    @Test
    @DisplayName("Should return null when no match recorded a use count")
    void shouldReturnNullWhenNoUseData() {
      // Boots rows have useCount = null: "no data", not "never used".
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, Set.of(BOOTS_ITEM_ID), null, null, 10);

      assertThat(results).hasSize(1);
      assertThat(results.get(0).getAvgUseCount()).isNull();
    }

    @Test
    @DisplayName("Average use count should respect the hero filter")
    void averageUseCountRespectsHeroFilter() {
      List<ItemRankingResponse> results =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, Set.of(BLINK_ITEM_ID), null, Set.of(INVOKER_HERO_ID), 10);

      // Invoker games only: use counts 3, 2, 4 -> 3.00
      assertThat(results).hasSize(1);
      assertThat(results.get(0).getAvgUseCount()).isEqualByComparingTo(new BigDecimal("3.00"));
    }
  }

  private static ItemRankingResponse find(List<ItemRankingResponse> results, Long itemId) {
    return results.stream().filter(r -> r.getItemId().equals(itemId)).findFirst().orElseThrow();
  }
}
