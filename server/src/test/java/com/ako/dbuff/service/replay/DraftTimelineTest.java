package com.ako.dbuff.service.replay;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DraftTimelineTest {

  private static AbilityDraftFacts skills(int... ticks) {
    DraftPick[] picks = new DraftPick[ticks.length];
    for (int i = 0; i < ticks.length; i++) {
      picks[i] = new DraftPick(i + 1, i, 0, ticks[i]);
    }
    return new AbilityDraftFacts(List.of(), List.of(picks));
  }

  private static HeroDraftFacts heroes(int... ticks) {
    DraftPick[] picks = new DraftPick[ticks.length];
    for (int i = 0; i < ticks.length; i++) {
      picks[i] = new DraftPick(i + 1, i, 0, ticks[i]);
    }
    return new HeroDraftFacts(List.of(), List.of(picks));
  }

  @Test
  void turns_interleaveSkillsAndHeroesByTick() {
    DraftTimeline.Turns turns = DraftTimeline.turns(skills(10, 30, 40), heroes(20, 50));

    assertThat(turns.skill(1)).isEqualTo(1);
    assertThat(turns.hero(1)).isEqualTo(2);
    assertThat(turns.skill(2)).isEqualTo(3);
    assertThat(turns.skill(3)).isEqualTo(4);
    assertThat(turns.hero(2)).isEqualTo(5);
  }

  @Test
  void turns_giveASkillPickTheTurnBeforeAHeroPickOfTheSameTick() {
    DraftTimeline.Turns turns = DraftTimeline.turns(skills(10), heroes(10));

    assertThat(turns.skill(1)).isEqualTo(1);
    assertThat(turns.hero(1)).isEqualTo(2);
  }

  @Test
  void turns_areEmptyWhenEitherSideIsMissing() {
    assertThat(DraftTimeline.turns(null, heroes(1)).skill(1)).isNull();
    assertThat(DraftTimeline.turns(skills(1), null).skill(1)).isNull();
  }

  @Test
  void clockSeconds_isNegativeBeforeTheHorn() {
    assertThat(DraftTimeline.clockSeconds(1956, 17136)).isEqualTo(-506.0);
  }
}
