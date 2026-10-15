package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.report.IncomeChartBuilder.IncomeChart;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphIncomeSectionTest {
  private final IncomeChartBuilder builder = mock(IncomeChartBuilder.class);
  private final ChartService charts = mock(ChartService.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final GraphIncomeSection section =
      new GraphIncomeSection(builder, charts, new PlainViewRenderer(), properties);
  private final ReplayReportInput input = new ReplayReportInput(7L, null, Set.of(0L), Map.of(), 3);

  private static IncomeChart chart(double end) {
    return new IncomeChart(
        new ChartSpec("t", end, List.of(), List.of(), List.of(), List.of()),
        "Biggest income drop: Axe.",
        List.of("Axe's Hand of Midas paid for itself at 10:00."));
  }

  private void builds(Optional<IncomeChart> chart) {
    when(builder.build(anyLong(), any(), anyInt(), anyInt(), anyInt())).thenReturn(chart);
  }

  @Test
  void isOffByDefaultAtStandardDetailUnderItsOwnName() {
    assertThat(section.name()).isEqualTo("graph-income");
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(section.minimumDetail()).isEqualTo(ReportDetail.STANDARD);
    assertThat(new SectionSwitch(properties, List.of(section)).isEnabled(section)).isFalse();
    assertThat(properties.getGraphIncomeMaxHeroes()).isEqualTo(2);
  }

  @Test
  void carriesTheImageTheVerdictFirstAndTheLines() {
    builds(Optional.of(chart(60)));
    when(charts.png(anyString(), any())).thenReturn(Optional.of(new byte[] {9}));

    SectionView view = section.view(input).orElseThrow();

    assertThat(view.image().name()).isEqualTo("income-7.png");
    assertThat(view.verdict()).isEqualTo("Biggest income drop: Axe.");
    assertThat(section.lines(input))
        .containsExactly(
            "**Hero income**",
            "Biggest income drop: Axe.",
            "Axe's Hand of Midas paid for itself at 10:00.");
    verify(builder, times(1)).build(7L, Set.of(0L), 2, 3, properties.getMaxBehaviourLineLength());
  }

  @Test
  void theCacheKeyHoldsTheMatchAndTheDataFingerprint() {
    builds(Optional.of(chart(60)));
    when(charts.png(anyString(), any())).thenReturn(Optional.of(new byte[] {9}));
    section.view(input);
    ArgumentCaptor<String> first = ArgumentCaptor.forClass(String.class);
    verify(charts, atLeastOnce()).png(first.capture(), any());

    builds(Optional.of(chart(120)));
    ReplayReportInput other = new ReplayReportInput(7L, null, Set.of(0L), Map.of(), 3);
    section.view(other);
    ArgumentCaptor<String> second = ArgumentCaptor.forClass(String.class);
    verify(charts, atLeastOnce()).png(second.capture(), any());

    assertThat(first.getValue()).startsWith("income:7:");
    assertThat(second.getValue()).isNotEqualTo(first.getValue());
  }

  @Test
  void aPictureThatCouldNotBeDrawnLeavesTheTextAndAnApology() {
    builds(Optional.of(chart(60)));
    when(charts.png(anyString(), any())).thenReturn(Optional.empty());

    assertThat(section.view(input).orElseThrow().image()).isNull();
    assertThat(section.image(input)).isEmpty();
    assertThat(section.lines(input)).contains(GraphSection.NOT_DRAWN);
  }

  @Test
  void nothingToDrawMeansNoSection() {
    builds(Optional.empty());

    assertThat(section.view(input)).isEmpty();
    assertThat(section.lines(input)).isEmpty();
  }

  @Test
  void aBuilderThatFailsOnABadRowDoesNotFailTheReport() {
    when(builder.build(anyLong(), any(), anyInt(), anyInt(), anyInt()))
        .thenThrow(new IllegalStateException("bad row"));

    assertThat(section.view(input)).isEmpty();
    assertThat(section.lines(input)).isEmpty();
  }
}
