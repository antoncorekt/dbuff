package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.AdvantageChartBuilder.AdvantageChart;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A picture of the match as a race: who led in gold and experience when, which fights decided it
 * and where the big swings came from. Off unless switched on, or asked for with the section name.
 */
@Component
@RequiredArgsConstructor
public class GraphAdvantageSection implements ReplayReportSection {
  static final String NOT_DRAWN = "The graph could not be drawn this time.";

  private final AdvantageChartBuilder builder;
  private final ChartService charts;
  private final PlainViewRenderer plain;

  @Override
  public String name() {
    return "graph-advantage";
  }

  @Override
  public int order() {
    return 90;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    return view(input).map(v -> plain.lines(v, true)).orElse(List.of());
  }

  @Override
  public Optional<ImageAttachment> image(ReplayReportInput input) {
    return view(input).map(SectionView::image);
  }

  /**
   * The view of one delivery is built once: the plain path asks for the lines and the image
   * separately, and both must come from the same read.
   */
  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    Built last = built;
    if (last != null && last.input == input) {
      return last.view;
    }
    Optional<SectionView> view = build(input);
    built = new Built(input, view);
    return view;
  }

  private record Built(ReplayReportInput input, Optional<SectionView> view) {}

  private volatile Built built;

  private Optional<SectionView> build(ReplayReportInput input) {
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    return builder
        .build(input.matchId(), yours, input.maxFindings())
        .map(chart -> view(input.matchId(), yours, chart));
  }

  private SectionView view(long matchId, TeamSide yours, AdvantageChart chart) {
    String key =
        "advantage:"
            + matchId
            + ":"
            + AdvantageChartBuilder.viewpoint(yours)
            + ":"
            + chart.spec().fingerprint();
    Optional<ImageAttachment> image =
        charts
            .png(key, chart::spec)
            .map(png -> new ImageAttachment("advantage-" + matchId + ".png", png));
    return new SectionView(
        "Team advantage",
        chart.verdict(),
        Accent.NEUTRAL,
        image.isPresent() ? List.of() : List.of(NOT_DRAWN),
        null,
        image.orElse(null));
  }
}
