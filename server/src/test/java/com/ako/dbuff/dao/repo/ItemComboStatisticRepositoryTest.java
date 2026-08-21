package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.resources.model.ItemComboStatisticResponse;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the conjunctive combo query: statistics over games containing ALL requested items.
 *
 * <p>Fixture layout (player 123, slot 0 in every match):
 *
 * <ul>
 *   <li>match 1 — Invoker, WIN, has Blink + BKB
 *   <li>match 2 — Invoker, LOSS, has Blink + BKB
 *   <li>match 3 — Invoker, WIN, has Blink only
 *   <li>match 4 — Anti-Mage, WIN, has Blink + BKB
 *   <li>match 5 — Invoker, WIN, has Blink only
 * </ul>
 */
@DataJpaTest
@Import(ItemRankingRepository.class)
@ActiveProfiles("test")
class ItemComboStatisticRepositoryTest {

  private static final Long PLAYER_ID = 123L;
  private static final String PLAYER_NAME = "TestPlayer";
  private static final Long BLINK = 100L;
  private static final Long BKB = 200L;
  private static final Long INVOKER = 74L;
  private static final Long ANTIMAGE = 1L;

  @Autowired private EntityManager entityManager;
  @Autowired private ItemRankingRepository itemRankingRepository;

  @BeforeEach
  void setUp() {
    entityManager.persist(PlayerDomain.builder().id(PLAYER_ID).name(PLAYER_NAME).build());

    match(1L, INVOKER, 1L);
    match(2L, INVOKER, 0L);
    match(3L, INVOKER, 1L);
    match(4L, ANTIMAGE, 1L);
    match(5L, INVOKER, 1L);

    item(1L, BLINK, 400L, 3L);
    item(1L, BKB, 900L, 1L);
    item(2L, BLINK, 500L, 2L);
    item(2L, BKB, 1000L, 2L);
    item(3L, BLINK, 450L, 4L);
    item(4L, BLINK, 420L, 1L);
    item(4L, BKB, 950L, 1L);
    item(5L, BLINK, 400L, 1L);

    entityManager.flush();
  }

  private void match(Long matchId, Long heroId, Long win) {
    entityManager.persist(
        MatchDomain.builder().id(matchId).startLocalDate(LocalDate.of(2026, 8, 10)).build());
    entityManager.persist(
        PlayerMatchStatisticDomain.builder()
            .matchId(matchId)
            .playerSlot(0L)
            .playerId(PLAYER_ID)
            .heroId(heroId)
            .win(win)
            .kda(BigDecimal.valueOf(3.0))
            .build());
  }

  private void item(Long matchId, Long itemId, Long purchaseTime, Long useCount) {
    entityManager.persist(
        ItemDomain.builder()
            .matchId(matchId)
            .playerSlot(0L)
            .itemId(itemId)
            .playerId(PLAYER_ID)
            .itemPurchaseTime(purchaseTime)
            .useCount(useCount)
            .isNeutral(false)
            .build());
  }

  @Test
  void requiresAllItemsInTheSameGame() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB), null, null, null, null);

    // Matches 1, 2 and 4 have both items. Matches 3 and 5 have only Blink.
    assertThat(result.getGamesFound()).isEqualTo(3L);
  }

  @Test
  void gamesHoldingOnlySomeOfTheItemsAreExcluded() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB), null, null, null, null);

    assertThat(result.getMatchIds()).containsExactlyInAnyOrder(1L, 2L, 4L);
  }

  /**
   * Documents why counting distinct item IDs cannot over-count: the schema forbids the row that
   * would break it. {@code ItemDomain}'s key is (itemId, matchId, playerSlot), so one player cannot
   * hold the same item twice in one match, and a single-item game can never reach the count a
   * two-item request demands. The query still uses {@code countDistinct} so it stays correct if
   * that key ever widens.
   */
  @Test
  void theCompositeKeyForbidsTwoRowsForOneItemInOneGame() {
    ItemDomain duplicate =
        ItemDomain.builder()
            .matchId(5L)
            .playerSlot(0L)
            .itemId(BLINK)
            .playerId(PLAYER_ID)
            .itemPurchaseTime(800L)
            .useCount(2L)
            .isNeutral(false)
            .build();

    assertThatThrownBy(() -> entityManager.persist(duplicate))
        .isInstanceOf(EntityExistsException.class);
  }

  @Test
  void winRateIsComputedOverComboGamesOnly() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB), null, null, null, null);

    // Matches 1 and 4 won, match 2 lost -> 2/3 = 66.67%
    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.valueOf(66.67).setScale(2));
  }

  @Test
  void heroFilterNarrowsComboGames() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB), Set.of(INVOKER), null, null, null);

    // Only matches 1 and 2 are Invoker games with both items.
    assertThat(result.getGamesFound()).isEqualTo(2L);
    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.valueOf(50.00).setScale(2));
  }

  @Test
  void perItemAveragesCoverOnlyComboGames() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB), Set.of(INVOKER), null, null, null);

    ItemComboStatisticResponse.Member blink =
        result.getMembers().stream()
            .filter(m -> m.getItemId().equals(BLINK))
            .findFirst()
            .orElseThrow();

    // Invoker combo games are 1 and 2: purchase times 400 and 500 -> 450
    assertThat(blink.getAvgPurchaseTime())
        .isEqualByComparingTo(BigDecimal.valueOf(450.00).setScale(2));
    // use counts 3 and 2 -> 2.50
    assertThat(blink.getAvgUseCount()).isEqualByComparingTo(BigDecimal.valueOf(2.50).setScale(2));
  }

  @Test
  void singleItemDegeneratesToGamesWithThatItem() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BKB), null, null, null, null);

    assertThat(result.getGamesFound()).isEqualTo(3L);
  }

  @Test
  void noComboGames_returnsZeroNotNull() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB, 999L), null, null, null, null);

    assertThat(result.getGamesFound()).isZero();
    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getMembers()).isEmpty();
  }

  @Test
  void dateRangeExcludesOlderComboGames() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB), null, null, LocalDate.of(2026, 8, 15), null);

    assertThat(result.getGamesFound()).isZero();
  }
}
