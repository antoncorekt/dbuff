package com.ako.dbuff.service.replay.analysis.death;

import com.ako.dbuff.service.replay.StateChangeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeroStateTimelineTest {

  private final HeroStateTimeline timeline = new HeroStateTimeline();

  @Test
  void owns_followsItemsGainedAndLostByNameThroughTime() {
    timeline.add(change(10, StateChangeKind.ITEM_ADDED, "item_black_king_bar", 0));
    timeline.add(change(50, StateChangeKind.ITEM_REMOVED, "item_black_king_bar", 0));

    assertThat(timeline.owns("item_black_king_bar", 5)).isFalse();
    assertThat(timeline.owns("item_black_king_bar", 20)).isTrue();
    assertThat(timeline.owns("item_black_king_bar", 60)).isFalse();
    assertThat(timeline.owns("item_blink", 20)).isFalse();
  }

  @Test
  void ready_isFalseUntilTheLastCooldownSeenBeforeThenHasEnded() {
    timeline.add(change(100, StateChangeKind.COOLDOWN, "item_black_king_bar", 160));
    timeline.add(change(300, StateChangeKind.COOLDOWN, "item_black_king_bar", 360));

    assertThat(timeline.ready("item_black_king_bar", 50)).isTrue();
    assertThat(timeline.ready("item_black_king_bar", 120)).isFalse();
    assertThat(timeline.ready("item_black_king_bar", 200)).isTrue();
    assertThat(timeline.ready("item_black_king_bar", 310)).isFalse();
    assertThat(timeline.ready("item_black_king_bar", 400)).isTrue();
  }

  private static HeroStateTimeline.Change change(
      double second, StateChangeKind kind, String name, double readyAt) {
    return new HeroStateTimeline.Change(second, kind, name, readyAt);
  }
}
