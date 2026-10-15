package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.report.FarmChartBuilder.FarmChart;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphFarmSectionTest {
  private final FarmChartBuilder builder = mock(FarmChartBuilder.class);
  private final ChartService charts = mock(ChartService.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final GraphFarmSection section =
      new GraphFarmSection(builder, charts, new PlainViewRenderer(), properties);
  private final ReplayReportInput input = new ReplayReportInput(7L, null, Set.of(0L), Map.of(), 3);

  private static FarmChart chart(String verdict, List<String> lines) {
    return new FarmChart(
        new ChartSpec("t", 60, List.of(), List.of(), List.of(), List.of()), verdict, lines);
  }

  @Test
  void isOffByDefaultAndTakesTheHeroCapFromTheProperties() {
    assertThat(section.name()).isEqualTo("graph-farm");
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(section.minimumDetail()).isEqualTo(ReportDetail.STANDARD);
    assertThat(new SectionSwitch(properties, List.of(section)).isEnabled(section)).isFalse();
    assertThat(properties.getGraphFarmMaxHeroes()).isEqualTo(2);
  }

  @Test
  void carriesTheImageAndTheVerdictFirstAndBuildsTheViewOnce() {
    when(builder.build(7L, Set.of(0L), 2))
        .thenReturn(Optional.of(chart("Pudge farmed.", List.of("Lina farmed."))));
    when(charts.png(anyString(), any())).thenReturn(Optional.of(new byte[] {9}));

    SectionView view = section.view(input).orElseThrow();

    assertThat(view.image().name()).isEqualTo("farm-7.png");
    assertThat(section.lines(input))
        .containsExactly("**Farm effectiveness**", "Pudge farmed.", "Lina farmed.");
    assertThat(section.image(input)).isPresent();
    verify(builder, times(1)).build(7L, Set.of(0L), 2);
  }

  @Test
  void aChartThatCouldNotBeDrawnPostsTheVerdictAndAnApology() {
    when(builder.build(7L, Set.of(0L), 2))
        .thenReturn(Optional.of(chart("Pudge farmed.", List.of())));
    when(charts.png(anyString(), any())).thenReturn(Optional.empty());

    assertThat(section.image(input)).isEmpty();
    assertThat(section.lines(input))
        .containsExactly("**Farm effectiveness**", "Pudge farmed.", GraphSection.NOT_DRAWN);
  }

  @Test
  void aMatchWithoutCreepKillsIsTextOnlyAndNeverAsksForADrawing() {
    when(builder.build(7L, Set.of(0L), 2))
        .thenReturn(
            Optional.of(
                new FarmChart(
                    null,
                    FarmChartBuilder.NO_CREEP_KILLS,
                    List.of("Pudge: Battle Fury paid back at 31:40."))));

    assertThat(section.lines(input))
        .containsExactly(
            "**Farm effectiveness**",
            FarmChartBuilder.NO_CREEP_KILLS,
            "Pudge: Battle Fury paid back at 31:40.");
    assertThat(section.image(input)).isEmpty();
    verify(charts, times(0)).png(anyString(), any());
  }

  @Test
  void longTextIsCutAndNothingToSayGivesNoSection() {
    when(builder.build(7L, Set.of(0L), 2))
        .thenReturn(Optional.of(chart("word ".repeat(200), List.of())));
    when(charts.png(anyString(), any())).thenReturn(Optional.of(new byte[] {9}));

    assertThat(section.view(input).orElseThrow().verdict()).hasSizeLessThanOrEqualTo(300);
    when(builder.build(8L, Set.of(0L), 2)).thenReturn(Optional.empty());
    assertThat(section.lines(new ReplayReportInput(8L, null, Set.of(0L), Map.of(), 3))).isEmpty();
  }
}
