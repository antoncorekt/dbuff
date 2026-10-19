package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.change;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DamageAspectsTest {
  private static final String ME = ItemTestData.HERO;
  private static final String ALLY = "npc_dota_hero_crystal_maiden";
  private static final String ENEMY = "npc_dota_hero_lion";
  private static final int HEROES = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;
  private static final Map<String, Long> SLOTS = Map.of(ME, 0L, ALLY, 1L, ENEMY, 128L);
  private static final ItemKits KITS =
      ItemTestData.kits(Map.of("maelstrom", 500L, "daedalus", 500L, "butterfly", 500L));
  private static final List<ItemAspect> ASPECTS =
      List.of(new ItemDamageAspect(), new StatUpliftAspect());

  private static ItemEfficiency judge(
      List<String> aspects, List<CombatEvent> mine, List<CombatEvent> all, List<String> bought) {
    return judge(KITS, aspects, mine, all, bought.stream().map(n -> Map.entry(n, 650.0)).toList());
  }

  private static ItemEfficiency judge(
      ItemKits kits,
      List<String> aspects,
      List<CombatEvent> mine,
      List<CombatEvent> all,
      List<Map.Entry<String, Double>> bought) {
    ItemEfficiencyEntry entry =
        ItemTestData.entry("damage_items", aspects, List.of("maelstrom"), Map.of());
    List<ReplayStateChangeDomain> changes = new ArrayList<>();
    changes.add(change(600, StateChangeKind.ITEM_ADDED, "item_maelstrom"));
    bought.forEach(e -> changes.add(change(e.getValue(), StateChangeKind.ITEM_ADDED, e.getKey())));
    HeroInputs hero =
        ItemTestData.heroWith(
            ItemTestData.hero(changes, mine, null, List.of(), List.of()),
            new MatchFacts(all, SLOTS, Map.of()));
    return ItemTestData.judge(entry, ASPECTS, kits, 900, List.of(hero)).getFirst();
  }

  private static CombatEvent hit(String by, double second, int value, String inflictor) {
    return new CombatEvent(
        CombatEventKind.DAMAGE, second, by, ENEMY, inflictor, HEROES, value, 500, 0, 0, 0, 0, 0);
  }

  private static CombatEvent died(double second) {
    return new CombatEvent(
        CombatEventKind.DEATH, second, ME, ENEMY, "", HEROES, 0, 0, 0, 0, 0, 0, 0);
  }

  private static CombatEvent bounty(double second, int value) {
    return new CombatEvent(CombatEventKind.GOLD, second, "", ME, "", 0, value, 0, 12, 0, 0, 0, 0);
  }

  @Test
  void aProcThatHelpedAKillGetsItsShareOfTheDeath() {
    List<CombatEvent> mine = List.of(hit(ME, 700, 300, "item_maelstrom"));
    List<CombatEvent> all =
        List.of(mine.getFirst(), hit(ALLY, 702, 700, ""), died(705), bounty(705, 500));

    ItemEfficiency result = judge(List.of("item-damage"), mine, all, List.of());

    assertThat(result.figures())
        .containsEntry("procDamage", 300.0)
        .containsEntry("damageValue", 150.0)
        .containsEntry("killsHelped", 1.0)
        .containsEntry("gain.item-damage", 150.0);
    assertThat(result.gain()).isEqualTo(150.0);
  }

  @Test
  void aProcOnAnEnemyWhoLivedIsWorthOnlyThePokePrice() {
    List<CombatEvent> mine = List.of(hit(ME, 700, 500, "item_maelstrom"));

    ItemEfficiency result = judge(List.of("item-damage"), mine, mine, List.of());

    assertThat(result.figures())
        .containsEntry("killsHelped", 0.0)
        .containsEntry("damageValue", 10.0);
  }

  private static List<CombatEvent> rising(double afterPerMinute) {
    List<CombatEvent> events = new ArrayList<>();
    for (int m = 2; m < 10; m++) {
      events.add(hit(ME, m * 60 + 10, 100 * m, ""));
    }
    for (int m = 10; m < 15; m++) {
      events.add(hit(ME, m * 60 + 10, (int) afterPerMinute, ""));
    }
    return events;
  }

  private static List<CombatEvent> flat() {
    List<CombatEvent> events = new ArrayList<>();
    for (int m = 2; m < 10; m++) {
      events.add(hit(ME, m * 60 + 10, 600, ""));
    }
    for (int m = 10; m < 15; m++) {
      events.add(hit(ME, m * 60 + 10, 1200, ""));
    }
    return events;
  }

  @Test
  void theUpliftIsTheRiseOfTheDamageRateValuedAtThePokePriceWhenNobodyDied() {
    List<CombatEvent> events = flat();

    ItemEfficiency result = judge(List.of("stat-uplift"), events, events, List.of());

    assertThat(result.figures().get("passiveDamage")).isCloseTo(3000.0, within(1e-6));
    assertThat(result.figures().get("damageValue")).isCloseTo(60.0, within(1e-6));
    assertThat(result.figures().get("gain.stat-uplift")).isCloseTo(60.0, within(1e-6));
  }

  @Test
  void twoItemsBoughtCloseTogetherShareTheUpliftByTheirStats() {
    List<CombatEvent> events = flat();

    ItemEfficiency result = judge(List.of("stat-uplift"), events, events, List.of("item_daedalus"));

    assertThat(result.figures().get("passiveDamage")).isCloseTo(1500.0, within(1e-6));
    assertThat(result.figures().get("damageValue")).isCloseTo(30.0, within(1e-6));
    assertThat(result.rows().getFirst().figures()).containsEntry("statShare", 0.5);
  }

  @Test
  void aRiseThatTheTrendBeforeThePurchaseAlreadyExpectedIsNotCreditedToTheItem() {
    List<CombatEvent> events = rising(1300);

    ItemEfficiency result = judge(List.of("stat-uplift"), events, events, List.of());

    assertThat(result.figures().get("passiveDamage")).isCloseTo(500.0, within(1e-6));
    assertThat(result.rows().getFirst().figures().get("driftPerMinute"))
        .isCloseTo(500.0, within(1e-6));
  }

  @Test
  void aRiseBelowTheTrendIsNotNegative() {
    List<CombatEvent> events = rising(1000);

    ItemEfficiency result = judge(List.of("stat-uplift"), events, events, List.of());

    assertThat(result.figures()).containsEntry("passiveDamage", 0.0);
  }

  @Test
  void threeItemsBoughtAboutTwoMinutesApartSplitTheUpliftInThirds() {
    List<CombatEvent> events = flat();

    ItemEfficiency result =
        judge(
            KITS,
            List.of("stat-uplift"),
            events,
            events,
            List.of(Map.entry("item_daedalus", 700.0), Map.entry("item_butterfly", 800.0)));

    assertThat(result.rows().getFirst().figures().get("statShare"))
        .isCloseTo(1.0 / 3, within(1e-9));
  }

  @Test
  void theRecipePartsOfAnotherItemDoNotShrinkTheShare() {
    ItemKits kits =
        key ->
            java.util.Optional.of(
                ItemConstant.builder()
                    .cost(500L)
                    .attrib(List.of(Map.of("key", "bonus_damage", "value", "25")))
                    .components("daedalus".equals(key) ? List.of("demon_edge") : List.of())
                    .build());
    List<CombatEvent> events = flat();

    ItemEfficiency result =
        judge(
            kits,
            List.of("stat-uplift"),
            events,
            events,
            List.of(Map.entry("item_daedalus", 650.0), Map.entry("item_demon_edge", 640.0)));

    assertThat(result.rows().getFirst().figures()).containsEntry("statShare", 0.5);
  }

  @Test
  void theMinibossRadianceBurnIsNotTheItemsDamage() {
    ItemEfficiencyEntry entry =
        ItemTestData.entry("radiance", List.of("item-damage"), List.of("radiance"), Map.of());
    List<CombatEvent> mine =
        List.of(hit(ME, 700, 300, "item_radiance"), hit(ME, 710, 900, "miniboss_radiance"));
    HeroInputs hero =
        ItemTestData.heroWith(
            ItemTestData.hero(
                List.of(change(600, StateChangeKind.ITEM_ADDED, "item_radiance")),
                mine,
                null,
                List.of(),
                List.of()),
            new MatchFacts(mine, SLOTS, Map.of()));

    ItemEfficiency result =
        ItemTestData.judge(
                entry, ASPECTS, ItemTestData.kits(Map.of("radiance", 1000L)), 900, List.of(hero))
            .getFirst();

    assertThat(result.figures()).containsEntry("procDamage", 300.0);
  }
}
