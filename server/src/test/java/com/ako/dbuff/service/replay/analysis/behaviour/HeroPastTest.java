package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.AwayTrip.End;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.MID;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.TOP;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.stayingAt;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.trace;
import static org.assertj.core.api.Assertions.assertThat;

class HeroPastTest {
  private static AwayTrip trip(int start, int end) {
    return new AwayTrip(4L, start, start, end, MID, TOP, MID, false, End.REJOINED);
  }

  @Test
  void undisturbed_isFalseWhenAnotherTripOverlaps() {
    AwayTrip now = trip(150, 190);
    HeroPast past = new HeroPast(stayingAt(0), List.of(trip(60, 100), now));

    assertThat(past.undisturbed(now, 90, 150)).isFalse();
    assertThat(past.undisturbed(now, 100, 150)).isTrue();
  }

  @Test
  void undisturbed_isFalseWhenHeWasDeadInTheStretch() {
    AwayTrip now = trip(150, 190);
    HeroPast past = new HeroPast(trace(i -> 0, i -> i == 12 ? 1 : 0), List.of(now));

    assertThat(past.undisturbed(now, 40, 100)).isFalse();
    assertThat(past.undisturbed(now, 65, 100)).isTrue();
  }
}
