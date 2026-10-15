package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.FarmChartBuilder.FarmChart;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * How the tracked heroes farmed: lane against camp gold per minute, and whether their farming items
 * paid back. A match whose creep kills were not stored gets the item verdicts as text. Off unless
 * switched on, or asked for with the section name.
 */
@Component
public class GraphFarmSection extends GraphSection {
  private static final String TITLE = "Farm effectiveness";
  private static final int MAX_LINE_LENGTH = 300;

  private final FarmChartBuilder builder;
  private final ReplayReportProperties properties;

  public GraphFarmSection(
      FarmChartBuilder builder,
      ChartService charts,
      PlainViewRenderer plain,
      ReplayReportProperties properties) {
    super(charts, plain);
    this.builder = builder;
    this.properties = properties;
  }

  @Override
  public String name() {
    return "graph-farm";
  }

  @Override
  protected String title() {
    return TITLE;
  }

  @Override
  public int order() {
    return 92;
  }

  @Override
  protected Optional<SectionView> build(ReplayReportInput input) {
    return builder
        .build(input.matchId(), input.focusSlots(), properties.getGraphFarmMaxHeroes())
        .map(chart -> view(input.matchId(), chart));
  }

  private SectionView view(long matchId, FarmChart chart) {
    String verdict = LineText.cut(chart.verdict(), MAX_LINE_LENGTH);
    List<String> lines = chart.lines().stream().map(l -> LineText.cut(l, MAX_LINE_LENGTH)).toList();
    if (chart.spec() == null) {
      return new SectionView(TITLE, verdict, Accent.NEUTRAL, lines, null, null);
    }
    return view(TITLE, verdict, lines, "farm:" + matchId, "farm-" + matchId + ".png", chart.spec());
  }
}
