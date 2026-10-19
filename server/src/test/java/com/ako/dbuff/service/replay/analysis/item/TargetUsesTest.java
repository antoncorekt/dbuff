package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.change;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.hero;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.sampleOf;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.use;
import static org.assertj.core.api.Assertions.assertThat;

class TargetUsesTest {
  private static final String ME = ItemTestData.HERO;
  private static final String ALLY = "npc_dota_hero_crystal_maiden";
  private static final String ENEMY = "npc_dota_hero_lion";
  private static final String GLIMMER = "item_glimmer_cape";
  private static final String FORCE = "item_force_staff";
  private static final int HEROES = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;
  private static final Map<String, Long> SLOTS = Map.of(ME, 0L, ALLY, 1L, ENEMY, 128L);

  private static final ItemEfficiencyEntry GLIMMER_ENTRY =
      ItemTestData.entry(
          "glimmer_cape",
          List.of(TargetUseAspect.NAME, SaveAspect.NAME),
          List.of("glimmer_cape"),
          Map.of());
  private static final ItemEfficiencyEntry FORCE_ENTRY =
      ItemTestData.entry(
          "force_staff",
          List.of(TargetUseAspect.NAME, SaveAspect.NAME, ControlAspect.NAME),
          List.of("force_staff"),
          Map.of());
  private static final ItemKits KITS =
      ItemTestData.kits(Map.of("glimmer_cape", 1950L, "force_staff", 2200L));
  private static final List<ItemAspect> ASPECTS =
      List.of(new TargetUseAspect(), new SaveAspect(), new ControlAspect());

  private static ReplayPlayerTrackDomain lowAt120() {
    ReplayPlayerTrackDomain track = ItemTestData.track(150);
    track.getHpPercent()[sampleOf(120)] = 30;
    track.setGold(new int[track.getSampleCount()]);
    track.getGold()[sampleOf(120)] = 600;
    return track;
  }

  private static CombatEvent hit(String target, double second, int damage, int healthAfter) {
    return new CombatEvent(
        CombatEventKind.DAMAGE,
        second,
        ENEMY,
        target,
        "",
        HEROES,
        damage,
        healthAfter,
        0,
        0,
        0,
        0,
        0);
  }

  private static CombatEvent modifier(String name, String target, double second) {
    return new CombatEvent(
        CombatEventKind.MODIFIER_ADD,
        second,
        "",
        target,
        name,
        CombatEvent.TARGET_HERO,
        0,
        0,
        0,
        0,
        0,
        0,
        0);
  }

  private static CombatEvent paid(CombatEventKind kind, double second, int value) {
    return new CombatEvent(
        kind, second, "", ME, "", 0, value, 0, kind == CombatEventKind.XP ? 1 : 12, 0, 0, 0, 0);
  }

  private static MatchFacts match(
      List<CombatEvent> events, Map<String, ReplayPlayerTrackDomain> t) {
    return new MatchFacts(
        events,
        SLOTS,
        Map.of(
            0L,
            List.of(
                ItemTestData.minute(1, 600, 0),
                ItemTestData.minute(2, 1200, 0),
                ItemTestData.minute(3, 1800, 0)),
            1L,
            List.of(
                ItemTestData.minute(1, 600, 0),
                ItemTestData.minute(2, 1200, 0),
                ItemTestData.minute(3, 1800, 0))),
        t);
  }

  private static List<ReplayStateChangeDomain> bought(String item) {
    return List.of(change(-60, StateChangeKind.ITEM_ADDED, item));
  }

  private static ItemEfficiency evaluate(
      ItemEfficiencyEntry entry,
      List<ReplayStateChangeDomain> changes,
      List<CombatEvent> mine,
      ReplayPlayerTrackDomain track,
      MatchFacts match) {
    HeroInputs base = hero(changes, mine, track, List.of(), List.of());
    List<ItemEfficiency> rows =
        ItemTestData.judge(entry, ASPECTS, KITS, 600, List.of(ItemTestData.heroWith(base, match)));
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  @Test
  void aCastOnAnAllyAtLowHealthUnderHeavyDamageIsAnAllySave() {
    List<CombatEvent> all =
        List.of(
            hit(ALLY, 120.2, 450, 300),
            modifier("modifier_item_glimmer_cape_fade", ALLY, 121.3),
            hit(ALLY, 122.0, 100, 250));
    List<CombatEvent> mine = new ArrayList<>(List.of(use(121.0, GLIMMER, ALLY)));

    ItemEfficiency result =
        evaluate(
            GLIMMER_ENTRY,
            bought(GLIMMER),
            mine,
            ItemTestData.track(150),
            match(all, Map.of(ALLY, lowAt120())));

    assertThat(result.kind()).isEqualTo(TargetUseAspect.NAME);
    assertThat(result.count()).isEqualTo(1);
    assertThat(result.figures())
        .containsEntry("saves", 1.0)
        .containsEntry("allySaves", 1.0)
        .containsEntry("selfSaves", 0.0)
        .containsEntry("saveGain", 900.0);
    assertThat(result.gain()).isEqualTo(900.0);
    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().getFirst().target()).isEqualTo(ALLY);
    assertThat(result.rows().getFirst().figures())
        .containsEntry("saved", 1.0)
        .containsEntry("effective", 1.0);
  }

