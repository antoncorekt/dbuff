package com.ako.dbuff.service.replay;

import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DraftTimelineTest {
  private final AbilityDraftProfile profile = new AbilityDraftProfile();

  /** 40 skills at ticks 10, 20, ... except that skill 1 may be moved by the caller. */
  private static AbilityDraftFacts skills(int firstTick) {
    List<DraftPick> picks = new ArrayList<>();
    List<DraftPoolEntry> pool = new ArrayList<>();
    for (int i = 0; i < 48; i++) {
      pool.add(new DraftPoolEntry(i, 100 + i, -1, 0));
    }
    for (int i = 0; i < 40; i++) {
      picks.add(new DraftPick(i + 1, i, 0, i == 0 ? firstTick : 1000 + i * 10));
    }
    return new AbilityDraftFacts(pool, picks);
  }

  /** 10 heroes at ticks 1005, 1055, ... between the skill picks, the first at {@code firstTick}. */
  private static HeroDraftFacts heroes(int firstTick) {
    List<DraftPick> picks = new ArrayList<>();
    List<HeroDraftEntry> pool = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      pool.add(new HeroDraftEntry(i, 1 + i, -1));
    }
    for (int i = 0; i < 10; i++) {
      picks.add(new DraftPick(i + 1, i, 0, i == 0 ? firstTick : 1005 + i * 50));
    }
    return new HeroDraftFacts(pool, picks);
  }

  @Test
  void turns_interleaveSkillsAndHeroesByTick() {
    DraftTimeline.Turns turns = DraftTimeline.turns(1L, skills(10), heroes(1015), profile);

    assertThat(turns.skill(1)).isEqualTo(1);
    assertThat(turns.skill(2)).isEqualTo(2);
    assertThat(turns.hero(1)).isEqualTo(3);
    assertThat(turns.skill(3)).isEqualTo(4);
    assertThat(turns.skills().size() + turns.heroes().size()).isEqualTo(50);
    assertThat(turns.known()).isTrue();
  }

  @Test
  void turns_giveASkillPickTheTurnBeforeAHeroPickOfTheSameTick() {
    DraftTimeline.Turns turns = DraftTimeline.turns(1L, skills(10), heroes(10), profile);

    assertThat(turns.skill(1)).isEqualTo(1);
    assertThat(turns.hero(1)).isEqualTo(2);
  }

  @Test
  void turns_areNoneUnlessBothSidesAreComplete() {
    HeroDraftFacts full = heroes(5);
    HeroDraftFacts short9 = new HeroDraftFacts(full.pool(), full.picks().subList(0, 9));

    assertThat(DraftTimeline.turns(1L, skills(10), null, profile).known()).isFalse();
    assertThat(DraftTimeline.turns(1L, null, full, profile).known()).isFalse();
    assertThat(DraftTimeline.turns(1L, skills(10), short9, profile).known()).isFalse();
  }

  @Test
  void clockSeconds_isNegativeBeforeTheHorn() {
    assertThat(DraftTimeline.clockSeconds(1956, 17136)).isEqualTo(-506.0);
  }
}
