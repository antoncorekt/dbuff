package com.ako.dbuff.service.replay.analysis.behaviour;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.Posture.GROUPED;
import static com.ako.dbuff.service.replay.analysis.behaviour.Posture.MIXED;
import static com.ako.dbuff.service.replay.analysis.behaviour.Posture.SPLIT;
import static org.assertj.core.api.Assertions.assertThat;

class SplitStretchesTest {
  private final SplitStretches stretches = new SplitStretches(2);

  private static List<MinutePosture> minutes(int first, Posture... postures) {
    List<MinutePosture> list = new ArrayList<>();
    for (int i = 0; i < postures.length; i++) {
      list.add(new MinutePosture(first + i, postures[i]));
    }
    return list;
  }

  @Test
  void of_joinsNeighbouringMinutesWithTheSameLabel() {
    assertThat(stretches.of(minutes(12, SPLIT, SPLIT, SPLIT, SPLIT)))
        .containsExactly(new Stretch(720, 960, SPLIT));
  }

  @Test
  void of_aOneMinuteDipInsideALongStretchVanishes() {
    assertThat(stretches.of(minutes(12, GROUPED, GROUPED, SPLIT, GROUPED, GROUPED)))
        .containsExactly(new Stretch(720, 1020, GROUPED));
  }

  @Test
  void of_twoLongStretchesStaySeparate() {
    assertThat(stretches.of(minutes(12, GROUPED, GROUPED, SPLIT, SPLIT, SPLIT)))
        .containsExactly(new Stretch(720, 840, GROUPED), new Stretch(840, 1020, SPLIT));
  }

  @Test
  void of_aShortFirstStretchJoinsTheOneAfterIt() {
    assertThat(stretches.of(minutes(12, GROUPED, MIXED, SPLIT, SPLIT, SPLIT)))
        .containsExactly(new Stretch(720, 840, MIXED), new Stretch(840, 1020, SPLIT));
  }

  @Test
  void of_aLabelThatFlapsEveryMinuteBecomesOneStretch() {
    assertThat(stretches.of(minutes(12, GROUPED, SPLIT, GROUPED, SPLIT, GROUPED, SPLIT)))
        .hasSize(1)
        .allSatisfy(s -> assertThat(s.endSeconds() - s.startSeconds()).isEqualTo(360));
  }

  @Test
  void of_aMissingMinuteBreaksTheRunAndATooShortRunIsDropped() {
    List<MinutePosture> all = new ArrayList<>(minutes(12, GROUPED, GROUPED));
    all.addAll(minutes(15, SPLIT, SPLIT));
    all.addAll(minutes(20, MIXED));

    assertThat(stretches.of(all))
        .containsExactly(new Stretch(720, 840, GROUPED), new Stretch(900, 1020, SPLIT));
  }

  @Test
  void of_noMinutesNoStretches() {
    assertThat(stretches.of(List.of())).isEmpty();
  }
}
