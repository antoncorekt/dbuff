package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.ItemEfficiencyDomain;
import com.ako.dbuff.dao.repo.CreepKillRepo;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.service.replay.report.FarmChartBuilder.FarmChart;
import com.ako.dbuff.service.replay.report.chart.ChartRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FarmChartBuilderTest {
  private final CreepKillRepo creeps = mock(CreepKillRepo.class);
  private final PlayerActivitySegmentRepo segments = mock(PlayerActivitySegmentRepo.class);
  private final ItemEfficiencyRepo items = mock(ItemEfficiencyRepo.class);
  private final ReportHeroNames names = mock(ReportHeroNames.class);
  private final FarmChartBuilder builder =
      new FarmChartBuilder(creeps, segments, items, names, internal -> "Battle Fury");

  private static CreepKillDomain kill(long slot, String creepClass, double seconds, int gold) {
    return CreepKillDomain.builder()
        .playerSlot(slot)
        .creepClass(creepClass)
        .seconds(seconds)
        .gold(gold)
        .denied(false)
        .build();
  }

  private static ItemEfficiencyDomain item(
      long slot, Double purchase, Double payback, Double ratio) {
    return ItemEfficiencyDomain.builder()
        .playerSlot(slot)
        .entryKey("farming_items")
        .itemKey("bfury")
        .kind("farming-group")
        .purchaseSeconds(purchase)
        .paybackSeconds(payback)
        .ratio(ratio)
        .useCount(10)
        .gain(500.0)
        .build();
  }

  private void kills(CreepKillDomain... rows) {
    when(creeps.findByMatchId(1L)).thenReturn(List.of(rows));
    when(segments.findByMatchId(1L)).thenReturn(List.of());
    when(names.bySlot(1L)).thenReturn(Map.of(0L, "Pudge", 1L, "Lina", 2L, "Axe"));
  }

  private List<CreepKillDomain> jungleHero(long slot) {
    List<CreepKillDomain> rows = new ArrayList<>();
    for (int m = 0; m < 20; m++) {
      rows.add(kill(slot, m < 6 ? "LANE_MELEE" : "NEUTRAL", m * 60.0, 200));
    }
    return rows;
  }

  @Test
  void saysWhereTheHeroFarmedAndHowTheItemPaidBackAndDrawsIt() {
    when(creeps.findByMatchId(1L)).thenReturn(jungleHero(0));
    when(segments.findByMatchId(1L)).thenReturn(List.of());
    when(names.bySlot(1L)).thenReturn(Map.of(0L, "Pudge"));
    when(items.findByMatchId(1L)).thenReturn(List.of(item(0, 840.0, 1900.0, 1.4)));

    FarmChart chart = builder.build(1L, Set.of(0L), 2).orElseThrow();

    assertThat(chart.verdict())
        .startsWith("Pudge farmed mostly in the jungle from ")
        .endsWith("Battle Fury paid back at 31:40.");
    assertThat(chart.spec().panels()).hasSize(1);
    assertThat(chart.spec().panels().get(0).markers()).hasSize(1);
    assertThat(chart.spec().panels().get(0).markers().get(0).label())
        .isEqualTo("Battle Fury (14:00), paid back at 31:40");
    assertThat(new ChartRenderer().render(chart.spec())).isNotEmpty();
  }

  @Test
  void anItemThatNeverPaidBackSaysSo() {
    when(creeps.findByMatchId(1L)).thenReturn(jungleHero(0));
    when(segments.findByMatchId(1L)).thenReturn(List.of());
    when(names.bySlot(1L)).thenReturn(Map.of(0L, "Pudge"));
    when(items.findByMatchId(1L)).thenReturn(List.of(item(0, 840.0, null, 0.5)));

    assertThat(builder.build(1L, Set.of(0L), 2).orElseThrow().verdict())
        .endsWith("Battle Fury not paid back.");
  }

  @Test
  void capsTheHeroesAndKeepsTheTrackedOnes() {
    List<CreepKillDomain> rows = new ArrayList<>();
    for (long slot : new long[] {0, 1, 2}) {
      rows.addAll(jungleHero(slot));
    }
    kills(rows.toArray(CreepKillDomain[]::new));
    when(items.findByMatchId(1L)).thenReturn(List.of());

    FarmChart chart = builder.build(1L, Set.of(1L, 2L), 1).orElseThrow();

    assertThat(chart.spec().panels()).extracting(p -> p.label()).containsExactly("Lina");
    assertThat(chart.lines()).isEmpty();
  }

  @Test
  void aSecondHeroGetsItsOwnLine() {
    List<CreepKillDomain> rows = new ArrayList<>(jungleHero(0));
    rows.addAll(jungleHero(1));
    kills(rows.toArray(CreepKillDomain[]::new));
    when(items.findByMatchId(1L)).thenReturn(List.of());

    FarmChart chart = builder.build(1L, Set.of(0L, 1L), 2).orElseThrow();

    assertThat(chart.verdict()).startsWith("Pudge");
    assertThat(chart.lines()).singleElement().asString().startsWith("Lina");
  }

  @Test
  void withoutCreepKillsTheItemsAreTextAndNothingIsDrawn() {
    when(creeps.findByMatchId(1L)).thenReturn(List.of());
    when(segments.findByMatchId(1L)).thenReturn(List.of());
    when(names.bySlot(1L)).thenReturn(Map.of(0L, "Pudge"));
    when(items.findByMatchId(1L)).thenReturn(List.of(item(0, 840.0, 1900.0, 1.4)));

    FarmChart chart = builder.build(1L, Set.of(0L), 2).orElseThrow();

    assertThat(chart.spec()).isNull();
    assertThat(chart.verdict()).isEqualTo(FarmChartBuilder.NO_CREEP_KILLS);
    assertThat(chart.lines()).containsExactly("Pudge: Battle Fury paid back at 31:40.");
  }

  @Test
  void hasNothingToSayWithNeitherCreepKillsNorItems() {
    when(creeps.findByMatchId(1L)).thenReturn(List.of());
    when(segments.findByMatchId(1L)).thenReturn(List.of());
    when(items.findByMatchId(1L)).thenReturn(List.of());
    when(names.bySlot(1L)).thenReturn(Map.of());

    assertThat(builder.build(1L, Set.of(0L), 2)).isEmpty();
  }

  @Test
  void aBadItemRowOrBadKillRowIsSkippedNotThrown() {
    List<CreepKillDomain> rows = new ArrayList<>(jungleHero(0));
    rows.add(CreepKillDomain.builder().playerSlot(0L).build());
    rows.add(null);
    when(creeps.findByMatchId(1L)).thenReturn(rows);
    when(segments.findByMatchId(1L)).thenReturn(List.of());
    when(names.bySlot(1L)).thenReturn(Map.of());
    ItemEfficiencyDomain broken = item(0, 840.0, 1900.0, 1.4);
    broken.setFigures("{not json");
    when(items.findByMatchId(1L)).thenReturn(List.of(broken));

    FarmChart chart = builder.build(1L, Set.of(0L), 2).orElseThrow();

    assertThat(chart.verdict()).startsWith("Player 0 farmed mostly in the jungle");
    assertThat(chart.spec().panels().get(0).markers()).isEmpty();
  }
}
