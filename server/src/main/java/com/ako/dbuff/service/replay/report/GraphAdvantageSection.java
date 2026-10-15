package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.AdvantageChartBuilder.AdvantageChart;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * A picture of the match as a race: who led in gold and experience when, which fights decided it
 * and where the big swings came from. Off unless switched on, or asked for with the section name.
 */
@Component
public class GraphAdvantageSection extends GraphSection {
  private final AdvantageChartBuilder builder;

  public GraphAdvantageSection(
      AdvantageChartBuilder builder, ChartService charts, PlainViewRenderer plain) {
    super(charts, plain);
    this.builder = builder;
  }

  @Override
  public String name() {
    return "graph-advantage";
  }

  @Override
  public int order() {
    return 90;
  }

  @Override
  protected Optional<SectionView> build(ReplayReportInput input) {
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    return builder
        .build(input.matchId(), yours, input.maxFindings())
        .map(chart -> view(input.matchId(), yours, chart));
  }

  private SectionView view(long matchId, TeamSide yours, AdvantageChart chart) {
    return view(
        "Team advantage",
        chart.verdict(),
        List.of(),
        "advantage:" + matchId + ":" + AdvantageChartBuilder.viewpoint(yours),
        "advantage-" + matchId + ".png",
        chart.spec());
  }
}
