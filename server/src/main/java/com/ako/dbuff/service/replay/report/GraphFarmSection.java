package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.report.FarmChartBuilder.FarmChart;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * How the tracked heroes farmed: lane against camp gold per minute, and whether their farming items
 * paid back. A match whose creep kills were not stored gets the item verdicts as text. Off unless
 * switched on, or asked for with the section name.
 */
@Component
@RequiredArgsConstructor
public class GraphFarmSection implements ReplayReportSection {
  static final String NOT_DRAWN = "The graph could not be drawn this time.";
  private static final int MAX_LINE_LENGTH = 300;

  private final FarmChartBuilder builder;
  private final ChartService charts;
  private final PlainViewRenderer plain;
  private final ReplayReportProperties properties;

  private record Built(ReplayReportInput input, Optional<SectionView> view) {}

  private volatile Built built;

  @Override
  public String name() {
    return "graph-farm";
  }

  @Override
  public int order() {
    return 92;
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

  /** Built once per delivery: the plain path asks for lines and image separately. */
  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    Built last = built;
    if (last != null && last.input == input) {
      return last.view;
    }
    Optional<SectionView> view =
        builder
            .build(input.matchId(), input.focusSlots(), properties.getGraphFarmMaxHeroes())
            .map(chart -> view(input.matchId(), chart));
    built = new Built(input, view);
    return view;
  }

  private SectionView view(long matchId, FarmChart chart) {
    Optional<ImageAttachment> image =
        chart.spec() == null
            ? Optional.empty()
            : charts
                .png("farm:" + matchId + ":" + chart.spec().fingerprint(), chart::spec)
                .map(png -> new ImageAttachment("farm-" + matchId + ".png", png));
    List<String> lines = chart.lines().stream().map(l -> LineText.cut(l, MAX_LINE_LENGTH)).toList();
    if (chart.spec() != null && image.isEmpty()) {
      lines = Stream.concat(lines.stream(), Stream.of(NOT_DRAWN)).toList();
    }
    return new SectionView(
        "Farm effectiveness",
        LineText.cut(chart.verdict(), MAX_LINE_LENGTH),
        Accent.NEUTRAL,
        lines,
        null,
        image.orElse(null));
  }
}
