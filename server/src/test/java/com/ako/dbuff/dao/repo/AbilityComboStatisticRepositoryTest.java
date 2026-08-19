package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AbilityDomain;
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
 *   <li>match 1 — Invoker, WIN, has Quas + Wex
 *   <li>match 2 — Invoker, LOSS, has Quas + Wex
 *   <li>match 3 — Invoker, WIN, has Quas only
 *   <li>match 4 — Anti-Mage, WIN, has Quas + Wex
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

  @Autowired private EntityManager entityManager;
  @Autowired private AbilityRankingRepository abilityRankingRepository;

  @BeforeEach
  void setUp() {
    entityManager.persist(PlayerDomain.builder().id(PLAYER_ID).name("TestPlayer").build());

    match(1L, INVOKER, 1L);
    match(2L, INVOKER, 0L);
    match(3L, INVOKER, 1L);
    match(4L, ANTIMAGE, 1L);

    ability(1L, QUAS, "invoker_quas", 10L);
    ability(1L, WEX, "invoker_wex", 4L);
    ability(2L, QUAS, "invoker_quas", 20L);
    ability(2L, WEX, "invoker_wex", 6L);
    ability(3L, QUAS, "invoker_quas", 15L);
    ability(4L, QUAS, "invoker_quas", 5L);
    ability(4L, WEX, "invoker_wex", 5L);

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
            PLAYER_ID, Set.of(QUAS, WEX), null, null, null);

    assertThat(result.getGamesFound()).isEqualTo(3L);
    assertThat(result.getMatchIds()).doesNotContain(3L);
  }

  @Test
  void winRateIsComputedOverComboGamesOnly() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), null, null, null);

    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.valueOf(66.67).setScale(2));
  }

  @Test
  void heroFilterNarrowsComboGames() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), Set.of(INVOKER), null, null);

    assertThat(result.getGamesFound()).isEqualTo(2L);
    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.valueOf(50.00).setScale(2));
  }

  @Test
  void perAbilityAveragesCoverOnlyComboGames() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), Set.of(INVOKER), null, null);

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
            PLAYER_ID, Set.of(QUAS, WEX, 9999L), null, null, null);

    assertThat(result.getGamesFound()).isZero();
    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getMembers()).isEmpty();
  }
}
