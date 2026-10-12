package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.SampleFacts;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.ako.dbuff.service.replay.map.Zone;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the map model against real replays and against OpenDota's own lane for each player. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class MapReplayTest {

  /** OpenDota {@code lane} per player index 0-9: 1 bottom, 2 middle, 3 top, for both teams. */
  private static final Map<String, int[]> OPEN_DOTA_LANES =
      Map.of(
          "9038725154.dem", new int[] {3, 2, 3, 1, 1, 3, 1, 1, 3, 2},
          "9037038717.dem", new int[] {1, 1, 2, 3, 3, 3, 1, 2, 3, 1});

  private static final int LANE_FROM_SECONDS = 30;
  private static final int LANE_TO_SECONDS = 300;

  private final MapLayouts layouts = new MapLayouts(new ObjectMapper());

  @Test
  void bothReplaysWerePlayedOnTheFrozenMap() {
    for (String replay : OPEN_DOTA_LANES.keySet()) {
      assertThat(parse(replay).map().version()).as(replay).isEqualTo(layouts.frozenVersion());
      assertThat(parse(replay).map().camps()).hasSize(28);
    }
  }

  @Test
  void theLaneEachPlayerSpendsTheFirstFiveMinutesInAgreesWithOpenDota() {
    for (var entry : OPEN_DOTA_LANES.entrySet()) {
      ReplayFacts facts = parse(entry.getKey());
      MapLayout layout = layouts.forVersion(facts.map().version()).orElseThrow();
      int agree = 0;
      for (SampleFacts.PlayerSeries series : facts.samples().series()) {
        Zone.Lane lane = dominantLane(layout, facts.samples(), series);
        int expected = entry.getValue()[series.replayPlayerId() / 2];
        if (lane == laneOf(expected)) {
          agree++;
        }
      }
      assertThat(agree)
          .as("players whose lane matches OpenDota in " + entry.getKey())
          .isGreaterThanOrEqualTo(9);
    }
  }

  private static Zone.Lane dominantLane(
      MapLayout layout, SampleFacts samples, SampleFacts.PlayerSeries series) {
    Map<Zone.Lane, Integer> seconds = new EnumMap<>(Zone.Lane.class);
    for (int i = 0; i < series.x().length; i++) {
      int time = samples.startSeconds() + i * samples.stepSeconds();
      if (time < LANE_FROM_SECONDS || time > LANE_TO_SECONDS || series.level()[i] == 0) {
        continue;
      }
      Zone zone = layout.zone(series.x()[i], series.y()[i]);
      if (zone.kind() == Zone.Kind.LANE) {
        seconds.merge(zone.lane(), samples.stepSeconds(), Integer::sum);
      }
    }
    return seconds.entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(null);
  }

  private static Zone.Lane laneOf(int openDotaLane) {
    return switch (openDotaLane) {
      case 1 -> Zone.Lane.BOTTOM;
      case 2 -> Zone.Lane.MID;
      default -> Zone.Lane.TOP;
    };
  }

  private static ReplayFacts parse(String replay) {
    return Parsers.standard()
        .parse(
            Path.of(System.getenv("REPLAY_FIXTURE_DIR")).resolve(replay),
            TestModes.ABILITY_DRAFT,
            () -> false);
  }
}
