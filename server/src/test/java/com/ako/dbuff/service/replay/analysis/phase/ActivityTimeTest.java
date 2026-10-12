package com.ako.dbuff.service.replay.analysis.phase;

import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.phase.ActivityTime.Span;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityTimeTest {

  @Test
  void within_clipsSegmentsToTheWindowAndAddsThoseOfOneActivity() {
    List<Span> spans =
        List.of(
            new Span(-90, 100, Activity.LANE_FARM),
            new Span(100, 300, Activity.DEAD),
            new Span(300, 900, Activity.LANE_FARM));

    var seconds = ActivityTime.within(spans, 0, 720);

    assertThat(seconds)
        .containsEntry(Activity.LANE_FARM, 100 + 420)
        .containsEntry(Activity.DEAD, 200);
    assertThat(seconds.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(720);
  }
}
