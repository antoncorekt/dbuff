package com.ako.dbuff.service.replay.analysis.activity;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.ako.dbuff.service.replay.map.Zone;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivitySegmenterTest {

  private static final String HERO = "npc_dota_hero_sniper";
  private static final int BASE_X = -5920;
  private static final int BASE_Y = -5352;
  private static final int BOT_LANE_X = 4859;
  private static final int BOT_LANE_Y = -6379;

  private final MapLayouts layouts = new MapLayouts(new ObjectMapper());
  private final MapLayout layout = layouts.forVersion(layouts.frozenVersion()).orElseThrow();
  private final ActivitySegmenter segmenter = new ActivitySegmenter(ActivityTuning.defaults());

  @Test
  void segment_labelsASampleBySomethingThatExplainsIt_deadBeforeAnythingElse() {
    var track = track(5, new int[][] {{BASE_X, BASE_Y, 1, 1}, {BASE_X, BASE_Y, 1, 0}});

    List<ActivitySegment> segments = segmenter.segment(track, HERO, List.of(), layout);

    assertThat(segments)
        .extracting(ActivitySegment::activity)
        .containsExactly(Activity.IN_BASE, Activity.DEAD);
  }

  @Test
  void segment_countsAHeroWithACreepLastHitNearbyAsFarming_neutralsAsJungleAndLaneCreepsAsLane() {
    var track = track(5, new int[][] {{-2000, -4000, 5, 1}, {BOT_LANE_X, BOT_LANE_Y, 5, 1}});
    List<CombatEvent> events =
        List.of(kill(0, "npc_dota_neutral_centaur_khan"), kill(5, "npc_dota_creep_goodguys_melee"));

    List<ActivitySegment> segments = segmenter.segment(track, HERO, events, layout);

    assertThat(segments).extracting(ActivitySegment::activity).contains(Activity.JUNGLE_FARM);
    assertThat(segments.getFirst().zone()).isEqualTo(Zone.Kind.JUNGLE);
  }

  @Test
  void segment_labelsATeleportWindowFromTheModifierPair() {
    var track =
        track(
            5,
            new int[][] {{BASE_X, BASE_Y, 3, 1}, {BASE_X, BASE_Y, 3, 1}, {BASE_X, BASE_Y, 3, 1}});
    List<CombatEvent> events =
        List.of(
            modifier(CombatEventKind.MODIFIER_ADD, 4),
            modifier(CombatEventKind.MODIFIER_REMOVE, 6));

    List<ActivitySegment> segments = segmenter.segment(track, HERO, events, layout);

    assertThat(segments)
        .extracting(ActivitySegment::activity)
        .containsExactly(Activity.IN_BASE, Activity.TELEPORTING, Activity.IN_BASE);
  }

  @Test
  void segment_callsFastMovementTravelingAndTheEnemyHalfOfALanePushing() {
    var fast =
        track(5, new int[][] {{-2000, -4000, 5, 1}, {-1000, -4000, 5, 1}, {-1000, -4000, 5, 1}});
    var enemyLane = track(5, new int[][] {{524, 652, 8, 1}});

    assertThat(segmenter.segment(fast, HERO, List.of(), layout))
        .extracting(ActivitySegment::activity)
        .containsExactly(Activity.POSITIONED, Activity.TRAVELING, Activity.POSITIONED);
    assertThat(segmenter.segment(enemyLane, HERO, List.of(), layout).getFirst().activity())
        .isEqualTo(Activity.PUSHING);
  }

  @Test
  void segment_mergesNeighbouringSamplesOfTheSameKindAndSkipsSamplesWithNoData() {
    var track =
        track(
            5,
            new int[][] {
              {BASE_X, BASE_Y, 0, 1},
              {BASE_X, BASE_Y, 1, 1},
              {BASE_X, BASE_Y, 1, 1},
              {BASE_X, BASE_Y, 1, 1}
            });

    List<ActivitySegment> segments = segmenter.segment(track, HERO, List.of(), layout);

    assertThat(segments).hasSize(1);
    assertThat(segments.getFirst().startSeconds()).isEqualTo(-85);
    assertThat(segments.getFirst().endSeconds()).isEqualTo(-70);
  }

  /** Samples are {x, y, level, flags}, starting at -90 s. */
  private static ActivitySegmenter.Track track(int step, int[][] samples) {
    int n = samples.length;
    int[] x = new int[n];
    int[] y = new int[n];
    int[] level = new int[n];
    int[] flags = new int[n];
    for (int i = 0; i < n; i++) {
      x[i] = samples[i][0];
      y[i] = samples[i][1];
      level[i] = samples[i][2];
      flags[i] = samples[i][3];
    }
    return new ActivitySegmenter.Track(Zone.Half.RADIANT, -90, step, x, y, level, flags);
  }

  private static CombatEvent kill(double second, String target) {
    return new CombatEvent(
        CombatEventKind.DEATH,
        -90 + second,
        HERO,
        target,
        "",
        CombatEvent.ATTACKER_HERO,
        0,
        0,
        0,
        0,
        0,
        0,
        0);
  }

  private static CombatEvent modifier(CombatEventKind kind, double second) {
    return new CombatEvent(
        kind,
        -90 + second,
        "",
        HERO,
        "modifier_teleporting",
        CombatEvent.TARGET_HERO,
        0,
        0,
        0,
        0,
        0,
        0,
        0);
  }
}
