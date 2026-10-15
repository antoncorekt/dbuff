package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What every graph section shares: the picture comes from the {@link ChartService} under a key that
 * holds the chart's data fingerprint, a picture that could not be drawn leaves the text with an
 * apology, and the view of one delivery is built once because the plain path asks for the lines and
 * the image separately and both must come from the same read.
 */
abstract class GraphSection implements ReplayReportSection {
  static final String NOT_DRAWN = "The graph could not be drawn this time.";

  private final ChartService charts;
  private final PlainViewRenderer plain;
  private volatile Built built;

  private record Built(ReplayReportInput input, Optional<SectionView> view) {}

  GraphSection(ChartService charts, PlainViewRenderer plain) {
    this.charts = charts;
    this.plain = plain;
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

  /** The section for one delivery; empty when the stored data has nothing to draw. */
  protected abstract Optional<SectionView> build(ReplayReportInput input);

  /** The section's view with the picture drawn from the spec, or with the apology when it fails. */
  protected SectionView view(
      String title,
      String verdict,
      List<String> bullets,
      String keyPrefix,
      String fileName,
      ChartSpec spec) {
    Optional<ImageAttachment> image =
        charts
            .png(keyPrefix + ":" + spec.fingerprint(), () -> spec)
            .map(png -> new ImageAttachment(fileName, png));
    List<String> text = new ArrayList<>(bullets);
    if (image.isEmpty()) {
      text.add(NOT_DRAWN);
    }
    return new SectionView(title, verdict, Accent.NEUTRAL, text, null, image.orElse(null));
  }
}
