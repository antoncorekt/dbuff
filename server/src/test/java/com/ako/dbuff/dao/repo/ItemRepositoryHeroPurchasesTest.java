package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ItemRepositoryHeroPurchasesTest {
  private static final long HERO = 74L;
  private static final long SINCE = 1_000L;
  private static final long NO_MATCH = 99L;

  @Autowired private EntityManager entityManager;
  @Autowired private ItemRepository repository;

  @BeforeEach
  void setUp() {
    match(1, 5_000);
    match(2, 5_000);
    match(3, 5_000);
    match(4, 500);
    match(5, 5_000);
    player(1, HERO, 1, 3L);
    player(2, HERO, 0, 1L);
    player(3, HERO, 1, 1L);
    player(4, HERO, 1, 1L);
    player(5, 1L, 1, 1L);
    role(1, 1);
    role(3, 3);
    role(4, 1);
    role(5, 1);
    for (long matchId = 1; matchId <= 5; matchId++) {
      item(matchId, 100, 1_200, false);
    }
    item(1, 200, 9_000, false);
    item(1, 300, 1_250, true);
    timeline(1, 0, 6_000);
    timeline(1, 128, 3_000);
    timeline(1, 129, 5_000);
    entityManager.flush();
  }

  @Test
  void filtersByHeroRoleMatchAgeAndNeutralAndComputesTheLeadOverTheOpponents() {
    List<Object[]> rows = purchases(1, 1L, 1_000L, 1_500L, NO_MATCH);

    assertThat(rows).hasSize(2);
    Object[] withRoleRow = rowOf(rows, 1L);
    Object[] byLaneRole = rowOf(rows, 0L);
    assertThat(withRoleRow[0]).isEqualTo(100L);
    assertThat(((Number) withRoleRow[3]).doubleValue()).isEqualTo(2_000.0);
    assertThat(byLaneRole[3]).isNull();
  }

  @Test
  void aMatchWithARoleRowIgnoresItsLaneRole() {
    List<Object[]> rows = purchases(3, 3L, 1_000L, 1_500L, NO_MATCH);

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst()[2]).isEqualTo(1L);
  }

  @Test
  void theTimeRangeAndTheExcludedMatchNarrowTheRows() {
    assertThat(purchases(1, 1L, 0L, 20_000L, NO_MATCH)).hasSize(3);
    assertThat(purchases(1, 1L, 0L, 20_000L, 1L)).hasSize(1);
  }

  private List<Object[]> purchases(int role, Long laneRole, long from, long to, long exclude) {
    return repository.findHeroPurchases(HERO, role, laneRole, from, to, 10, exclude, SINCE);
  }

  private static Object[] rowOf(List<Object[]> rows, long win) {
    return rows.stream().filter(r -> ((Number) r[2]).longValue() == win).findFirst().orElseThrow();
  }

  private void match(long id, long start) {
    entityManager.persist(MatchDomain.builder().id(id).startTime(start).build());
  }

  private void player(long matchId, long hero, long win, Long laneRole) {
    entityManager.persist(
        PlayerMatchStatisticDomain.builder()
            .matchId(matchId)
            .playerSlot(0L)
            .playerId(10L)
            .heroId(hero)
            .win(win)
            .laneRole(laneRole)
            .build());
  }

  private void role(long matchId, int role) {
    entityManager.persist(
        PlayerRoleDomain.builder().matchId(matchId).playerSlot(0L).role(role).build());
  }

  private void item(long matchId, long itemId, long time, boolean neutral) {
    entityManager.persist(
        ItemDomain.builder()
            .matchId(matchId)
            .playerSlot(0L)
            .itemId(itemId)
            .playerId(10L)
            .itemPurchaseTime(time)
            .isNeutral(neutral)
            .build());
  }

  private void timeline(long matchId, long slot, long networth) {
    entityManager.persist(
        PlayerTimelineDomain.builder()
            .matchId(matchId)
            .playerSlot(slot)
            .gameMinute(30)
            .networth(networth)
            .build());
  }
}
