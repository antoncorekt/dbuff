package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemKindWordingsTest {
  private final ItemKindWordings wordings = ItemKindWordings.standard();

  @Test
  void aRowIsWordedByItsMainAspect() {
    assertThat(wordings.find("charge-restore")).containsInstanceOf(StickKindWording.class);
    assertThat(wordings.find("midas-uses")).containsInstanceOf(MidasKindWording.class);
    assertThat(wordings.find("farm-rate")).containsInstanceOf(FarmingKindWording.class);
    assertThat(wordings.find("bottle-runes")).containsInstanceOf(BottleKindWording.class);
    assertThat(wordings.find("cooldown")).containsInstanceOf(CooldownKindWording.class);
    assertThat(wordings.find("vision")).containsInstanceOf(VisionKindWording.class);
    assertThat(wordings.find("team-restore")).containsInstanceOf(TeamRestoreKindWording.class);
    assertThat(wordings.find("self-harm")).containsInstanceOf(SoulRingKindWording.class);
    assertThat(wordings.find("target-use")).containsInstanceOf(TargetUseKindWording.class);
  }

  @Test
  void theBottleLineNamesSipsHealthManaAndRunes() {
    ItemEfficiency row =
        new ItemEfficiency(
            0,
            "bottle",
            "bottle",
            "bottle-runes",
            60.0,
            31,
            1000,
            675L,
            1.5,
            null,
            Map.of("hp", 2400.0, "mana", 1600.0, "runes", 7.0, "bottled", 3.0),
            List.of(),
            List.of());

    assertThat(new BottleKindWording().shortVerdict(row, "Axe"))
        .isEqualTo("Axe: 31 sips, 2.4k HP, 1.6k mana, 7 runes (3 bottled), 1.5x its cost");
  }

  @Test
  void rowsStoredBeforeTheAspectsKeepTheWordingOfTheirKind() {
    assertThat(wordings.find("magic-stick")).containsInstanceOf(StickKindWording.class);
    assertThat(wordings.find("hand-of-midas")).containsInstanceOf(MidasKindWording.class);
    assertThat(wordings.find("farming-group")).containsInstanceOf(FarmingKindWording.class);
  }

  @Test
  void anUnknownOrMissingKindHasNoWording() {
    assertThat(wordings.find("new-kind")).isEmpty();
    assertThat(wordings.find(null)).isEmpty();
  }

  private static ItemEfficiency cooldown(Double ratio, double casts, double kills) {
    return new ItemEfficiency(
        0,
        "refresher",
        "refresher",
        "cooldown",
        60.0,
        1,
        1000,
        1000L,
        ratio,
        null,
        Map.of("extraCasts", casts, "killsHelped", kills),
        List.of(),
        List.of());
  }

  @Test
  void aZeroEffectIsNeverWrittenAsAZeroRatio() {
    ItemEfficiency none = cooldown(0.0, 0, 0);
    ItemEfficiency negativeZero = cooldown(-0.01, 0, 0);

    assertThat(new CooldownKindWording().shortVerdict(none, "Axe"))
        .isEqualTo("Axe gave no extra casts (no measurable effect)");
    assertThat(new CooldownKindWording().longVerdict(negativeZero))
        .isEqualTo("no extra casts, no measurable effect")
        .doesNotContain("0.0x");
    assertThat(ItemWording.signedRatio(-0.01)).doesNotContain("0.0");
    assertThat(ItemWording.worthClause(0.0)).isEqualTo(", no measurable effect");
    assertThat(ItemWording.worthClause(null)).isEmpty();
    assertThat(ItemWording.worthClause(1.5)).isEqualTo(", worth 1.5x its cost");
  }

  @Test
  void aCooldownRowKeepsItsCastsAndKills() {
    assertThat(new CooldownKindWording().shortVerdict(cooldown(1.5, 3, 1), "Axe"))
        .isEqualTo("Axe gave 3 extra casts, helped 1 kill (1.5x its cost)");
  }

  @Test
  void aDurationIsMinutesAndSecondsAndAGameTimeStaysAClock() {
    assertThat(ItemWording.duration(325.0)).isEqualTo("5 min 25 s");
    assertThat(ItemWording.duration(40.0)).isEqualTo("40 s");
    assertThat(ItemWording.duration(null)).isEqualTo("?");
    assertThat(ItemWording.clock(325.0)).isEqualTo("5:25");
  }

  @Test
  void savingCostUsesTheDuration() {
    ItemEfficiency row =
        new ItemEfficiency(
            0,
            "x",
            "x",
            "farm-rate",
            60.0,
            1,
            1000,
            1000L,
            1.0,
            null,
            Map.of(),
            List.of(),
            List.of(
                new ItemEfficiency.Detail(
                    SavingCostLine.LABEL,
                    0,
                    null,
                    Map.of(
                        "savingSeconds", 325.0, "savingPeakGold", 3400.0, "savingDeaths", 0.0))));

    assertThat(SavingCostLine.of(row)).contains("saved 3.4k over 5 min 25 s without dying");
  }

  private static ItemEfficiency zeroWorth(String aspect, Map<String, Double> figures) {
    return new ItemEfficiency(
        0, "item", "item", aspect, 60.0, 2, 1000, 1000L, 0.0, null, figures, List.of(), List.of());
  }

  @Test
  void everyKindSaysNoMeasurableEffectForAZeroRatio() {
    Map<String, Double> all =
        Map.ofEntries(
            Map.entry("hp", 100.0),
            Map.entry("mana", 50.0),
            Map.entry("runes", 1.0),
            Map.entry("bottled", 1.0),
            Map.entry("selfDeaths", 0.0),
            Map.entry("saves", 1.0),
            Map.entry("allies", 2.0),
            Map.entry("gold", 10.0),
            Map.entry("xp", 10.0),
            Map.entry("idleSeconds", 5.0),
            Map.entry("farmingSeconds", 60.0),
            Map.entry("netGoldPerMinute", 10.0),
            Map.entry("extraCasts", 1.0),
            Map.entry("killsHelped", 0.0));
    for (String aspect :
        List.of(
            "bottle-runes",
            "self-harm",
            "team-restore",
            "target-use",
            "charge-restore",
            "midas-uses",
            "farm-rate",
            "cooldown")) {
      ItemEfficiency row = zeroWorth(aspect, all);
      ItemKindWording wording = wordings.find(aspect).orElseThrow();
      assertThat(wording.longVerdict(row)).as(aspect).doesNotContain("0.0x");
      assertThat(wording.shortVerdict(row, "Axe")).as(aspect).doesNotContain("0.0x");
      assertThat(wording.longVerdict(row)).as(aspect).contains("no measurable effect");
    }
  }
}
