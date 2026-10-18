package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.HERO;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.change;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.sampleOf;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.use;
import static org.assertj.core.api.Assertions.assertThat;

class BottleAspectsTest {
  private static final String BOTTLE = "item_bottle";
  private static final ItemEfficiencyEntry ENTRY =
      ItemTestData.entry(
          "bottle",
          List.of(BottleRunesAspect.NAME, ChargeRestoreAspect.NAME),
          List.of("bottle"),
          Map.of("restoreFromConstants", 1.0));
  private static final ItemKits KITS =
      key ->
          "bottle".equals(key)
              ? Optional.of(
                  ItemConstant.builder()
                      .cost(675L)
                      .attrib(
                          List.of(
                              Map.of("key", "health_restore", "value", "110"),
                              Map.of("key", "mana_restore", "value", "60")))
                      .build())
              : Optional.empty();

  private final List<ItemAspect> aspects =
      List.of(new BottleRunesAspect(), new ChargeRestoreAspect());

  private ItemEfficiency evaluate(HeroInputs hero) {
    List<ItemEfficiency> rows = ItemTestData.judge(ENTRY, aspects, KITS, 900, List.of(hero));
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  private static CombatEvent heal(double second, int amount, String inflictor) {
    return new CombatEvent(
        CombatEventKind.HEAL,
        second,
        HERO,
        HERO,
        inflictor,
        CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
        amount,
        0,
        0,
        0,
        0,
        0,
        0);
  }

  private static RunePickupDomain rune(int seconds, String type, Double effectEnd) {
    return RunePickupDomain.builder()
        .playerSlot(0L)
        .seconds(seconds)
        .runeType(type)
        .effectEndSeconds(effectEnd)
        .build();
  }

  private static HeroInputs hero(
      List<CombatEvent> events, ReplayPlayerTrackDomain track, List<RunePickupDomain> runes) {
    return new HeroInputs(
        0,
        HERO,
        List.of(change(-60, StateChangeKind.ITEM_ADDED, BOTTLE)),
        events,
        track,
        List.of(),
        List.of(),
        MatchFacts.EMPTY,
        runes);
  }

  @Test
  void healthComesFromTheLoggedHealsAndManaFromTheConstantsCappedByWhatWasMissing() {
    ReplayPlayerTrackDomain track = ItemTestData.track(150);
    track.getManaPercent()[sampleOf(200)] = 90;
    track.getMana()[sampleOf(200)] = 360;
    List<CombatEvent> events =
        List.of(
            use(200, BOTTLE, ""),
            heal(201, 40, BOTTLE),
            heal(202, 70, BOTTLE),
            heal(203, 30, "item_magic_wand"),
            heal(260, 50, BOTTLE),
            use(300, BOTTLE, ""));

    ItemEfficiency result = evaluate(hero(events, track, List.of()));

    assertThat(result.count()).isEqualTo(2);
    assertThat(result.kind()).isEqualTo("bottle-runes");
    assertThat(result.figures()).containsEntry("hp", 110.0).containsEntry("mana", 40.0);
    assertThat(result.flags()).contains("MANA_ESTIMATED").doesNotContain("HP_ESTIMATED");
  }

  @Test
  void withoutAnyHealOfTheBottleHealthIsTheConstantsAmountCappedByTheMissingHealth() {
    ReplayPlayerTrackDomain track = ItemTestData.track(150);
    track.getHpPercent()[sampleOf(200)] = 95;
    List<CombatEvent> events =
        List.of(
            new CombatEvent(
                CombatEventKind.DAMAGE,
                199.5,
                "npc_dota_hero_lion",
                HERO,
                "",
                CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
                50,
                950,
                0,
                0,
                0,
                0,
                0),
            use(200, BOTTLE, ""));

    ItemEfficiency result = evaluate(hero(events, track, List.of()));

    assertThat(result.figures().get("hp")).isEqualTo(50.0);
    assertThat(result.flags()).contains("HP_ESTIMATED");
    assertThat(result.gain()).isEqualTo(50 * 0.3, org.assertj.core.data.Offset.offset(0.001));
  }

  @Test
  void runesCountedWhileHoldingTheBottleAndBottledWhenNoEffectBeganAtThePickup() {
    List<RunePickupDomain> runes =
        List.of(
            rune(-100, "HASTE", null),
            rune(120, "DOUBLE_DAMAGE", null),
            rune(240, "HASTE", 250.0),
            rune(360, "REGENERATION", null),
            rune(480, "BOUNTY", null));

    ItemEfficiency result =
        evaluate(hero(List.of(use(130, BOTTLE, "")), ItemTestData.track(150), runes));

    assertThat(result.figures()).containsEntry("runes", 4.0).containsEntry("bottled", 1.0);
    assertThat(result.flags()).contains("RUNES_BOTTLED_ESTIMATED");
    assertThat(result.rows().stream().filter(r -> r.label().equals("RUNE"))).hasSize(4);
  }

  @Test
  void aBottleNeverUsedAndNoRunesHasZeroFiguresAndNoRuneFlag() {
    ItemEfficiency result = evaluate(hero(List.of(), ItemTestData.track(150), List.of()));

    assertThat(result.count()).isZero();
    assertThat(result.figures()).containsEntry("runes", 0.0).containsEntry("hp", 0.0);
    assertThat(result.flags()).doesNotContain("RUNES_BOTTLED_ESTIMATED", "HP_ESTIMATED");
  }
}