  @Test
  void aCastOnHimselfIsASelfSave() {
    List<CombatEvent> all =
        List.of(
            hit(ME, 120.2, 450, 300),
            modifier("modifier_item_glimmer_cape_fade", ME, 121.1),
            hit(ME, 122.0, 100, 250));

    ItemEfficiency result =
        evaluate(
            GLIMMER_ENTRY,
            bought(GLIMMER),
            new ArrayList<>(List.of(use(121.0, GLIMMER, ME), all.get(0), all.get(2))),
            lowAt120(),
            match(all, Map.of(ME, lowAt120())));

    assertThat(result.figures()).containsEntry("selfSaves", 1.0).containsEntry("allySaves", 0.0);
    assertThat(result.rows().getFirst().target()).isNull();
  }

  @Test
  void glimmerWithoutItsModifierOnTheTargetDidNothing() {
    List<CombatEvent> all =
        List.of(
            hit(ALLY, 120.2, 450, 300),
            modifier("modifier_item_glimmer_cape_fade", ALLY, 125.0),
            hit(ALLY, 122.0, 100, 250));

    ItemEfficiency result =
        evaluate(
            GLIMMER_ENTRY,
            bought(GLIMMER),
            List.of(use(121.0, GLIMMER, ALLY)),
            ItemTestData.track(150),
            match(all, Map.of(ALLY, lowAt120())));

    assertThat(result.count()).isEqualTo(1);
    assertThat(result.figures()).containsEntry("effective", 0.0).containsEntry("saves", 0.0);
    assertThat(result.gain()).isZero();
  }

  @Test
  void aCastWithNoDangerIsNoSave() {
    ItemEfficiency result =
        evaluate(
            FORCE_ENTRY,
            bought(FORCE),
            List.of(use(300.0, FORCE, ALLY)),
            ItemTestData.track(150),
            match(List.of(), Map.of(ALLY, ItemTestData.track(150))));

    assertThat(result.figures()).containsEntry("saves", 0.0).containsEntry("setUps", 0.0);
    assertThat(result.gain()).isZero();
  }

  @Test
  void aPushOntoAnEnemyWhoDiesWithinTheWindowEarnsAShareOfTheKill() {
    List<CombatEvent> all =
        List.of(
            new CombatEvent(CombatEventKind.DEATH, 135, ME, ENEMY, "", HEROES, 0, 0, 0, 0, 0, 0, 0),
            paid(CombatEventKind.GOLD, 135, 400));

    ItemEfficiency result =
        evaluate(
            FORCE_ENTRY,
            bought(FORCE),
            List.of(use(130.0, FORCE, ENEMY), use(300.0, FORCE, ENEMY)),
            ItemTestData.track(150),
            match(all, Map.of()));

    assertThat(result.count()).isEqualTo(2);
    assertThat(result.figures()).containsEntry("setUps", 1.0).containsEntry("controlGain", 100.0);
    assertThat(result.gain()).isEqualTo(100.0);
    assertThat(result.rows())
        .anySatisfy(
            r -> {
              assertThat(r.target()).isEqualTo(ENEMY);
              assertThat(r.figures()).containsEntry("setUp", 1.0);
            });
  }

  @Test
  void aCastOnAnUnknownUnitHasNoEffect() {
    ItemEfficiency result =
        evaluate(
            FORCE_ENTRY,
            bought(FORCE),
            List.of(use(130.0, FORCE, "npc_dota_creep_goodguys_melee")),
            ItemTestData.track(150),
            match(List.of(), Map.of()));

    assertThat(result.figures()).containsEntry("effective", 0.0);
    assertThat(result.gain()).isZero();
  }

  @Test
  void aGlimmerModifierBeforeTheCastOrFromAnotherCasterIsNotItsEffect() {
    CombatEvent early = modifier("modifier_item_glimmer_cape_fade", ALLY, 119.5);
    CombatEvent foreign =
        new CombatEvent(
            CombatEventKind.MODIFIER_ADD,
            121.2,
            ENEMY,
            ALLY,
            "modifier_item_glimmer_cape_fade",
            CombatEvent.TARGET_HERO,
            0,
            0,
            0,
            0,
            0,
            0,
            0);

    ItemEfficiency result =
        evaluate(
            GLIMMER_ENTRY,
            bought(GLIMMER),
            List.of(use(121.0, GLIMMER, ALLY)),
            ItemTestData.track(150),
            match(List.of(early, foreign), Map.of(ALLY, lowAt120())));

    assertThat(result.figures()).containsEntry("effective", 0.0);
  }

  @Test
  void onlyTheFirstCastBeforeADeathEarnsTheControlShare() {
    List<CombatEvent> all =
        List.of(
            new CombatEvent(CombatEventKind.DEATH, 135, ME, ENEMY, "", HEROES, 0, 0, 0, 0, 0, 0, 0),
            paid(CombatEventKind.GOLD, 135, 400));

    ItemEfficiency result =
        evaluate(
            FORCE_ENTRY,
            bought(FORCE),
            List.of(use(129.0, FORCE, ENEMY), use(131.0, FORCE, ENEMY)),
            ItemTestData.track(150),
            match(all, Map.of()));

    assertThat(result.figures()).containsEntry("setUps", 1.0).containsEntry("controlGain", 100.0);
  }
}
