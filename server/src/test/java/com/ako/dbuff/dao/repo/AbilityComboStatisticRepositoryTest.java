package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.resources.model.AbilityComboStatisticResponse;
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

/**
 * Fixture (player 123, slot 0):
 *
 * <ul>
 *   <li>match 1 — Invoker, WIN, Ability Draft, has Quas + Wex, bought Blink at 500
 *   <li>match 2 — Invoker, LOSS, Ability Draft, has Quas + Wex, bought BKB
 *   <li>match 3 — Invoker, WIN, Ability Draft, has Quas only, bought Blink at 700
 *   <li>match 4 — Anti-Mage, WIN, no recorded mode, has Quas + Wex, bought Blink and a neutral
 * </ul>
 */
@DataJpaTest
@Import(AbilityRankingRepository.class)
@ActiveProfiles("test")
class AbilityComboStatisticRepositoryTest {

  private static final Long PLAYER_ID = 123L;
  private static final Long QUAS = 5001L;
  private static final Long WEX = 5002L;
  private static final Long INVOKER = 74L;
  private static final Long ANTIMAGE = 1L;
  private static final Long BLINK = 1L;
  private static final Long BKB = 2L;
  private static final Long TRUSTY_SHOVEL = 3L;
  private static final Long ABILITY_DRAFT = 18L;

  @Autowired private EntityManager entityManager;
  @Autowired private AbilityRankingRepository abilityRankingRepository;

  @BeforeEach
  void setUp() {
    entityManager.persist(PlayerDomain.builder().id(PLAYER_ID).name("TestPlayer").build());

    match(1L, INVOKER, 1L, ABILITY_DRAFT);
    match(2L, INVOKER, 0L, ABILITY_DRAFT);
    match(3L, INVOKER, 1L, ABILITY_DRAFT);
    match(4L, ANTIMAGE, 1L, null);

    ability(1L, QUAS, "invoker_quas", 10L);
    ability(1L, WEX, "invoker_wex", 4L);
    ability(2L, QUAS, "invoker_quas", 20L);
    ability(2L, WEX, "invoker_wex", 6L);
    ability(3L, QUAS, "invoker_quas", 15L);
    ability(4L, QUAS, "invoker_quas", 5L);
    ability(4L, WEX, "invoker_wex", 5L);

    item(1L, BLINK, "blink", 500L, false);
    item(2L, BKB, "black_king_bar", 900L, false);
    item(3L, BLINK, "blink", 700L, false);
    item(4L, BLINK, "blink", 600L, false);
    item(4L, TRUSTY_SHOVEL, "trusty_shovel", 800L, true);

    entityManager.flush();
  }

