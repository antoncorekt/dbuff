package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ItemEfficiencyDomain;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemsSectionTest {
  private final ItemEfficiencyRepo repo = mock(ItemEfficiencyRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final SectionSwitch sectionSwitch = new SectionSwitch(properties, List.of());
  private final AbilityLabels labels =
      name -> name.equals("item_magic_stick") ? "Magic Stick" : Prettify.ability(name);
  private final ItemsSection section = new ItemsSection(repo, heroNames, labels, properties);
  private final List<ItemEfficiencyDomain> rows = new ArrayList<>();
  private long nextId = 1;

  ItemsSectionTest() {
    when(repo.findByMatchId(1L)).thenReturn(rows);
    when(heroNames.bySlot(1L)).thenReturn(Map.of(0L, "Axe", 1L, "Lion", 2L, "Pudge", 128L, "Sven"));
  }

  private ItemEfficiencyDomain add(ItemEfficiency e) {
    ItemEfficiencyDomain row = ItemEfficiencyJson.toRow(1L, e, 1);
    row.setId(nextId++);
    rows.add(row);
    return row;
  }

  private ItemEfficiency stick(long slot, double bought, double ratio, double saves) {
    return new ItemEfficiency(
        slot,
        "magic_stick",
        "magic_stick",
        "magic-stick",
        bought,
        3,
        90,
        200L,
        ratio,
        null,
        Map.of("hp", 600.0, "mana", 300.0, "saves", saves),
        List.of(),
        List.of());
  }

  private ItemEfficiency midas(long slot, double bought, double ratio) {
    return new ItemEfficiency(
        slot,
        "hand_of_midas",
        "hand_of_midas",
        "hand-of-midas",
        bought,
        4,
        500,
        2200L,
        ratio,
        1500.0,
        Map.of(
            "gold",
            800.0,
            "xp",
            400.0,
            "idleSeconds",
            30.0,
            "farmingSeconds",
            600.0,
            "heroGold",
            8000.0),
        List.of(),
        List.of());
  }

  private ItemEfficiency farming(long slot, double bought) {
    return new ItemEfficiency(
        slot,
        "farming",
        "battle_fury",
        "farming-group",
        bought,
        20,
        900,
        4100L,
        1.8,
        1800.0,
        Map.of("netGoldPerMinute", 150.0),
        List.of(),
        List.of(
            new Detail(
                "BEFORE",
                0,
                null,
                Map.of("killsPerMinute", 3.0, "goldPerMinute", 200.0, "campsPerMinute", 0.5)),
            new Detail(
                "AFTER",
                0,
                null,
                Map.of("killsPerMinute", 5.0, "goldPerMinute", 350.0, "campsPerMinute", 1.0))));
  }

  private ReplayReportInput input(Long... focus) {
    return new ReplayReportInput(1L, null, Set.of(focus), Map.of(), 3);
  }

  @Test
  void isOffByDefaultUntilThePropertyIsTrue() {
    assertThat(section.name()).isEqualTo("items");
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(sectionSwitch.isEnabled(section)).isFalse();

    ReplayReportProperties.SectionToggle on = new ReplayReportProperties.SectionToggle();
    on.setEnabled(true);
    properties.setSections(Map.of("items", on));

    assertThat(sectionSwitch.isEnabled(section)).isTrue();
  }

  @Test
  void writesHeroItemTimeAndTheLoopVerdictForEachKindInTimeOrder() {
    add(farming(1, 1500));
    add(midas(2, 600, 0.9));
    add(stick(0, 200, 1.5, 2));

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(4);
    assertThat(lines.get(0)).isEqualTo("**Items**");
    assertThat(lines.get(1))
        .isEqualTo("Axe bought Magic Stick at 3:20: " + ItemWording.stick(stick(0, 200, 1.5, 2)));
    assertThat(lines.get(1))
        .contains("3 uses, restored 600 HP and 300 mana, 2 saves, worth 1.5x its cost");
    assertThat(lines.get(2))
        .startsWith("Pudge bought Hand Of Midas at 10:00: 4 uses, gave 800 gold (10% of his gold)")
        .contains("paid for itself at 25:00");
    assertThat(lines.get(3))
        .startsWith("Lion bought Battle Fury at 25:00: creeps 3.0 per min before and 5.0 after")
        .contains("creep gold 200 and 350 per min");
  }

  @Test
  void listsTheTrackedPlayersFirstWhenTheCapBites() {
    properties.setMaxItemLines(2);
    add(stick(128, 100, 5.0, 3));
    add(stick(2, 300, 3.0, 1));
    add(stick(0, 200, 1.0, 0));
    add(stick(1, 250, 1.05, 0));

    List<String> lines = section.lines(input(0L, 1L));

    assertThat(lines).hasSize(3);
    assertThat(lines.get(1)).startsWith("Axe bought");
    assertThat(lines.get(2)).startsWith("Lion bought");
  }

  @Test
  void withoutTrackedPlayersTheRowsFurthestFromTheirCostComeFirst() {
    properties.setMaxItemLines(2);
    add(stick(0, 100, 1.0, 0));
    add(stick(1, 200, 0.1, 0));
    add(stick(2, 300, 4.0, 2));

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(3);
    assertThat(lines.get(1)).startsWith("Lion bought");
    assertThat(lines.get(2)).startsWith("Pudge bought");
  }

  @Test
  void capsAtTheConfiguredLines() {
    for (int i = 0; i < 6; i++) {
      add(stick(i % 3, 100 + i, 2.0, 0));
    }

    assertThat(section.lines(input())).hasSize(1 + 4);
  }

  @Test
  void skipsUnknownKindsAndRowsWithoutTheirData() {
    ItemEfficiencyDomain unknown = add(stick(0, 100, 1.0, 0));
    unknown.setKind("new-kind");
    ItemEfficiencyDomain noFigures = add(stick(1, 100, 1.0, 0));
    noFigures.setFigures(null);
    ItemEfficiencyDomain noDetails = add(stick(2, 100, 1.0, 0));
    noDetails.setDetails("{}");
    ItemEfficiencyDomain noTime = add(stick(128, 100, 1.0, 0));
    noTime.setPurchaseSeconds(null);
    ItemEfficiencyDomain noGain = add(stick(0, 100, 1.0, 0));
    noGain.setGain(null);
    rows.add(null);
    add(stick(0, 400, 2.0, 1));

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(2);
    assertThat(lines.get(1)).startsWith("Axe bought Magic Stick at 6:40");
  }

  @Test
  void aMissingFigureInsideARowSkipsOnlyThatRow() {
    ItemEfficiencyDomain broken = add(midas(0, 100, 1.0));
    broken.setFigures("{\"gold\":null}");
    add(stick(1, 200, 1.0, 0));

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(2);
    assertThat(lines.get(1)).startsWith("Lion bought");
  }

  @Test
  void printsNothingWithoutRowsOrWithNullData() {
    assertThat(section.lines(input())).isEmpty();
    when(repo.findByMatchId(1L)).thenReturn(null);
    assertThat(section.lines(input())).isEmpty();
    when(repo.findByMatchId(1L)).thenReturn(List.of());
    assertThat(section.lines(new ReplayReportInput(1L, null, null, null, 3))).isEmpty();
  }

  @Test
  void saysABoughtButUnusedItemInPlainWordsAndNamesAHeroWithoutAKnownName() {
    when(heroNames.bySlot(1L)).thenReturn(Map.of());
    ItemEfficiency unused = stick(129, 500, 0.0, 0);
    add(
        new ItemEfficiency(
            129,
            unused.entryKey(),
            unused.itemKey(),
            unused.kind(),
            500.0,
            0,
            0,
            200L,
            0.0,
            null,
            Map.of(),
            List.of(),
            List.of()));

    assertThat(section.lines(input()).get(1))
        .isEqualTo("Dire player 2 bought Magic Stick at 8:20: bought, never used");
  }

  @Test
  void cutsALongLineAtAWordBoundary() {
    properties.setMaxBehaviourLineLength(60);
    add(farming(0, 600));

    String line = section.lines(input()).get(1);

    assertThat(line).hasSizeLessThanOrEqualTo(60).endsWith("...");
  }

  @Test
  void validatesTheLineCap() {
    for (int bad : new int[] {0, 11}) {
      properties.setMaxItemLines(bad);
      assertThatThrownBy(properties::validate).hasMessageContaining("max-item-lines");
    }
    properties.setMaxItemLines(10);
    properties.validate();
  }
}
