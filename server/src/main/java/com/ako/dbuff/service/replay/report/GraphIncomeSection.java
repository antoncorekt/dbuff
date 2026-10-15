package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.IncomeChartBuilder.IncomeChart;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The income of the tracked heroes as a picture: gold per minute over time, where it fell and why,
 * deaths, buybacks and the items bought, with a Hand of Midas paying for itself. Off unless
 * switched on, or asked for with the section name.
 */
@Slf4j
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
  public int order() {
    return 91;
  }

  @Override
  protected Optional<SectionView> build(ReplayReportInput input) {
    try {
      return builder
          .build(
              input.matchId(),
              input.focusSlots(),
              properties.getGraphIncomeMaxHeroes(),
              input.maxFindings(),
              properties.getMaxBehaviourLineLength())
          .map(chart -> view(input.matchId(), chart));
    } catch (RuntimeException e) {
      log.warn("Income graph of match {} skipped: {}", input.matchId(), e.toString());
      return Optional.empty();
    }
  }

  private SectionView view(long matchId, IncomeChart chart) {
    return view(
        "Hero income",
        chart.verdict(),
        chart.bullets(),
        "income:" + matchId,
        "income-" + matchId + ".png",
        chart.spec());
  }
}
