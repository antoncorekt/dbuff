package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.FarmChartBuilder.FarmChart;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
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
  protected List<SectionView> build(ReplayReportInput input) {
    List<FarmChart> charts =
        builder.pages(
            input.matchId(),
            input.focusSlots(),
            properties.getGraphHeroesPerImage(),
            properties.getGraphMaxImages());
    return pagesOf(charts, (chart, i, count) -> view(input.matchId(), chart, i, count));
  }

  private SectionView view(long matchId, FarmChart chart, int index, int count) {
    String title = pageTitle(TITLE, index, count);
    String verdict = LineText.cut(chart.verdict(), MAX_LINE_LENGTH);
    List<String> lines = chart.lines().stream().map(l -> LineText.cut(l, MAX_LINE_LENGTH)).toList();
    if (chart.spec() == null) {
      return new SectionView(title, verdict, Accent.NEUTRAL, lines, null, null);
    }
    return view(
        title,
        verdict,
        lines,
        "farm:" + matchId + ":" + index,
        "farm-" + matchId + "-" + (index + 1) + ".png",
        chart.spec());
  }
}
