package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.hit;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.sampleOf;
import static org.assertj.core.api.Assertions.assertThat;

class SaveJudgeTest {
  private static final ItemEfficiencyEntry ENTRY =
      ItemTestData.entry("glimmer", List.of(SaveAspect.NAME), List.of("glimmer_cape"), Map.of());

  private static SaveJudge judge(List<CombatEvent> events, ReplayPlayerTrackDomain track) {
    HeroInputs hero = ItemTestData.hero(List.of(), events, track, List.of(), List.of());
    return new SaveJudge(
        hero, AspectContext.of(ENTRY, ItemTestData.kits(Map.of()), 600, hero, Map.of()));
  }

  @Test
  void theTracksMaxHealthIsUsedWhenItHoldsIt() {
    ReplayPlayerTrackDomain track = ItemTestData.track(150);
    track.setMaxHp(new int[150]);
    track.getMaxHp()[sampleOf(120)] = 2000;
    track.getHpPercent()[sampleOf(120)] = 15;

    SaveJudge.Verdict verdict = judge(List.of(hit(119.5, 1400, 300)), track).judge(120.0);

    assertThat(verdict.maxHp()).hasValue(2000);
    assertThat(verdict.hpNow()).hasValue(300);
    assertThat(verdict.saved()).isTrue();
  }

  @Test
  void withoutMaxHealthInTheTrackTheHitsEstimateIt() {
    ReplayPlayerTrackDomain track = ItemTestData.track(150);
    track.getHpPercent()[sampleOf(120)] = 30;

    SaveJudge.Verdict verdict = judge(List.of(hit(120.2, 450, 300)), track).judge(121.0);

    assertThat(verdict.maxHp()).hasValue(1000);
  }

  @Test
  void aDeathWithinTheSurviveWindowIsNoSave() {
    List<CombatEvent> events =
        List.of(hit(120.2, 450, 300), hit(122.0, 300, 0), ItemTestData.death(122.0));

    SaveJudge.Verdict verdict = judge(events, null).judge(121.0);

    assertThat(verdict.survived()).isFalse();
    assertThat(verdict.saved()).isFalse();
  }
}
