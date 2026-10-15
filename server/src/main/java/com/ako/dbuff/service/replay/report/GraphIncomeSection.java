package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.IncomeChartBuilder.IncomeChart;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * The income of the tracked heroes as a picture: gold per minute over time, where it fell and why,
 * deaths, buybacks and the items bought, with a Hand of Midas paying for itself. Off unless
 * switched on, or asked for with the section name.
 */
@Component
public class GraphIncomeSection extends GraphSection {
  private final IncomeChartBuilder builder;
  private final ReplayReportProperties properties;

  public GraphIncomeSection(
      IncomeChartBuilder builder,
      ChartService charts,
      PlainViewRenderer plain,
      ReplayReportProperties properties) {
    super(charts, plain);
    this.builder = builder;
    this.properties = properties;
  }

  @Override
  public String name() {
    return "graph-income";
  }

  @Override
  protected String title() {
    return "Hero income";
  }

  @Override
  public int order() {
    return 91;
  }

  @Override
  protected List<SectionView> build(ReplayReportInput input) {
    List<IncomeChart> charts =
        builder.pages(
            input.matchId(),
            input.focusSlots(),
            properties.getGraphHeroesPerImage(),
            properties.getGraphMaxImages(),
            input.maxFindings(),
            properties.getMaxBehaviourLineLength());
    return IntStream.range(0, charts.size())
        .mapToObj(i -> view(input.matchId(), charts.get(i), i, charts.size()))
        .toList();
  }

  private SectionView view(long matchId, IncomeChart chart, int index, int count) {
    return view(
        pageTitle(title(), index, count),
        chart.verdict(),
        chart.bullets(),
        "income:" + matchId + ":" + index,
        "income-" + matchId + "-" + (index + 1) + ".png",
        chart.spec());
  }
}
