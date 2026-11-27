package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.SampleFacts;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the base armor, base magic resistance and total agility sampled from a real replay. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class HeroDefenseTrackReplayTest {

  private final SampleFacts samples =
      Parsers.standard()
          .parse(
              Path.of(System.getenv("REPLAY_FIXTURE_DIR"), "9044278208.dem"),
              TestModes.ALL_PICK,
              () -> false)
          .samples();

  @Test
  void everySeriesHasTheThreeArraysAsLongAsMaxHp() {
    assertThat(samples.series()).hasSize(10);
    for (SampleFacts.PlayerSeries series : samples.series()) {
      int length = series.maxHp().length;
      assertThat(series.baseArmorX10()).isNotNull().hasSize(length);
      assertThat(series.baseMagicResistX10()).isNotNull().hasSize(length);
      assertThat(series.agilityTotalX10()).isNotNull().hasSize(length);
    }
  }

  @Test
  void baseMagicResistanceIs25PercentWheneverTheHeroExists() {
    for (SampleFacts.PlayerSeries series : samples.series()) {
      for (int i = 0; i < series.maxHp().length; i++) {
        if (series.maxHp()[i] > 0) {
          assertThat(series.baseMagicResistX10()[i]).as("sample %d", i).isEqualTo(250);
        }
      }
    }
  }

  @Test
  void totalAgilityGrowsFromTheFirstHeroSampleToTheLast() {
    for (SampleFacts.PlayerSeries series : samples.series()) {
      int[] maxHp = series.maxHp();
      int first = 0;
      while (first < maxHp.length && maxHp[first] <= 0) {
        first++;
      }
      int[] agility = series.agilityTotalX10();
      assertThat(first).isLessThan(maxHp.length);
      assertThat(agility[agility.length - 1])
          .as("hero %d", series.replayPlayerId())
          .isGreaterThan(agility[first]);
    }
  }
}
