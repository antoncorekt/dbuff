package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphAdvantageSectionTest {
  private final AdvantageChartBuilder builder = mock(AdvantageChartBuilder.class);
  private final ChartService charts = mock(ChartService.class);
  private final GraphAdvantageSection section =
      new GraphAdvantageSection(builder, charts, new PlainViewRenderer());
  private final ReplayReportInput input = new ReplayReportInput(7L, null, Set.of(0L), Map.of(), 3);

  private static AdvantageChartBuilder.AdvantageChart chart() {
    return new AdvantageChartBuilder.AdvantageChart(
        new ChartSpec("t", 60, List.of(), List.of(), List.of(), List.of()), "Radiant led.");
  }

  @Test
  void isOffByDefaultAtStandardDetailUnderItsOwnName() {
    assertThat(section.name()).isEqualTo("graph-advantage");
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(section.minimumDetail()).isEqualTo(ReportDetail.STANDARD);
    ReplayReportProperties properties = new ReplayReportProperties();
    assertThat(new SectionSwitch(properties, List.of(section)).isEnabled(section)).isFalse();
  }

  @Test
  void carriesTheImageAndTheVerdictWhenDrawn() {
    when(builder.build(7L, TeamSide.RADIANT, 3)).thenReturn(Optional.of(chart()));
    when(charts.png(anyString(), any())).thenReturn(Optional.of(new byte[] {9}));

    SectionView view = section.view(input).orElseThrow();

    assertThat(view.image().name()).isEqualTo("advantage-7.png");
    assertThat(view.verdict()).isEqualTo("Radiant led.");
    assertThat(view.bullets()).isEmpty();
    assertThat(section.lines(input)).containsExactly("**Team advantage**", "Radiant led.");
    assertThat(section.image(input)).map(ImageAttachment::name).contains("advantage-7.png");
    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
    verify(charts, org.mockito.Mockito.atLeastOnce()).png(key.capture(), any());
    assertThat(key.getValue()).startsWith("advantage:7:RADIANT:").hasSizeGreaterThan(20);
    verify(builder, org.mockito.Mockito.times(1)).build(7L, TeamSide.RADIANT, 3);
  }

  @Test
  void theCacheKeyChangesWhenTheDataBehindThePictureChanges() {
    ChartSpec other = new ChartSpec("t", 120, List.of(), List.of(), List.of(), List.of());

    assertThat(chart().spec().fingerprint()).isNotEqualTo(other.fingerprint());
    assertThat(chart().spec().fingerprint()).isEqualTo(chart().spec().fingerprint());
  }

  @Test
  void aChartThatCouldNotBeDrawnPostsTheVerdictAndAnApologyInsteadOfFailing() {
    when(builder.build(7L, TeamSide.RADIANT, 3)).thenReturn(Optional.of(chart()));
    when(charts.png(anyString(), any())).thenReturn(Optional.empty());

    SectionView view = section.view(input).orElseThrow();

    assertThat(view.image()).isNull();
    assertThat(section.image(input)).isEmpty();
    assertThat(section.lines(input))
        .containsExactly("**Team advantage**", "Radiant led.", GraphAdvantageSection.NOT_DRAWN);
  }

  @Test
  void hasNothingToSayWithoutTimelines() {
    when(builder.build(7L, TeamSide.RADIANT, 3)).thenReturn(Optional.empty());

    assertThat(section.view(input)).isEmpty();
    assertThat(section.lines(input)).isEmpty();
    assertThat(section.image(input)).isEmpty();
  }
}
