package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.report.DefenseChartBuilder.DefenseChart;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphDefenseSectionTest {
  private final DefenseChartBuilder builder = mock(DefenseChartBuilder.class);
  private final ChartService charts = mock(ChartService.class);
  private final GraphDefenseSection section =
      new GraphDefenseSection(builder, charts, new PlainViewRenderer());
  private final ReplayReportInput input = new ReplayReportInput(7L, null, Set.of(), Map.of(), 3);

  private void builds(Optional<DefenseChart> chart) {
    when(builder.build(7L)).thenReturn(chart);
    when(charts.png(anyString(), any())).thenReturn(Optional.of(new byte[] {9}));
  }

  @Test
  void isOffByDefaultAtStandardDetailInPlace65() {
    assertThat(section.name()).isEqualTo("graph-defense");
    assertThat(section.order()).isEqualTo(65);
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(section.minimumDetail()).isEqualTo(ReportDetail.STANDARD);
    assertThat(section.summary())
        .isEqualTo("Team HP, estimated armor and effective HP over the game");
  }

  @Test
  void carriesVerdictBulletsMissingRowsAndTheEstimateFootnote() {
    builds(
        Optional.of(
            new DefenseChart(
                new ChartSpec("t", 60, List.of(), List.of(), List.of(), List.of()),
                "At 20:00 Dire had 18% more effective HP against the damage Radiant dealt",
                List.of("At 20:00: Radiant x.", "By 20:00 Radiant took 60% magical damage."))));

    SectionView view = section.view(input).orElseThrow();

    assertThat(view.verdict()).startsWith("At 20:00 Dire had 18% more");
    assertThat(view.bullets()).contains("By 20:00 Radiant took 60% magical damage.");
    assertThat(view.footnote())
        .contains(
            "Armor and resistance are estimated from base, agility and items; auras, talents and buffs are not counted.")
        .contains("End-of-game");
    assertThat(view.image().name()).isEqualTo("defense-7.png");
  }

  @Test
  void anEmptyMatchGivesNoView() {
    builds(Optional.empty());

    assertThat(section.view(input)).isEmpty();
    assertThat(section.lines(input)).isEmpty();
  }
}
