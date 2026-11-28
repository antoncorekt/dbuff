package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.MissingDefenseItemDomain;
import com.ako.dbuff.dao.model.TeamDefenseItemDomain;
import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import com.ako.dbuff.dao.repo.MissingDefenseItemRepo;
import com.ako.dbuff.dao.repo.TeamDefenseItemRepo;
import com.ako.dbuff.dao.repo.TeamDefensePointRepo;
import com.ako.dbuff.service.replay.report.DefenseChartBuilder.DefenseChart;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefenseChartBuilderTest {
  private final TeamDefensePointRepo points = mock(TeamDefensePointRepo.class);
  private final TeamDefenseItemRepo items = mock(TeamDefenseItemRepo.class);
  private final MissingDefenseItemRepo missing = mock(MissingDefenseItemRepo.class);
  private final DefenseReportProperties properties = new DefenseReportProperties();
  private final DefenseChartBuilder builder =
      new DefenseChartBuilder(points, items, missing, properties);

  private static TeamDefensePointDomain point(String team, int minute, Integer mix, String cp) {
    return TeamDefensePointDomain.builder()
        .matchId(1L)
        .team(team)
        .minute(minute)
        .maxHpSum(minute * 100)
        .ehpPhys(minute * 150)
        .ehpMag(minute * 120)
        .ehpMix(mix)
        .armorAvgX10(50)
        .magicResistAvgX10(250)
        .checkpoint(cp)
        .build();
  }

  private static List<TeamDefensePointDomain> game(int last) {
    List<TeamDefensePointDomain> rows = new ArrayList<>();
    for (String team : List.of("RADIANT", "DIRE")) {
      for (int m = 0; m <= last; m++) {
        String cp = m == 10 || m == 20 ? "M" + m : null;
        int mix = m * 100 * ("DIRE".equals(team) && m == 20 ? 2 : 1);
        rows.add(point(team, m, mix, cp));
      }
      rows.add(point(team, last, last * 100, "END"));
    }
    return rows;
  }

  private void stored(List<TeamDefensePointDomain> rows) {
    when(points.findByMatchId(1L)).thenReturn(rows);
    when(items.findByMatchId(1L)).thenReturn(List.of());
    when(missing.findByMatchId(1L)).thenReturn(List.of());
  }

  @Test
  void stepTwoKeepsEverySecondMinute() {
    stored(game(24));

    ChartSpec spec = builder.build(1L).orElseThrow().spec();

    assertThat(spec.panels()).hasSize(4);
    ChartSpec.Series radiant = spec.panels().getFirst().series().getFirst();
    assertThat(radiant.seconds()).hasSize(13);
    assertThat(radiant.seconds()[1]).isEqualTo(120);
    assertThat(spec.markers()).hasSize(2);
  }

  @Test
  void stepIsConfigurable() {
    properties.setStepMinutes(5);
    stored(game(24));

    assertThat(
            builder
                .build(1L)
                .orElseThrow()
                .spec()
                .panels()
                .getFirst()
                .series()
                .getFirst()
                .seconds())
        .hasSize(5);
  }

  @Test
  void skipsMinutesWithoutEffectiveHp() {
    List<TeamDefensePointDomain> rows = game(10);
    rows.stream()
        .filter(p -> "DIRE".equals(p.getTeam()) && p.getMinute() == 4 && p.getCheckpoint() == null)
        .forEach(p -> p.setEhpMix(null));
    stored(rows);

    ChartSpec spec = builder.build(1L).orElseThrow().spec();

    ChartSpec.Panel mixed = spec.panels().get(3);
    assertThat(mixed.series().get(0).seconds()).hasSize(6);
    assertThat(mixed.series().get(1).seconds()).hasSize(5);
  }

  @Test
  void countsPinsForItemsAndWritesTheirNames() {
    stored(game(10));
    when(items.findByMatchId(1L))
        .thenReturn(
            List.of(
                TeamDefenseItemDomain.builder().team("DIRE").item("pipe").atSecond(300).build(),
                TeamDefenseItemDomain.builder()
                    .team("RADIANT")
                    .item("black_king_bar")
                    .atSecond(200)
                    .build()));

    ChartSpec spec = builder.build(1L).orElseThrow().spec();

    assertThat(spec.panels().get(3).pins())
        .extracting(ChartSpec.Pin::label)
        .containsExactly("Black King Bar", "Pipe");
    assertThat(spec.panels().get(0).pins()).isEmpty();
  }

  @Test
  void noRowsGiveNoChart() {
    stored(List.of());

    assertThat(builder.build(1L)).isEmpty();
  }

  @Test
  void verdictNamesTheLargestGapAndBulletsFollowCheckpointsAndMissingRows() {
    stored(game(24));
    when(missing.findByMatchId(1L))
        .thenReturn(
            List.of(
                MissingDefenseItemDomain.builder()
                    .minute(20)
                    .evidence("By 20:00 no magic item.")
                    .build()));

    DefenseChart chart = builder.build(1L).orElseThrow();

    assertThat(chart.verdict())
        .isEqualTo("At 20:00 Dire had 100% more effective HP against the damage Radiant dealt");
    assertThat(chart.bullets()).hasSize(4);
    assertThat(chart.bullets().getLast()).isEqualTo("By 20:00 no magic item.");
    assertThat(chart.bullets().getFirst()).startsWith("At 10:00: Radiant ");
    assertThat(chart.bullets().get(2)).startsWith("At the end: ");
  }
}
