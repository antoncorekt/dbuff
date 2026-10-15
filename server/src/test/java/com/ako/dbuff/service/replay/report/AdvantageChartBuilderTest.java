package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.importance.AdvantageSeries;
import com.ako.dbuff.service.replay.analysis.importance.AdvantageSeriesReader;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.AdvantageChartBuilder.AdvantageChart;
import com.ako.dbuff.service.replay.report.chart.ChartPalette;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdvantageChartBuilderTest {
  private final AdvantageSeriesReader reader = mock(AdvantageSeriesReader.class);
  private final GameSpikeRepo spikes = mock(GameSpikeRepo.class);
  private final FightExplanationReader fights = mock(FightExplanationReader.class);
  private final AdvantageChartBuilder builder = new AdvantageChartBuilder(reader, spikes, fights);

  private void series(double[] gold, double[] xp) {
    when(reader.read(1L))
        .thenReturn(
            Optional.of(
                new AdvantageSeries(gold, xp, new double[] {1, 1, 1}, new double[] {1, 1, 1})));
  }

  private static GameSpikeDomain spike(String team, long delta, int start, int end, String cause) {
    return GameSpikeDomain.builder()
        .benefitingTeam(team)
        .deltaGold(delta)
        .startSeconds(start)
        .endSeconds(end)
        .primaryCause(cause)
        .build();
  }

  @Test
  void isEmptyWithoutStoredTimelines() {
    when(reader.read(1L)).thenReturn(Optional.empty());

    assertThat(builder.build(1L, null, 3)).isEmpty();
  }

  @Test
  void drawsRadiantUpByDefaultAndFlipsTheSeriesForADireChannel() {
    series(new double[] {0, 1000, -2000}, new double[] {0, 500, -500});
    when(spikes.findByMatchId(1L)).thenReturn(List.of());
    when(fights.importantFights(1L)).thenReturn(List.of());

    ChartSpec neutral = builder.build(1L, null, 3).orElseThrow().spec();
    ChartSpec dire = builder.build(1L, TeamSide.DIRE, 3).orElseThrow().spec();

    assertThat(neutral.panels())
        .extracting(ChartSpec.Panel::label)
        .containsExactly("Gold lead", "Experience lead");
    assertThat(neutral.panels().get(0).series().get(0).values()).containsExactly(0, 1000, -2000);
    assertThat(neutral.panels().get(0).series().get(0).positiveCaption())
        .isEqualTo("Radiant ahead");
    assertThat(neutral.endSeconds()).isEqualTo(120);
    assertThat(dire.panels().get(0).series().get(0).values()).containsExactly(-0.0, -1000, 2000);
    assertThat(dire.panels().get(0).series().get(0).positiveCaption())
        .isEqualTo("Dire (your team) ahead");
    assertThat(dire.panels().get(0).series().get(0).positive()).isEqualTo(ChartPalette.DIRE);
    assertThat(dire.legend()).isEmpty();
  }

  @Test
  void shadesImportantFightsAndLabelsTheBiggestSwingsInPlainWords() {
    series(new double[] {0, 1000, 3000}, new double[] {0, 500, 900});
    when(spikes.findByMatchId(1L))
        .thenReturn(
            List.of(
                spike("RADIANT", 3100, 60, 120, "FIGHT_WIN"),
                spike("DIRE", 900, 0, 60, "OBJECTIVE"),
                spike("DIRE", 5000, 30, 90, "OTHER")));
    when(fights.importantFights(1L))
        .thenReturn(
            List.of(
                new LostFight(
                    FightDomain.builder().startSeconds(40.0).endSeconds(70.0).build(),
                    null,
                    List.of(),
                    0)));

    AdvantageChart chart = builder.build(1L, TeamSide.RADIANT, 1).orElseThrow();

    assertThat(chart.spec().bands()).hasSize(1);
    assertThat(chart.spec().bands().get(0).startSeconds()).isEqualTo(40);
    assertThat(chart.spec().markers()).hasSize(1);
    assertThat(chart.spec().markers().get(0).label())
        .isEqualTo("Radiant (your team) gained 3.1k from a won fight");
    assertThat(chart.spec().markers().get(0).seconds()).isEqualTo(90);
    assertThat(chart.spec().legend()).hasSize(2);
  }

  @Test
  void saysInOneLineWhoLedAndByHowMuch() {
    series(new double[] {0, 1500, -800}, new double[] {0, 0, 0});
    when(spikes.findByMatchId(1L)).thenReturn(List.of());
    when(fights.importantFights(1L)).thenReturn(List.of());

    assertThat(builder.build(1L, TeamSide.RADIANT, 3).orElseThrow().verdict())
        .isEqualTo(
            "Radiant (your team) was ahead by up to 1.5k gold (at 1:00); Dire by up to 800 (at"
                + " 2:00).");
    assertThat(builder.build(1L, TeamSide.DIRE, 3).orElseThrow().verdict())
        .isEqualTo(
            "Dire (your team) was ahead by up to 800 gold (at 2:00); Radiant by up to 1.5k (at"
                + " 1:00).");
  }

  @Test
  void saysSoWhenOneSideNeverLed() {
    series(new double[] {0, 100, 200}, new double[] {0, 0, 0});
    when(spikes.findByMatchId(1L)).thenReturn(List.of());
    when(fights.importantFights(1L)).thenReturn(List.of());

    assertThat(builder.build(1L, null, 3).orElseThrow().verdict()).endsWith("Dire never led.");
  }
}
