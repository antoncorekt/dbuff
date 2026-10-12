package com.ako.dbuff.service.replay.analysis.death;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeathAssessorTest {

  private static final int VICTIM = 4;
  private static final double DEATH = 200;
  private static final String BKB = "item_black_king_bar";

  private final DeathAssessor assessor = new DeathAssessor(tuning(0));
  private final DeathAssessor defaultAssessor =
      new DeathAssessor(new ReplayAnalysisProperties().deathTuning());

  @Test
  void assess_doesNotBlameAMissingBkbOnADeathBeforeOneWasAffordable() {
    ReplayAnalysisProperties defaults = new ReplayAnalysisProperties();
    assertThat(defaults.getDeathNoBkbMinSeconds()).isEqualTo(720);
    assertThat(noBkbAt(96)).isEqualTo(DeathReason.OTHER);
    assertThat(noBkbAt(719)).isEqualTo(DeathReason.OTHER);
    assertThat(noBkbAt(720)).isEqualTo(DeathReason.NO_BKB);
    assertThat(noBkbAt(2000)).isEqualTo(DeathReason.NO_BKB);
  }

  @Test
  void assess_explainsADeathByWhatBkbWasDoing_mostSpecificFirst() {
    assertThat(reason(timeline(BKB), List.of(immuneAdd(190, 10), immuneRemove(199))))
        .isEqualTo(DeathReason.BKB_EXPIRED);
    assertThat(reason(timeline(BKB), List.of(immuneAdd(190, 60))))
        .isEqualTo(DeathReason.DIED_IMMUNE);
    assertThat(reason(timeline(BKB), List.of())).isEqualTo(DeathReason.BKB_READY_UNUSED);
    HeroStateTimeline cooling = timeline(BKB);
    cooling.add(new HeroStateTimeline.Change(150, StateChangeKind.COOLDOWN, BKB, 230));
    assertThat(reason(cooling, List.of())).isEqualTo(DeathReason.BKB_ON_COOLDOWN);
  }

  @Test
  void assess_fallsBackToWhatTheSituationSays_andNoBkbIsOnlyTheLastResort() {
    List<CombatEvent> stuns = List.of(stun(195, 400), stun(197, 300));
    assertThat(reason(new HeroStateTimeline(), stuns)).isEqualTo(DeathReason.DISABLE_CHAIN);
    assertThat(reason(new HeroStateTimeline(), List.of(damage(199, 1600))))
        .isEqualTo(DeathReason.BURST);
    assertThat(reason(new HeroStateTimeline(), List.of())).isEqualTo(DeathReason.NO_BKB);
  }

  @Test
  void assess_callsAHeroWhoIsAliveAgainAtOnceReincarnated() {
    Map<Integer, FightAssessor.Trace> traces = traces(1);

    var a =
        assessor.assess(
            death(CombatEvent.ATTACKER_HERO), traces, new HeroStateTimeline(), List.of(), null);

    assertThat(a.reincarnated()).isTrue();
    assertThat(a.reason()).isEqualTo(DeathReason.REINCARNATED);
  }

  @Test
  void assess_findsAHeroCaughtAloneAndWhoKilledHim() {
    Map<Integer, FightAssessor.Trace> traces = traces(0);
    traces.put(VICTIM, trace(0, 0, 0));
    traces.put(5, trace(300, 0, 1));
    traces.put(6, trace(300, 100, 1));
    for (int ally : new int[] {0, 1, 2, 3}) {
      traces.put(ally, trace(9000, 9000, 1));
    }

    var alone =
        assessor.assess(
            death(CombatEvent.ATTACKER_HERO), traces, new HeroStateTimeline(), List.of(), null);
    traces.put(3, trace(200, 0, 1));
    var withAlly =
        assessor.assess(
            death(CombatEvent.ATTACKER_HERO), traces, new HeroStateTimeline(), List.of(), null);

    assertThat(alone.isolated()).isTrue();
    assertThat(alone.reason()).isEqualTo(DeathReason.CAUGHT_OUT);
    assertThat(withAlly.isolated()).isFalse();
    assertThat(kind("npc_dota_goodguys_tower1_mid", 0)).isEqualTo(KillerKind.TOWER);
    assertThat(kind("npc_dota_creep_badguys_melee", 0)).isEqualTo(KillerKind.CREEP);
    assertThat(kind("npc_dota_neutral_alpha_wolf", 0)).isEqualTo(KillerKind.NEUTRAL);
    assertThat(kind("", 0)).isEqualTo(KillerKind.OTHER);
  }

  @Test
  void assess_reportsWhatHeldAndTheBiggestDamageSources() {
    HeroStateTimeline state = timeline(BKB);
    state.add(new HeroStateTimeline.Change(10, StateChangeKind.ITEM_ADDED, "item_glimmer_cape", 0));
    state.add(new HeroStateTimeline.Change(10, StateChangeKind.ITEM_ADDED, "item_tpscroll", 0));
    List<CombatEvent> events =
        List.of(damage(199, 900, "npc_dota_hero_a"), damage(198, 300, "npc_dota_hero_b"));

    var a = assessor.assess(death(CombatEvent.ATTACKER_HERO), traces(0), state, events, null);

    assertThat(a.saveItemsOwned()).isEqualTo("item_glimmer_cape");
    assertThat(a.teleportReady()).isTrue();
    assertThat(a.damageLookback()).isEqualTo(1200);
    assertThat(a.topSources()).isEqualTo("npc_dota_hero_a:900,npc_dota_hero_b:300");
  }

  @Test
  void assess_callsAHeroWhoStoodWellInFrontOfHisRangeOutOfPosition_afterTheSharperCauses() {
    assertThat(positioned(-2, List.of())).isEqualTo(DeathReason.OUT_OF_POSITION);
    assertThat(positioned(-1, List.of())).isEqualTo(DeathReason.NO_BKB);
    assertThat(positioned(null, List.of())).isEqualTo(DeathReason.NO_BKB);
    assertThat(positioned(-3, List.of(stun(195, 400), stun(197, 300))))
        .isEqualTo(DeathReason.DISABLE_CHAIN);
    assertThat(positioned(-3, List.of(damage(199, 1600)))).isEqualTo(DeathReason.BURST);
  }

  @Test
  void assess_namesTheDisablesThatWentThroughSpellImmunity() {
    CombatEvent pierce =
        new CombatEvent(
            CombatEventKind.MODIFIER_ADD,
            192,
            "",
            "npc_dota_hero_victim",
            "modifier_enigma_black_hole_pull",
            CombatEvent.TARGET_HERO,
            0,
            0,
            300,
            0,
            0,
            0,
            0);

    var a =
        assessor.assess(
            death(CombatEvent.ATTACKER_HERO),
            traces(0),
            timeline(BKB),
            List.of(immuneAdd(190, 60), pierce),
            null);

    assertThat(a.reason()).isEqualTo(DeathReason.BKB_PIERCED);
    assertThat(a.immunityPiercedBy()).isEqualTo("modifier_enigma_black_hole_pull");
    assertThat(reason(timeline(BKB), List.of(immuneAdd(190, 60))))
        .isEqualTo(DeathReason.DIED_IMMUNE);
  }

  @Test
  void assess_doesNotCallADisableTakenOffAtOnceOrLongBeforeAPierce() {
    CombatEvent blocked = modifier(CombatEventKind.MODIFIER_ADD, 192, "modifier_stunned", 300, 0);
    CombatEvent stripped =
        modifier(CombatEventKind.MODIFIER_REMOVE, 192.1, "modifier_stunned", 0, 0);
    CombatEvent old = modifier(CombatEventKind.MODIFIER_ADD, 100, "modifier_stunned", 300, 0);

    var a =
        assessor.assess(
            death(CombatEvent.ATTACKER_HERO),
            traces(0),
            timeline(BKB),
            List.of(old, immuneAdd(190, 60), blocked, stripped),
            null);

    assertThat(a.reason()).isEqualTo(DeathReason.DIED_IMMUNE);
    assertThat(a.immunityPiercedBy()).isNull();
  }

  @Test
  void assess_countsADisableOnlyUntilItWasRemoved() {
    List<CombatEvent> events =
        List.of(
            stun(195, 800),
            modifier(CombatEventKind.MODIFIER_REMOVE, 196, "modifier_stunned", 0, 0));

    assertThat(reason(new HeroStateTimeline(), events)).isEqualTo(DeathReason.NO_BKB);
  }

  private static DeathAssessor.Tuning tuning(int noBkbMinSeconds) {
    DeathAssessor.Tuning t = new ReplayAnalysisProperties().deathTuning();
    return new DeathAssessor.Tuning(
        t.lookbackSeconds(),
        t.burstDamage(),
        t.isolationRadius(),
        t.disableSeconds(),
        t.reincarnationSeconds(),
        t.outOfPositionRanks(),
        noBkbMinSeconds);
  }

  private DeathReason noBkbAt(double second) {
    var death =
        new DeathAssessor.Death(second, VICTIM, "npc_dota_hero_x", CombatEvent.ATTACKER_HERO, 0);
    return defaultAssessor
        .assess(death, traces(0), new HeroStateTimeline(), List.of(), null)
        .reason();
  }

  private DeathReason positioned(Integer delta, List<CombatEvent> events) {
    return assessor
        .assess(
            death(CombatEvent.ATTACKER_HERO),
            traces(0),
            new HeroStateTimeline(),
            events,
            null,
            delta)
        .reason();
  }

  private DeathReason reason(HeroStateTimeline state, List<CombatEvent> events) {
    return assessor
        .assess(death(CombatEvent.ATTACKER_HERO), traces(0), state, events, null)
        .reason();
  }

  private KillerKind kind(String killer, int flags) {
    var death = new DeathAssessor.Death(DEATH, VICTIM, killer, flags, 0);
    return assessor.assess(death, traces(0), new HeroStateTimeline(), List.of(), null).killerKind();
  }

  private static DeathAssessor.Death death(int killerFlags) {
    return new DeathAssessor.Death(DEATH, VICTIM, "npc_dota_hero_x", killerFlags, 0);
  }

  private static HeroStateTimeline timeline(String owned) {
    HeroStateTimeline timeline = new HeroStateTimeline();
    timeline.add(new HeroStateTimeline.Change(10, StateChangeKind.ITEM_ADDED, owned, 0));
    return timeline;
  }

  /** Ten players far apart and alive, or dead from {@code flags}. */
  private static Map<Integer, FightAssessor.Trace> traces(int victimFlags) {
    Map<Integer, FightAssessor.Trace> traces = new HashMap<>();
    for (int p = 0; p < 10; p++) {
      traces.put(p, trace(p * 5000, 0, 1));
    }
    traces.put(VICTIM, trace(0, 0, victimFlags));
    return traces;
  }

  private static FightAssessor.Trace trace(int x, int y, int flags) {
    int[] xs = new int[80];
    int[] ys = new int[80];
    int[] levels = new int[80];
    int[] alive = new int[80];
    Arrays.fill(xs, x);
    Arrays.fill(ys, y);
    Arrays.fill(levels, 10);
    Arrays.fill(alive, flags);
    return new FightAssessor.Trace(0, 5, xs, ys, levels, alive);
  }

  private static CombatEvent immuneAdd(double second, int seconds) {
    return modifier(
        CombatEventKind.MODIFIER_ADD, second, "modifier_black_king_bar_immune", 0, seconds * 100);
  }

  private static CombatEvent immuneRemove(double second) {
    return modifier(
        CombatEventKind.MODIFIER_REMOVE, second, "modifier_black_king_bar_immune", 0, 0);
  }

  private static CombatEvent stun(double second, int centiseconds) {
    return modifier(CombatEventKind.MODIFIER_ADD, second, "modifier_stunned", centiseconds, 0);
  }

  private static CombatEvent modifier(
      CombatEventKind kind, double second, String name, int aux, int aux2) {
    return new CombatEvent(
        kind,
        second,
        "",
        "npc_dota_hero_victim",
        name,
        CombatEvent.TARGET_HERO,
        0,
        0,
        aux,
        aux2,
        0,
        0,
        0);
  }

  private static CombatEvent damage(double second, int value) {
    return damage(second, value, "npc_dota_hero_a");
  }

  private static CombatEvent damage(double second, int value, String attacker) {
    return new CombatEvent(
        CombatEventKind.DAMAGE,
        second,
        attacker,
        "npc_dota_hero_victim",
        "",
        CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
        value,
        0,
        0,
        0,
        0,
        0,
        0);
  }
}
