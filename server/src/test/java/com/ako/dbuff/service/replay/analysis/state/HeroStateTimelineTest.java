package com.ako.dbuff.service.replay.analysis.state;

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

  @Test
  void abilityHelpers_followLevelsCostsAndItemsThroughTime() {
    timeline.add(change(10, StateChangeKind.ITEM_ADDED, "item_tango", 0));
    timeline.add(level(100, StateChangeKind.ABILITY_LEVEL, 1, 0));
    timeline.add(level(100, StateChangeKind.MANA_COST, 0, 100));
    timeline.add(level(400, StateChangeKind.ABILITY_LEVEL, 2, 0));
    timeline.add(level(400, StateChangeKind.MANA_COST, 0, 110));
    timeline.add(change(450, StateChangeKind.COOLDOWN, "sven_storm_bolt", 470));
    timeline.add(change(500, StateChangeKind.ITEM_REMOVED, "item_tango", 0));

    assertThat(timeline.level("sven_storm_bolt", 50)).isZero();
    assertThat(timeline.level("sven_storm_bolt", 300)).isEqualTo(1);
    assertThat(timeline.manaCost("sven_storm_bolt", 300)).isEqualTo(100);
    assertThat(timeline.manaCost("sven_storm_bolt", 450)).isEqualTo(110);
    assertThat(timeline.learnedAbilities()).containsExactly("sven_storm_bolt");
    assertThat(timeline.everOnCooldown("sven_storm_bolt")).isTrue();
    assertThat(timeline.everOnCooldown("item_tango")).isFalse();
    assertThat(timeline.itemsAt(100)).containsExactly("item_tango");
    assertThat(timeline.itemsAt(600)).isEmpty();
  }

  private static HeroStateTimeline.Change level(
      double second, StateChangeKind kind, int level, int manaCost) {
    return new HeroStateTimeline.Change(second, kind, "sven_storm_bolt", 0, level, manaCost);
  }

  private static HeroStateTimeline.Change change(
      double second, StateChangeKind kind, String name, double readyAt) {
    return new HeroStateTimeline.Change(second, kind, name, readyAt);
  }
}
