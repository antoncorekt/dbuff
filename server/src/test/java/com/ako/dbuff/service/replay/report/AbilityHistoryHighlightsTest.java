package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AbilityHistoryHighlightsTest {
  private static final long PLAYER = 77L;

  private final AbilityRepo repo = mock(AbilityRepo.class);
  private final AbilityHistoryHighlights source =
      new AbilityHistoryHighlights(repo, new ReplayReportProperties());

  private static DbufInstanceConfigDomain instance() {
    DbufInstanceConfigDomain instance = new DbufInstanceConfigDomain();
    PlayerDomain player = new PlayerDomain();
    player.setId(PLAYER);
    instance.setPlayers(Set.of(player));
    return instance;
  }

  private static ReplayReportInput input(DbufInstanceConfigDomain instance) {
    return new ReplayReportInput(1L, null, Set.of(0L), Map.of(), 3, null, instance);
  }

  private void history(long damage, double average, long games) {
    when(repo.findAllByMatchId(1L))
        .thenReturn(
            List.of(
                AbilityDomain.builder()
                    .matchId(1L)
                    .playerSlot(0L)
                    .abilityId(5L)
                    .playerId(PLAYER)
                    .name("morphling_waveform")
                    .prettyName("Ball Lightning")
                    .damageDealt(damage)
                    .build()));
    List<Object[]> rows = new java.util.ArrayList<>();
    rows.add(new Object[] {5L, "morphling_waveform", "Ball Lightning", average, 0.0, 1.0, games});
    when(repo.findAvgStatsByPlayerAbilities(PLAYER, 1L, 1L)).thenReturn(rows);
  }

  @Test
  void theRatioToTheOwnAverageIsScaledAndWorded() {
    history(12300, 3900.0, 8);

    assertThat(source.highlights(input(instance())))
        .singleElement()
        .satisfies(
            h -> {
              assertThat(h.source()).isEqualTo("ability-history");
              assertThat(h.seconds()).isNull();
              assertThat(h.score()).isEqualTo(Highlight.scale(12300 / 3900.0, 1.5, 4));
              assertThat(h.text())
                  .isEqualTo(
                      "Ball Lightning did 12.3k damage, 3.2x this player's average of 3.9k over 8"
                          + " games");
            });
  }

  @Test
  void fewerThanFiveEarlierGamesGiveNothing() {
    history(12300, 3900.0, 4);

    assertThat(source.highlights(input(instance()))).isEmpty();
  }

  @Test
  void aZeroAverageGivesNothing() {
    history(12300, 0.0, 8);

    assertThat(source.highlights(input(instance()))).isEmpty();
  }

  @Test
  void damageUnderAThousandGivesNothing() {
    history(900, 100.0, 8);

    assertThat(source.highlights(input(instance()))).isEmpty();
  }

  @Test
  void withoutAnInstanceThereAreNoCandidates() {
    assertThat(source.highlights(input(null))).isEmpty();
    verifyNoInteractions(repo);
  }
}
