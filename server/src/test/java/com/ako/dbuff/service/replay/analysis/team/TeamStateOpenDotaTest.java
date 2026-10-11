package com.ako.dbuff.service.replay.analysis.team;

import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter.Segment;
import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter.State;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the segmenter over the gold OpenDota stored for real matches and judges the outcome. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class TeamStateOpenDotaTest {

  private final TeamStateSegmenter segmenter =
      new TeamStateSegmenter(new ReplayAnalysisProperties().teamStateTuning());

  @Test
  void theWinnerEndsAheadOrEvenAndNoStateIsShorterThanItTakesToLeaveIt() throws Exception {
    for (String match : List.of("9040892342", "9040889566")) {
      JsonNode json =
          new ObjectMapper()
              .readTree(Path.of(System.getenv("REPLAY_FIXTURE_DIR"), match + ".json").toFile());
      JsonNode advantage = json.get("radiant_gold_adv");
      double[] lead = new double[advantage.size()];
      for (int minute = 0; minute < lead.length; minute++) {
        long total = 0;
        for (JsonNode player : json.get("players")) {
          total += player.get("gold_t").get(minute).asLong();
        }
        lead[minute] = advantage.get(minute).asDouble() / total;
      }
      boolean radiantWon = json.get("radiant_win").asBoolean();
      double[] winnerLead = new double[lead.length];
      for (int i = 0; i < lead.length; i++) {
        winnerLead[i] = radiantWon ? lead[i] : -lead[i];
      }

      List<Segment> segments = segmenter.segment(winnerLead, 60);

      assertThat(segments.getLast().state()).as(match).isNotEqualTo(State.BEHIND);
      for (int i = 0; i < segments.size() - 1; i++) {
        Segment s = segments.get(i);
        assertThat(s.endSeconds() - s.startSeconds())
            .as("%s segment %d %s", match, i, s.state())
            .isGreaterThanOrEqualTo(i == 0 ? 0 : 60);
      }
    }
  }
}
