package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * What every graph section shares: the picture comes from the {@link ChartService} under a key that
 * holds the chart's data fingerprint, a picture that could not be drawn leaves the text with an
 * apology, and the view of one delivery is built once because the plain path asks for the lines and
 * the image separately and both must come from the same read. A build that throws is logged with
 * its stack trace and gives the same apology view for that delivery only; it is never stored as an
 * empty section.
 *
 * <p>The views are kept per delivery: a small cache keyed by the identity of the input, which holds
 * the input weakly, so nothing outlives the delivery for long.
 */
@Slf4j
abstract class GraphSection implements ReplayReportSection {
  static final String NOT_DRAWN = "The graph could not be drawn this time.";
  private static final int DELIVERIES_KEPT = 8;

  private final ChartService charts;
  private final PlainViewRenderer plain;
  private final Cache<ReplayReportInput, Optional<SectionView>> views =
      Caffeine.newBuilder().weakKeys().maximumSize(DELIVERIES_KEPT).build();

  GraphSection(ChartService charts, PlainViewRenderer plain) {
    this.charts = charts;
    this.plain = plain;
  }

  /** The heading of the section, also on the apology view. */
  protected abstract String title();

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
    return views.get(input, this::buildOrApologise);
  }

  private Optional<SectionView> buildOrApologise(ReplayReportInput input) {
    try {
      return build(input);
    } catch (RuntimeException e) {
      log.warn("Graph section {} of match {} failed: {}", name(), input.matchId(), e.toString(), e);
      return Optional.of(
          new SectionView(title(), null, Accent.NEUTRAL, List.of(NOT_DRAWN), null, null));
    }
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
