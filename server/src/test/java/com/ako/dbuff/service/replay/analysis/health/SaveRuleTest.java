package com.ako.dbuff.service.replay.analysis.health;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SaveRuleTest {
  private static final String HERO = "npc_dota_hero_axe";
  private static final String ATTACKER = "npc_dota_hero_lion";
  private static final int START = -90;
  private static final int STEP = 5;

  private static CombatEvent event(CombatEventKind kind, double second, int damage, int after) {
    return new CombatEvent(
        kind,
        second,
        ATTACKER,
        HERO,
        "",
        CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
        damage,
        after,
        0,
        0,
        0,
        0,
        0);
  }

  private static ReplayPlayerTrackDomain track() {
    int[] full = new int[150];
    Arrays.fill(full, 100);
    return ReplayPlayerTrackDomain.builder()
        .startSeconds(START)
        .stepSeconds(STEP)
        .sampleCount(150)
        .hpPercent(full)
        .build();
  }

  private static int sampleOf(double second) {
    return (int) Math.floor((second - START) / (double) STEP);
  }

  private static SaveRule rule(List<CombatEvent> events, ReplayPlayerTrackDomain track) {
    return new SaveRule(new HeroHealth(HERO, events, track), SaveRule.Parameters.defaults());
  }

  @Test
  void theTracksMaxHealthIsUsedWhenItHoldsIt() {
    ReplayPlayerTrackDomain track = track();
    track.setMaxHp(new int[150]);
    track.getMaxHp()[sampleOf(120)] = 2000;
    track.getHpPercent()[sampleOf(120)] = 15;

    SaveRule.Verdict verdict =
        rule(List.of(event(CombatEventKind.DAMAGE, 119.5, 1400, 300)), track).judge(120.0);

    assertThat(verdict.maxHp()).hasValue(2000);
    assertThat(verdict.hpNow()).hasValue(300);
    assertThat(verdict.saved()).isTrue();
  }

  @Test
  void withoutMaxHealthInTheTrackTheHitsEstimateIt() {
    ReplayPlayerTrackDomain track = track();
    track.getHpPercent()[sampleOf(120)] = 30;

    SaveRule.Verdict verdict =
        rule(List.of(event(CombatEventKind.DAMAGE, 120.2, 450, 300)), track).judge(121.0);

    assertThat(verdict.maxHp()).hasValue(1000);
  }

  @Test
  void aDeathWithinTheSurviveWindowIsNoSave() {
    List<CombatEvent> events =
        List.of(
            event(CombatEventKind.DAMAGE, 120.2, 450, 300),
            event(CombatEventKind.DAMAGE, 122.0, 300, 0),
            event(CombatEventKind.DEATH, 122.0, 0, 0));

    SaveRule.Verdict verdict = rule(events, null).judge(121.0);

    assertThat(verdict.survived()).isFalse();
    assertThat(verdict.saved()).isFalse();
  }

  @Test
  void withoutMaxHealthTheAbsoluteAmountsDecide() {
    List<CombatEvent> events = List.of(event(CombatEventKind.DAMAGE, 120.0, 400, 250));

    SaveRule.Verdict verdict = rule(events, null).judge(121.0);

    assertThat(verdict.maxHp()).isEmpty();
    assertThat(verdict.saved()).isTrue();
  }
}
