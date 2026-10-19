package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.analysis.item.AlternativeFinder.Alternative;
import com.ako.dbuff.service.replay.analysis.item.AlternativeFinder.Purchase;
import com.ako.dbuff.service.replay.analysis.item.AlternativeFinder.Result;
import com.ako.dbuff.service.replay.analysis.item.AlternativeFinder.Settings;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlternativeFinderTest {
  private static final long MATCH = 7;
  private static final long HERO = 74;
  private static final Map<String, ItemConstant> ITEMS =
      Map.of(
          "radiance", constant(1, 5_150),
          "maelstrom", constant(2, 2_700),
          "manta", constant(3, 4_600),
          "bfury", constant(4, 4_100),
          "blink", constant(5, 2_250),
          "travel_boots", constant(6, 7_500));
  private static final Settings SETTINGS = new Settings(0.3, 300, 5);
  private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");

  private final ItemRepository itemRepo = mock(ItemRepository.class);
  private final PlayerGameStatisticRepo statRepo = mock(PlayerGameStatisticRepo.class);
  private final PlayerRoleRepo roleRepo = mock(PlayerRoleRepo.class);
  private final ItemKits kits =
      new ItemKits() {
        @Override
        public Optional<ItemConstant> item(String key) {
          return Optional.ofNullable(ITEMS.get(key));
        }

        @Override
        public Optional<String> key(long id) {
          return ITEMS.entrySet().stream()
              .filter(e -> e.getValue().getId() == id)
              .map(Map.Entry::getKey)
              .findFirst();
        }
      };
  private final PopularItems popular = mock(PopularItems.class);
  private final AlternativeFinder finder =
      new AlternativeFinder(
          itemRepo, statRepo, roleRepo, kits, popular, Clock.fixed(NOW, ZoneOffset.UTC));

  private static ItemConstant constant(long id, long cost) {
    return ItemConstant.builder().id(id).cost(cost).build();
  }

  private void player(Long laneRole) {
    when(statRepo.findAllByMatchId(MATCH))
        .thenReturn(
            List.of(
                PlayerMatchStatisticDomain.builder()
                    .matchId(MATCH)
                    .playerSlot(0L)
                    .heroId(HERO)
                    .laneRole(laneRole)
                    .build()));
  }

  private void role(Integer role) {
    when(roleRepo.findByMatchId(MATCH))
        .thenReturn(
            role == null
                ? List.of()
                : List.of(PlayerRoleDomain.builder().playerSlot(0L).role(role).build()));
  }

  private static Purchase radiance(List<String> fixed) {
    return new Purchase(MATCH, 0, "radiance", Set.of("radiance"), 1_500, 5_150, fixed);
  }

  private void rows(Object[]... rows) {
    when(itemRepo.findHeroPurchases(
            eq(HERO), any(), any(), anyLong(), anyLong(), eq(10), eq(MATCH), anyLong()))
        .thenReturn(List.of(rows));
  }

  private static List<Object[]> games(long itemId, int total, int wins, Double lead) {
    List<Object[]> rows = new ArrayList<>();
    for (int i = 0; i < total; i++) {
      rows.add(new Object[] {itemId, 1_500L, i < wins ? 1L : 0L, lead});
    }
    return rows;
  }

  private void rowsOf(List<Object[]>... groups) {
    List<Object[]> all = new ArrayList<>();
    for (List<Object[]> group : groups) {
      all.addAll(group);
    }
    rows(all.toArray(new Object[0][]));
  }

  @Test
  @SuppressWarnings("unchecked")
  void candidatesAreTheItemsWithinTheCostBandComparedWithTheItemsOwnGames() {
    player(null);
    role(1);
    rowsOf(
        games(1, 10, 5, 1_000.0),
        games(3, 8, 6, 2_500.0),
        games(4, 6, 3, null),
        games(5, 20, 10, 0.0),
        games(6, 20, 10, 0.0));

    Result result = finder.find(radiance(List.of()), SETTINGS);

    assertThat(result.games()).isEqualTo(10);
    assertThat(result.alternatives())
        .extracting(Alternative::itemKey)
        .containsExactly("manta", "bfury");
    Alternative manta = result.alternatives().getFirst();
    assertThat(manta.games()).isEqualTo(8);
    assertThat(manta.winRateDelta()).isEqualTo(0.25);
    assertThat(manta.leadDelta()).isEqualTo(1_500.0);
    Alternative bfury = result.alternatives().get(1);
    assertThat(bfury.winRateDelta()).isEqualTo(0.0);
    assertThat(bfury.leadDelta()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void aSideWithTooFewGamesShowsNoNumbers() {
    player(null);
    role(1);
    rowsOf(games(1, 10, 5, 0.0), games(3, 4, 4, 0.0));
    Result few = finder.find(radiance(List.of()), SETTINGS);
    assertThat(few.alternatives()).hasSize(1);
    assertThat(few.alternatives().getFirst().games()).isEqualTo(4);
    assertThat(few.alternatives().getFirst().winRateDelta()).isNull();

    rowsOf(games(1, 4, 4, 0.0), games(3, 9, 9, 0.0));
    Result ownFew = finder.find(radiance(List.of()), SETTINGS);
    assertThat(ownFew.alternatives().getFirst().winRateDelta()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void theCatalogListReplacesTheCandidatesAndIgnoresTheCostBand() {
    player(null);
    role(1);
    rowsOf(games(1, 10, 5, 0.0), games(2, 7, 7, 0.0), games(3, 9, 0, 0.0), games(5, 30, 0, 0.0));

    Result result = finder.find(radiance(List.of("maelstrom", "manta")), SETTINGS);

    assertThat(result.alternatives())
        .extracting(Alternative::itemKey)
        .containsExactly("manta", "maelstrom");
  }

  @Test
  void theQueryUsesTheTimeBandTheRoleRowAndTheTwelveMonthWindow() {
    player(1L);
    role(3);
    rows();

    finder.find(radiance(List.of()), SETTINGS);

    ArgumentCaptor<Long> from = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Long> to = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Long> since = ArgumentCaptor.forClass(Long.class);
    verify(itemRepo)
        .findHeroPurchases(
            eq(HERO),
            eq(3),
            isNull(),
            from.capture(),
            to.capture(),
            eq(10),
            eq(MATCH),
            since.capture());
    assertThat(from.getValue()).isEqualTo(1_200);
    assertThat(to.getValue()).isEqualTo(1_800);
    assertThat(since.getValue()).isEqualTo(NOW.minusSeconds(365L * 86_400).getEpochSecond());
  }

  @Test
  void withoutARoleRowOnlyTheMiddleLaneStandsInForARole() {
    player(2L);
    role(null);
    rows();
    finder.find(radiance(List.of()), SETTINGS);
    verify(itemRepo)
        .findHeroPurchases(
            eq(HERO), eq(2), eq(2L), anyLong(), anyLong(), eq(10), eq(MATCH), anyLong());
  }

  @Test
  void aSideLaneWithoutARoleRowOrNoLaneAtAllGivesNothing() {
    player(1L);
    role(null);
    assertThat(finder.find(radiance(List.of()), SETTINGS)).isEqualTo(Result.NONE);

    player(null);
    assertThat(newFinder().find(radiance(List.of()), SETTINGS)).isEqualTo(Result.NONE);
    verify(itemRepo, never())
        .findHeroPurchases(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void aMatchIsLoadedOnceAndTheSameQueryIsAskedOnce() {
    player(null);
    role(1);
    rows();

    finder.find(radiance(List.of()), SETTINGS);
    finder.find(radiance(List.of()), SETTINGS);

    verify(statRepo, times(1)).findAllByMatchId(MATCH);
    verify(roleRepo, times(1)).findByMatchId(MATCH);
    verify(itemRepo, times(1))
        .findHeroPurchases(any(), any(), any(), anyLong(), anyLong(), any(), any(), anyLong());
  }

  @Test
  @SuppressWarnings("unchecked")
  void popularItemsAreOnlyAskedWhenNoCandidateHasEnoughGames() {
    player(null);
    role(1);
    when(popular.near(eq(HERO), anyDouble(), eq(5_150L), eq(0.3), any()))
        .thenReturn(List.of("manta"));
    rowsOf(games(1, 10, 5, null), games(3, 2, 1, null));

    Result few = finder.find(radiance(List.of()), SETTINGS);

    assertThat(few.popular()).containsExactly("manta");

    rowsOf(games(1, 10, 5, null), games(3, 8, 4, null));
    Result enough = newFinder().find(radiance(List.of()), SETTINGS);

    assertThat(enough.popular()).isEmpty();
  }

  private AlternativeFinder newFinder() {
    return new AlternativeFinder(
        itemRepo, statRepo, roleRepo, kits, popular, Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
