package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The most unusual moments of the match in game order, collected from every {@link
 * HighlightSource}.
 */
@Slf4j
@Component
public class HighlightsSection implements ReplayReportSection {
  static final String TITLE = "What stood out";

  private final ObjectProvider<HighlightSource> sources;
  private final ReplayReportProperties properties;

  public HighlightsSection(
      ObjectProvider<HighlightSource> sources, ReplayReportProperties properties) {
    this.sources = sources;
    this.properties = properties;
  }

  @Override
  public String name() {
    return "highlights";
  }

  @Override
  public int order() {
    return 55;
  }

  @Override
  public String summary() {
    return "the most unusual moments of the match";
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<String> bullets = bullets(input);
    if (bullets.isEmpty()) {
      return List.of();
    }
    List<String> lines = new ArrayList<>();
    lines.add("**" + TITLE + "**");
    lines.addAll(bullets);
    return lines;
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    return SectionViews.titled(lines(input), new SectionViews.Verdict(null, Accent.INFO));
  }

  private List<String> bullets(ReplayReportInput input) {
    ReplayReportProperties.Highlights settings = properties.getHighlights();
    return select(candidates(input), settings).stream()
        .map(h -> line(h, settings.getMaxLineLength()))
        .toList();
  }

  private List<Highlight> candidates(ReplayReportInput input) {
    List<Highlight> all = new ArrayList<>();
    sources
        .orderedStream()
        .forEach(
            source -> {
              try {
                all.addAll(source.highlights(input));
              } catch (RuntimeException e) {
                log.warn(
                    "Highlights of {} for match {} skipped: {}",
                    source.getClass().getSimpleName(),
                    input.matchId(),
                    e.getMessage());
              }
            });
    return all;
  }

  static List<Highlight> select(List<Highlight> candidates, ReplayReportProperties.Highlights s) {
    Comparator<Highlight> best = Comparator.comparingDouble(Highlight::score).reversed();
    List<Highlight> ranked = candidates.stream().sorted(best).toList();
    if (ranked.isEmpty()) {
      return List.of();
    }
    Map<String, Integer> perSource = new LinkedHashMap<>();
    List<Highlight> kept = new ArrayList<>();
    for (Highlight h : ranked) {
      if (h.score() >= s.getMinScore()
          && perSource.merge(h.source(), 1, Integer::sum) <= s.getMaxPerSource()) {
        kept.add(h);
      }
    }
    if (kept.isEmpty()) {
      kept.add(ranked.get(0));
    }
    return kept.stream()
        .limit(s.getMaxLines())
        .sorted(
            Comparator.comparing(
                    Highlight::seconds, Comparator.nullsLast(Comparator.<Double>naturalOrder()))
                .thenComparing(best))
        .toList();
  }

  private static String line(Highlight h, int maxLength) {
    String text = h.seconds() == null ? h.text() : GameTime.clock(h.seconds()) + " " + h.text();
    return LineText.cut(text, maxLength);
  }
}
