package com.ako.dbuff.service.replay.analysis.spike;

import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the detector over the gold advantage OpenDota stored for real matches. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class SpikeOpenDotaTest {

  @Test
  void aDecidedGameHasASwingTheWinnerMadeAndNoSwingIsSmallerThanTheMinimum() throws Exception {
    SpikeDetector detector = new SpikeDetector(new ReplayAnalysisProperties().spikeTuning());
    for (String match : List.of("9040892342", "9040889566")) {
      JsonNode json =
          new ObjectMapper()
              .readTree(Path.of(System.getenv("REPLAY_FIXTURE_DIR"), match + ".json").toFile());
      JsonNode advantage = json.get("radiant_gold_adv");
      double[] difference = new double[advantage.size()];
      double[] total = new double[difference.length];
      for (int minute = 0; minute < difference.length; minute++) {
        difference[minute] = advantage.get(minute).asDouble();
        for (JsonNode player : json.get("players")) {
          total[minute] += player.get("gold_t").get(minute).asLong();
        }
      }
      boolean radiantWon = json.get("radiant_win").asBoolean();

      var spikes = detector.detect(difference, total);

      assertThat(spikes).as(match).isNotEmpty();
      assertThat(spikes)
          .as(match)
          .allSatisfy(s -> assertThat(Math.abs(s.delta())).isGreaterThanOrEqualTo(1500));
      assertThat(spikes)
          .as("%s: the winner has a swing", match)
          .anyMatch(s -> (s.delta() > 0) == radiantWon);
    }
  }
}
