package com.ako.dbuff.service.replay.analysis.team;

import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter.Segment;
import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter.State;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamStateSegmenterTest {

  private final TeamStateSegmenter segmenter =
      new TeamStateSegmenter(new TeamStateSegmenter.Tuning(0.05, 120, 60));

  @Test
  void segment_entersAStateOnlyAfterTheLeadHeldAndStartsItWhereTheLeadBegan() {
    double[] leads = {0, 0.02, 0.06, 0.07, 0.08, 0.08, 0.01, 0.0};

    List<Segment> segments = segmenter.segment(leads, 60);

    assertThat(segments)
        .extracting(Segment::state, Segment::startSeconds)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(State.EVEN, 0),
            org.assertj.core.groups.Tuple.tuple(State.AHEAD, 120),
            org.assertj.core.groups.Tuple.tuple(State.EVEN, 360));
    assertThat(segments.get(1).endSeconds()).isEqualTo(360);
    assertThat(segments.get(1).peakLead()).isEqualTo(0.08);
  }

  @Test
  void segment_doesNotFlapWhenTheLeadWobblesAroundTheThreshold() {
    double[] leads = {0.06, 0.07, 0.04, 0.06, 0.04, 0.07, 0.03, 0.06, 0.05, 0.04};

    List<Segment> segments = segmenter.segment(leads, 60);

    assertThat(segments).extracting(Segment::state).containsExactly(State.AHEAD);
  }

  @Test
  void segment_ignoresALeadThatDoesNotLastLongEnough() {
    double[] leads = {0, 0.09, 0, 0, 0.09, 0, 0};

    assertThat(segmenter.segment(leads, 60)).extracting(Segment::state).containsExactly(State.EVEN);
  }

  @Test
  void segment_goesFromAheadThroughEvenToBehind_withoutAStateShorterThanItsHysteresis() {
    double[] leads = {0.08, 0.08, 0.08, 0.0, 0.0, -0.08, -0.09, -0.1, -0.1};

    List<Segment> segments = segmenter.segment(leads, 60);

    assertThat(segments)
        .extracting(Segment::state)
        .containsExactly(State.AHEAD, State.EVEN, State.BEHIND);
    assertThat(segments.get(2).peakLead()).isEqualTo(-0.1);
    assertThat(segments)
        .allSatisfy(s -> assertThat(s.endSeconds() - s.startSeconds()).isGreaterThanOrEqualTo(0));
  }

  @Test
  void segment_ofNothingIsEmpty() {
    assertThat(segmenter.segment(new double[0], 60)).isEmpty();
  }
}
