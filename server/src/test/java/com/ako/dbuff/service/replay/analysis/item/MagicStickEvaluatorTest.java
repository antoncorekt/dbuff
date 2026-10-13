package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.change;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.charges;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.death;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.hero;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.hit;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.sampleOf;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.use;
import static org.assertj.core.api.Assertions.assertThat;

class MagicStickEvaluatorTest {
  private static final String STICK = "item_magic_stick";
  private static final String WAND = "item_magic_wand";
  private static final ItemEfficiencyEntry ENTRY =
      ItemTestData.entry(
          "magic_stick", MagicStickEvaluator.KIND, List.of("magic_stick", "magic_wand"), Map.of());
  private static final ItemKits KITS =
      ItemTestData.kits(Map.of("magic_stick", 200L, "magic_wand", 460L));

  private final MagicStickEvaluator evaluator = new MagicStickEvaluator();

  private ItemEfficiency evaluate(HeroInputs hero) {
    List<ItemEfficiency> rows =
        evaluator.evaluate(new EntryContext(ENTRY, KITS, 600, List.of(hero)));
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  /** At 120 s the hero has 30 percent of 1000 and a hit left him 300; at 180 s 97 percent. */
  private static ReplayPlayerTrackDomain track() {
    ReplayPlayerTrackDomain track = ItemTestData.track(150);
    track.getHpPercent()[sampleOf(120)] = 30;
    track.getHpPercent()[sampleOf(180)] = 97;
    track.getManaPercent()[sampleOf(120)] = 50;
    track.getMana()[sampleOf(120)] = 200;
    return track;
  }

  private static List<com.ako.dbuff.dao.model.ReplayStateChangeDomain> sticks() {
    return List.of(
        change(-60, StateChangeKind.ITEM_ADDED, STICK),
        charges(100, STICK, 10),
        charges(125, STICK, 0),
        charges(170, STICK, 10),
        charges(190, STICK, 0));
  }

  @Test
  void aUseAtLowHealthDuringHeavyDamageThatTheHeroSurvivesIsASave() {
    List<CombatEvent> events =
        List.of(hit(120.2, 450, 300), use(121.0, STICK, ""), hit(122.0, 100, 250));

    ItemEfficiency result = evaluate(hero(sticks(), events, track(), List.of(), List.of()));

    assertThat(result.count()).isEqualTo(1);
    assertThat(result.rows().getFirst().figures())
        .containsEntry("charges", 10.0)
        .containsEntry("hp", 150.0)
        .containsEntry("mana", 150.0)
        .containsEntry("hpBefore", 300.0)
        .containsEntry("maxHp", 1000.0)
        .containsEntry("saved", 1.0)
        .containsEntry("diedSoon", 0.0)
        .containsEntry("laning", 1.0);
    assertThat(result.figures()).containsEntry("saves", 1.0).containsEntry("hp", 150.0);
    assertThat(result.cost()).isEqualTo(200);
    assertThat(result.purchaseSeconds()).isEqualTo(-60.0);
    assertThat(result.itemKey()).isEqualTo("magic_stick");
    assertThat(result.flags()).isEmpty();
    assertThat(result.gain()).isEqualTo(150 * 0.3 + 150 * 0.3 + 300);
    assertThat(result.ratio()).isEqualTo(result.gain() / 200);
  }

  @Test
  void aDeathSoonAfterTheUseIsNotASave() {
    List<CombatEvent> events =
        List.of(hit(120.2, 450, 300), use(121.0, STICK, ""), hit(122.0, 300, 0), death(122.0));

    ItemEfficiency result = evaluate(hero(sticks(), events, track(), List.of(), List.of()));

    assertThat(result.rows().getFirst().figures())
        .containsEntry("saved", 0.0)
        .containsEntry("diedSoon", 1.0);
    assertThat(result.figures()).containsEntry("saves", 0.0).containsEntry("diedSoon", 1.0);
  }

  @Test
  void whatTheHeroWasMissingCapsTheHealthRestored() {
    List<CombatEvent> events =
        List.of(hit(120.2, 450, 300), hit(180.2, 30, 970), use(181.0, STICK, ""));

    ItemEfficiency result = evaluate(hero(sticks(), events, track(), List.of(), List.of()));

    assertThat(result.rows().getFirst().figures())
        .containsEntry("charges", 10.0)
        .containsEntry("hp", 30.0)
        .containsEntry("saved", 0.0);
  }

  @Test
  void withoutATrackTheChargesAreCountedUncappedAndSaysSo() {
    List<CombatEvent> events = List.of(use(121.0, STICK, ""));

    ItemEfficiency result = evaluate(hero(sticks(), events, null, List.of(), List.of()));

    assertThat(result.figures()).containsEntry("hp", 150.0).containsEntry("mana", 150.0);
    assertThat(result.flags())
        .containsExactly(MagicStickEvaluator.HP_UNCAPPED, MagicStickEvaluator.MANA_UNCAPPED);
  }

  @Test
  void theStickBecomingAWandIsOneChainCostedAsTheWand() {
    var changes =
        List.of(
            change(-60, StateChangeKind.ITEM_ADDED, STICK),
            charges(100, STICK, 4),
            change(150, StateChangeKind.ITEM_REMOVED, STICK),
            change(150, StateChangeKind.ITEM_ADDED, WAND),
            charges(150, WAND, 6),
            charges(300, WAND, 12));
    List<CombatEvent> events = List.of(use(110, STICK, ""), use(310, WAND, ""));

    ItemEfficiency result = evaluate(hero(changes, events, null, List.of(), List.of()));

    assertThat(result.count()).isEqualTo(2);
    assertThat(result.cost()).isEqualTo(460);
    assertThat(result.rows())
        .extracting(r -> r.figures().get("charges"))
        .containsExactly(4.0, 12.0);
    assertThat(result.rows().get(1).figures()).containsEntry("laning", 1.0);
  }

  @Test
  void aHeroWithoutTheItemGetsNoRowAndAUseWithNoChargesSeenIsFlagged() {
    HeroInputs without = hero(List.of(), List.of(), null, List.of(), List.of());
    assertThat(evaluator.evaluate(new EntryContext(ENTRY, KITS, 600, List.of(without)))).isEmpty();

    var changes = List.of(change(-60, StateChangeKind.ITEM_ADDED, STICK));
    ItemEfficiency result =
        evaluate(hero(changes, List.of(use(-9, STICK, "")), null, List.of(), List.of()));

    assertThat(result.flags()).contains(MagicStickEvaluator.NO_CHARGES_SEEN);
    assertThat(result.figures()).containsEntry("hp", 0.0);
  }

  @Test
  void aParameterFromTheEntryChangesTheRule() {
    ItemEfficiencyEntry stricter =
        ItemTestData.entry(
            "magic_stick",
            MagicStickEvaluator.KIND,
            List.of("magic_stick", "magic_wand"),
            Map.of("lowHpFraction", 0.1, "saveGold", 0.0));
    List<CombatEvent> events = List.of(hit(120.2, 450, 300), use(121.0, STICK, ""));

    ItemEfficiency result =
        evaluator
            .evaluate(
                new EntryContext(
                    stricter,
                    KITS,
                    600,
                    List.of(hero(sticks(), events, track(), List.of(), List.of()))))
            .getFirst();

    assertThat(result.figures()).containsEntry("saves", 0.0);
  }
}