  private void match(Long matchId, Long heroId, Long win, Long gameModeId) {
    entityManager.persist(
        MatchDomain.builder()
            .id(matchId)
            .startLocalDate(LocalDate.of(2026, 8, 10))
            .gameModeId(gameModeId)
            .build());
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

  private void item(Long matchId, Long itemId, String name, Long purchaseTime, boolean neutral) {
    entityManager.persist(
        ItemDomain.builder()
            .matchId(matchId)
            .playerSlot(0L)
            .itemId(itemId)
            .playerId(PLAYER_ID)
            .itemName(name)
            .itemPrettyName(name)
            .itemPurchaseTime(purchaseTime)
            .isNeutral(neutral)
            .useCount(1L)
            .build());
  }

  private void ability(Long matchId, Long abilityId, String name, Long useCount) {
    entityManager.persist(
        AbilityDomain.builder()
            .matchId(matchId)
            .playerSlot(0L)
            .abilityId(abilityId)
            .playerId(PLAYER_ID)
            .name(name)
            .prettyName(name)
            .useCount(useCount)
            .build());
  }

  @Test
  void requiresAllAbilitiesInTheSameGame() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), null, null, null, null, null);

    assertThat(result.getGamesFound()).isEqualTo(3L);
    assertThat(result.getMatchIds()).doesNotContain(3L);
  }

  @Test
  void winRateIsComputedOverComboGamesOnly() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), null, null, null, null, null);

    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.valueOf(66.67).setScale(2));
  }

  @Test
  void heroFilterNarrowsComboGames() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), null, Set.of(INVOKER), null, null, null);

    assertThat(result.getGamesFound()).isEqualTo(2L);
    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.valueOf(50.00).setScale(2));
  }

  @Test
  void perAbilityAveragesCoverOnlyComboGames() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), null, Set.of(INVOKER), null, null, null);

    AbilityComboStatisticResponse.Member quas =
        result.getMembers().stream()
            .filter(m -> m.getAbilityId().equals(QUAS))
            .findFirst()
            .orElseThrow();

    // Invoker combo games are 1 and 2: use counts 10 and 20 -> 15.00
    assertThat(quas.getAvgUseCount()).isEqualByComparingTo(BigDecimal.valueOf(15.00).setScale(2));
  }

  @Test
  void noComboGames_returnsZeroNotNull() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX, 9999L), null, null, null, null, null);

    assertThat(result.getGamesFound()).isZero();
    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getMembers()).isEmpty();
  }

  // ------------------------------------------------------- skills plus items

  /**
   * Items narrow the skill combo to games containing both. Match 2 has Quas + Wex but bought a BKB
   * rather than a Blink, so the three-game skill combo becomes two — asking for the skill combo and
   * the item build as separate questions would report three.
   */
  @Test
  void itemsNarrowTheComboToGamesContainingBoth() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), Set.of(BLINK), null, null, null, null);

    assertThat(result.getGamesFound()).isEqualTo(2L);
    assertThat(result.getMatchIds()).containsExactlyInAnyOrder(1L, 4L);
    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.valueOf(100.00).setScale(2));
  }

  @Test
  void everyRequestedItemMustBePresent() {
    // Match 1 has Blink but no BKB, match 2 has BKB but no Blink: no game has both.
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), Set.of(BLINK, BKB), null, null, null, null);

    assertThat(result.getGamesFound()).isZero();
    assertThat(result.getItemMembers()).isEmpty();
  }

  @Test
  void itemMembersAreAveragedOverTheComboGamesOnly() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS), Set.of(BLINK), null, null, null, null);

    // Quas + Blink games are 1, 3 and 4: purchase times 500, 700 and 600 -> 600.00
    assertThat(result.getItemMembers()).hasSize(1);
    assertThat(result.getItemMembers().get(0).getAvgPurchaseTime())
        .isEqualByComparingTo(BigDecimal.valueOf(600.00).setScale(2));
  }

  /** Without items the response must not pretend an item conjunction was asked for. */
  @Test
  void noItemsRequested_leavesItemMembersEmpty() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), null, null, null, null, null);

    assertThat(result.getGamesFound()).isEqualTo(3L);
    assertThat(result.getItemMembers()).isEmpty();
  }

  /** A neutral item is not something the player bought, so it must not satisfy the conjunction. */
  @Test
  void neutralItemsDoNotSatisfyTheConjunction() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), Set.of(TRUSTY_SHOVEL), null, null, null, null);

    assertThat(result.getGamesFound()).isZero();
  }

  @Test
  void itemConjunctionCombinesWithTheHeroAndGameModeFilters() {
    // Match 4 is Anti-Mage with Quas + Wex + Blink; restricting to Invoker excludes it.
    AbilityComboStatisticResponse antiMage =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), Set.of(BLINK), Set.of(ANTIMAGE), null, null, null);
    assertThat(antiMage.getGamesFound()).isEqualTo(1L);
    assertThat(antiMage.getMatchIds()).containsExactly(4L);

    // Every fixture match is Ability Draft except match 4, which has no recorded mode.
    AbilityComboStatisticResponse byMode =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), Set.of(BLINK), null, Set.of(ABILITY_DRAFT), null, null);
    assertThat(byMode.getMatchIds()).containsExactly(1L);
  }
}
