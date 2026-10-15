package com.ako.dbuff.service.replay.analysis.mid;

import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MidMoveFinderTest {
  private static final int LEVEL_SIX = 400;

  private final MidMoveFinder finder = new MidMoveFinder(new MidActivityProperties());

  private static ActivitySegment lane(int from, int to, Zone.Lane lane) {
    return new ActivitySegment(
        from, to, Activity.LANE_FARM, Zone.Kind.LANE, lane, ActivitySegment.Side.OWN);
  }

  private static ActivitySegment jungle(int from, int to) {
    return new ActivitySegment(from, to, Activity.JUNGLE_FARM, Zone.Kind.JUNGLE, null, null);
  }

  private MidMove find(
      List<TeamRotationDomain> rotations, List<ActivitySegment> segments, List<Double> fights) {
    return finder.find(LEVEL_SIX, rotations, segments, fights);
  }

  @Test
  void nothingAwayWithinTheWindowMeansHeStayedMid() {
    MidMove move = find(List.of(), List.of(lane(0, 900, Zone.Lane.MID)), List.of(800.0));

    assertThat(move).isEqualTo(MidMove.STAYED);
  }

  @Test
  void aRotationRowIsARotationToItsLane() {
    TeamRotationDomain trip =
        TeamRotationDomain.builder().startSeconds(450).toLane("BOTTOM").build();

    MidMove move = find(List.of(trip), List.of(), List.of());

    assertThat(move).isEqualTo(new MidMove(MidNext.ROTATED, 450, "BOTTOM"));
  }

  @Test
  void aStretchInAnotherLaneCountsFromLevelSixWhenHeWasAlreadyThere() {
    MidMove move = find(List.of(), List.of(lane(300, 500, Zone.Lane.TOP)), List.of());

    assertThat(move).isEqualTo(new MidMove(MidNext.ROTATED, LEVEL_SIX, "TOP"));
  }

  @Test
  void aShortWalkThroughAnotherLaneIsNotLeavingMid() {
    MidMove move = find(List.of(), List.of(lane(450, 460, Zone.Lane.TOP)), List.of());

    assertThat(move).isEqualTo(MidMove.STAYED);
  }

  @Test
  void theEarliestMoveWinsAndAFightIsAMove() {
    MidMove move = find(List.of(), List.of(jungle(500, 600)), List.of(450.0));

    assertThat(move).isEqualTo(new MidMove(MidNext.FIGHT, 450, null));
  }

  @Test
  void theJungleIsTheJungle() {
    MidMove move = find(List.of(), List.of(jungle(420, 600)), List.of());

    assertThat(move).isEqualTo(new MidMove(MidNext.JUNGLE, 420, "JUNGLE"));
  }

  @Test
  void aMoveAfterTheWindowDoesNotCount() {
    MidMove move = find(List.of(), List.of(jungle(800, 900)), List.of(900.0));

    assertThat(move).isEqualTo(MidMove.STAYED);
  }

  @Test
  void aRotationToTheMidLaneOrToNoLaneIsNotAMove() {
    TeamRotationDomain toMid = TeamRotationDomain.builder().startSeconds(450).toLane("MID").build();
    TeamRotationDomain toNowhere = TeamRotationDomain.builder().startSeconds(450).build();

    assertThat(find(List.of(toMid, toNowhere), List.of(), List.of())).isEqualTo(MidMove.STAYED);
  }

  @Test
  void aRotationShorterThanTheMinimumAwayIsNotAMove() {
    TeamRotationDomain brief =
        TeamRotationDomain.builder().startSeconds(450).endSeconds(460).toLane("TOP").build();

    assertThat(find(List.of(brief), List.of(), List.of())).isEqualTo(MidMove.STAYED);
  }
}
