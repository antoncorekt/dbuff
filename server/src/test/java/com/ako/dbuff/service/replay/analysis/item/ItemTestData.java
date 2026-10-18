package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builders of the synthetic facts the item evaluator tests share. */
final class ItemTestData {
  static final String HERO = "npc_dota_hero_axe";
  static final int STEP = 5;
  static final int START = -90;

  private ItemTestData() {}

  static ItemKits kits(Map<String, Long> costs) {
    return key ->
        Optional.ofNullable(costs.get(key))
            .map(
                cost ->
                    ItemConstant.builder()
                        .cost(cost)
                        .attrib(
                            "hand_of_midas".equals(key)
                                ? List.of(
                                    Map.of("key", "bonus_gold", "value", "160"),
                                    Map.of("key", "bonus_attack_speed", "value", "35"))
                                : List.of(Map.of("key", "bonus_damage", "value", "25")))
                        .build());
  }

  static ItemEfficiencyEntry entry(
      String key, List<String> aspects, List<String> items, Map<String, Double> parameters) {
    return new ItemEfficiencyEntry(
        key, "farming", items, aspects, List.of(), List.of(), parameters);
  }

  /** The rows of the entry's own aspects, taken from {@code available} in the order it lists. */
  static List<ItemEfficiency> judge(
      ItemEfficiencyEntry entry,
      List<ItemAspect> available,
      ItemKits kits,
      int gameEndSeconds,
      List<HeroInputs> heroes) {
    List<ItemAspect> own =
        entry.aspects().stream()
            .map(name -> available.stream().filter(a -> a.name().equals(name)).findFirst())
            .flatMap(java.util.Optional::stream)
            .toList();
    return AspectSum.judge(entry, own, kits, gameEndSeconds, heroes);
  }

  static ReplayStateChangeDomain change(double second, StateChangeKind kind, String name) {
    return ReplayStateChangeDomain.builder()
        .gameSeconds(second)
        .kind(kind.name())
        .name(name)
        .build();
  }

  static ReplayStateChangeDomain charges(double second, String name, int charges) {
    ReplayStateChangeDomain c = change(second, StateChangeKind.ITEM_CHARGES, name);
    c.setCharges(charges);
    return c;
  }

  static CombatEvent use(double second, String item, String target) {
    return new CombatEvent(
        CombatEventKind.ITEM,
        second,
        HERO,
        target,
        item,
        CombatEvent.ATTACKER_HERO,
        0,
        0,
        0,
        0,
        0,
        0,
        0);
  }

  static CombatEvent hit(double second, int damage, int healthAfter) {
    return new CombatEvent(
        CombatEventKind.DAMAGE,
        second,
        "npc_dota_hero_lion",
        HERO,
        "",
        CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
        damage,
        healthAfter,
        0,
        0,
        0,
        0,
        0);
  }

  /** Damage the hero dealt to an enemy hero; {@code inflictor} is empty for an attack. */
  static CombatEvent dealt(double second, int damage, String inflictor) {
    return new CombatEvent(
        CombatEventKind.DAMAGE,
        second,
        HERO,
        "npc_dota_hero_lion",
        inflictor,
        CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
        damage,
        1000,
        0,
        0,
        0,
        0,
        0);
  }

  static CombatEvent death(double second) {
    return new CombatEvent(
        CombatEventKind.DEATH,
        second,
        "npc_dota_hero_lion",
        HERO,
        "",
        CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
        0,
        0,
        0,
        0,
        0,
        0,
        0);
  }

  static CombatEvent paid(CombatEventKind kind, double second, int value) {
    int reason = kind == CombatEventKind.XP ? 2 : 0;
    return new CombatEvent(kind, second, "", HERO, "", 0, value, 0, reason, 0, 0, 0, 0);
  }

  /** A track from -90 s with {@code samples} samples, full health, 400 mana of 400. */
  static ReplayPlayerTrackDomain track(int samples) {
    int[] full = new int[samples];
    Arrays.fill(full, 100);
    int[] mana = new int[samples];
    Arrays.fill(mana, 400);
    return ReplayPlayerTrackDomain.builder()
        .startSeconds(START)
        .stepSeconds(STEP)
        .sampleCount(samples)
        .hpPercent(full)
        .manaPercent(full.clone())
        .mana(mana)
        .build();
  }

  static int sampleOf(double second) {
    return (int) Math.floor((second - START) / (double) STEP);
  }

  static CreepKillDomain kill(double second, String creep, int gold, Integer camp) {
    return CreepKillDomain.builder()
        .seconds(second)
        .creepName(creep)
        .gold(gold)
        .camp(camp)
        .denied(false)
        .build();
  }

  static ActivitySegment farming(int from, int to) {
    return new ActivitySegment(from, to, Activity.LANE_FARM, Zone.Kind.LANE, null, null);
  }

  static ReplayPlayerMinuteDomain minute(int minute, long creepGold, long lostToDeath) {
    return ReplayPlayerMinuteDomain.builder()
        .gameMinute(minute)
        .creepKillGold(creepGold)
        .goldLostToDeath(lostToDeath)
        .build();
  }

  static HeroInputs heroWith(HeroInputs base, MatchFacts match) {
    return new HeroInputs(
        base.slot(),
        base.hero(),
        base.changes(),
        base.events(),
        base.track(),
        base.creepKills(),
        base.activity(),
        match,
        0);
  }

  static HeroInputs hero(
      List<ReplayStateChangeDomain> changes,
      List<CombatEvent> events,
      ReplayPlayerTrackDomain track,
      List<CreepKillDomain> kills,
      List<ActivitySegment> activity) {
    return new HeroInputs(0, HERO, changes, events, track, kills, activity);
  }
}
