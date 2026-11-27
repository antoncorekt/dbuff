package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.DefenseChartBuilder.DefenseChart;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Both teams' health, estimated armor and effective health over the game as a picture, with the
 * defensive items they got and the ones a team lacked. Off unless switched on, or asked for with
 * the section name.
 */
@Component
public class GraphDefenseSection extends GraphSection {
  static final String FOOTNOTE =
      "End-of-game values mostly follow from winning (more gold, more items); compare 10:00 and"
          + " 20:00 rather than the end. Armor and resistance are estimated from base, agility and"
          + " items; auras, talents and buffs are not counted.";

  private final DefenseChartBuilder builder;

  public GraphDefenseSection(
      DefenseChartBuilder builder, ChartService charts, PlainViewRenderer plain) {
    super(charts, plain);
    this.builder = builder;
  }

  @Override
  public String name() {
    return "graph-defense";
  }

  @Override
  protected String title() {
    return "Team defense";
  }

  @Override
  public int order() {
    return 65;
  }

  @Override
  public String summary() {
    return "Team HP, estimated armor and effective HP over the game";
  }

  @Override
  protected List<SectionView> build(ReplayReportInput input) {
    return builder
        .build(input.matchId())
        .map(chart -> List.of(view(input.matchId(), chart)))
        .orElse(List.of());
  }

  private SectionView view(long matchId, DefenseChart chart) {
    SectionView view =
        view(
            title(),
            chart.verdict(),
            chart.bullets(),
            "defense:" + matchId,
            "defense-" + matchId + ".png",
            chart.spec());
    return new SectionView(
        view.title(), view.verdict(), view.accent(), view.bullets(), FOOTNOTE, view.image());
  }
}
