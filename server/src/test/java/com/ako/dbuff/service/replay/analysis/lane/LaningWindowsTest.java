package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.dead;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.inBase;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.inLane;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.inRiver;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.jungleFarm;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.passing;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.teleporting;
import static org.assertj.core.api.Assertions.assertThat;

class LaningWindowsTest {
  private static final Zone.Lane LANE = Zone.Lane.TOP;
  private static final int GAME_END = 800;

  private final LaningWindows windows = new LaningWindows(new LaneProperties());

  private Optional<LaningWindow> find(Integer tower, ActivitySegment... segments) {
    return windows.find(List.of(segments), LANE, tower, GAME_END);
  }

  @Test
  void find_startsWhenTheHeroFirstStandsInHisLaneNotBeforeTheHorn() {
    LaningWindow w =
        find(null, inBase(-90, 20), inLane(20, 700, LANE), inLane(700, GAME_END, Zone.Lane.MID))
            .orElseThrow();

    assertThat(w.startSeconds()).isEqualTo(20);
    assertThat(find(null, inLane(-90, 100, LANE)).orElseThrow().startSeconds()).isZero();
  }

  @Test
  void find_isEmptyForAHeroWhoNeverReachesHisLane() {
    assertThat(find(null, inBase(0, 100), jungleFarm(100, 400))).isEmpty();
  }

  @Test
  void find_endsWhereTheHeroLeftWhenHeFarmedTheJungleForThirtySeconds() {
    LaningWindow w =
        find(null, inLane(0, 400, LANE), jungleFarm(400, 440), inLane(440, 600, LANE))
            .orElseThrow();

    assertThat(w.endSeconds()).isEqualTo(400);
    assertThat(w.end()).isEqualTo(LaneEnd.JUNGLE);
  }

  @Test
  void find_keepsLaningWhenTheJungleTripIsShorterThanThirtySecondsAndHeComesBack() {
    LaningWindow w =
        find(null, inLane(0, 400, LANE), jungleFarm(400, 425), inLane(425, GAME_END, LANE))
            .orElseThrow();

    assertThat(w.endSeconds()).isEqualTo(GAME_END);
    assertThat(w.end()).isEqualTo(LaneEnd.GAME_END);
  }

  @Test
  void find_endsWhenTheHeroFarmsAnotherLane() {
    LaningWindow w =
        find(null, inLane(0, 300, LANE), inLane(300, 360, Zone.Lane.MID)).orElseThrow();

    assertThat(w.endSeconds()).isEqualTo(300);
    assertThat(w.end()).isEqualTo(LaneEnd.OTHER_LANE);
  }

  @Test
  void find_doesNotCountPassingThroughAnotherLaneAsLeaving() {
    LaningWindow w =
        find(null, inLane(0, 300, LANE), passing(300, 340, Zone.Lane.MID), inLane(340, 500, LANE))
            .orElseThrow();

    assertThat(w.end()).isEqualTo(LaneEnd.GAME_END);
  }

  @Test
  void find_doesNotEndOnATeleportHomeAndBackWithinSixtySeconds() {
    LaningWindow w =
        find(
                null,
                inLane(0, 400, LANE),
                teleporting(400, 410),
                inBase(410, 440),
                inLane(440, 700, LANE))
            .orElseThrow();

    assertThat(w.endSeconds()).isEqualTo(GAME_END);
  }

  @Test
  void find_endsAtTheDepartureWhenATeleportDoesNotComeBackWithinSixtySeconds() {
    LaningWindow w =
        find(
                null,
                inLane(0, 400, LANE),
                teleporting(400, 410),
                inBase(410, 470),
                inLane(470, 900, LANE))
            .orElseThrow();

    assertThat(w.endSeconds()).isEqualTo(400);
    assertThat(w.end()).isEqualTo(LaneEnd.TELEPORTED);
  }

  @Test
  void find_endsWhenTheHeroStaysAwayInTheRiverForSixtySeconds() {
    LaningWindow w = find(null, inLane(0, 300, LANE), inRiver(300, 380)).orElseThrow();

    assertThat(w.endSeconds()).isEqualTo(300);
    assertThat(w.end()).isEqualTo(LaneEnd.LEFT);
  }

  @Test
  void find_doesNotCountTimeSpentDeadAsTimeAway() {
    LaningWindow w =
        find(null, inLane(0, 300, LANE), dead(300, 400), inBase(400, 420), inLane(420, 800, LANE))
            .orElseThrow();

    assertThat(w.endSeconds()).isEqualTo(GAME_END);
  }

  @Test
  void find_endsWhenALaneTowerFallsBeforeTheHeroLeaves() {
    LaningWindow w = find(480, inLane(0, 900, LANE)).orElseThrow();

    assertThat(w.endSeconds()).isEqualTo(480);
    assertThat(w.end()).isEqualTo(LaneEnd.TOWER_FELL);
  }

  @Test
  void find_endsWhereTheHeroLeftWhenHeLeftBeforeTheTowerFell() {
    LaningWindow w = find(600, inLane(0, 300, LANE), jungleFarm(300, 500)).orElseThrow();

    assertThat(w.endSeconds()).isEqualTo(300);
    assertThat(w.end()).isEqualTo(LaneEnd.JUNGLE);
  }

  @Test
  void find_stopsAtTheCapWhateverTheHeroDoes() {
    LaningWindow w = windows.find(List.of(inLane(0, 2000, LANE)), LANE, null, 2000).orElseThrow();

    assertThat(w.endSeconds()).isEqualTo(900);
    assertThat(w.end()).isEqualTo(LaneEnd.CAP);
  }

  @Test
  void find_endsWithTheDataWhenNothingEndsTheLaning() {
    LaningWindow w = find(null, inLane(0, 500, LANE)).orElseThrow();

    assertThat(w.endSeconds()).isEqualTo(GAME_END);
    assertThat(w.contains(10)).isTrue();
    assertThat(w.contains(GAME_END)).isFalse();
  }
}
