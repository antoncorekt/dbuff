package com.ako.dbuff.service.replay.report;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.ItemEfficiencyDomain;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyFile;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyJson;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
  private final ItemsSection section =
      new ItemsSection(
          repo,
          heroNames,
          ItemKindWordings.standard(),
          new ItemEfficiencyFile("replay/items.yaml"),
          labels,
          properties);
  private final List<ItemEfficiencyDomain> rows = new ArrayList<>();
  private long nextId = 1;

  ItemsSectionTest() {
    when(repo.findByMatchId(1L)).thenReturn(rows);
    when(heroNames.iconBySlot(1L))
        .thenReturn(Map.of(0L, "Axe", 1L, "Lion", 2L, "Pudge", 128L, "Sven"));
  }

  private ItemEfficiencyDomain add(ItemEfficiency e) {
    ItemEfficiencyDomain row = ItemEfficiencyJson.toRow(1L, e, 1);
    row.setId(nextId++);
    rows.add(row);
    return row;
  }

  private ItemEfficiency stick(long slot, double bought, Double ratio, double saves) {
    return new ItemEfficiency(
        slot,
        "magic_stick",
        "magic_stick",
        "charge-restore",
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

  private ItemEfficiency midas(long slot, double bought, Double ratio) {
    return new ItemEfficiency(
        slot,
        "hand_of_midas",
        "hand_of_midas",
        "midas-uses",
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
    return farming(slot, bought, 1.8, 1800.0, 200.0, 350.0, Map.of("netGoldPerMinute", 150.0));
  }

  private ItemEfficiency farming(
      long slot,
      double bought,
      Double ratio,
      Double payback,
      double goldBefore,
      double goldAfter,
      Map<String, Double> figures) {
    return new ItemEfficiency(
        slot,
        "farming_items",
        "battle_fury",
        "farm-rate",
        bought,
        20,
        900,
        2700L,
        ratio,
        payback,
        figures,
        List.of(),
        List.of(
            new Detail(
                "BEFORE",
                0,
                null,
                Map.of("killsPerMinute", 3.0, "goldPerMinute", goldBefore, "campsPerMinute", 0.5)),
            new Detail(
                "AFTER",
                0,
                null,
                Map.of("killsPerMinute", 5.0, "goldPerMinute", goldAfter, "campsPerMinute", 1.0))));
  }

  private ReplayReportInput input(Long... focus) {
    return new ReplayReportInput(1L, null, Set.of(focus), Map.of(), 3);
  }

  private List<String> rowLines(ReplayReportInput input) {
    return section.lines(input).stream().filter(line -> !line.startsWith("**")).toList();
  }

  private ReplayReportInput filtered(List<String> names) {
    return input().withItemFilter(names);
  }

  private ReplayReportInput channelChoosing(String itemCategories) {
    DbufInstanceConfigDomain channel = new DbufInstanceConfigDomain();
    InstanceSettings settings = new InstanceSettings();
    settings.setItemCategories(itemCategories);
    channel.setSettings(settings);
    return input().withInstance(channel);
  }

  private ListAppender<ILoggingEvent> warnings() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(ItemsSection.class)).addAppender(appender);
    return appender;
  }

  @Test
  void aCategoryFilterKeepsOnlyThatCategoryUnderItsHeading() {
    add(farming(1, 1500));
    add(stick(0, 200, 1.5, 2));

    assertThat(section.lines(filtered(List.of("sustain"))))
        .hasSize(3)
        .containsSubsequence("**Items**", "**Sustain and mana**")
        .noneMatch(line -> line.contains("Battle Fury"));
  }

  @Test
  void anEntryKeyFilterKeepsOnlyThatEntry() {
    add(midas(2, 600, 0.9));
    add(farming(1, 1500));

    assertThat(section.lines(filtered(List.of("hand_of_midas"))))
        .hasSize(3)
        .anyMatch(line -> line.contains("Hand Of Midas"))
        .noneMatch(line -> line.contains("Battle Fury"));
  }

  @Test
  void aFilterThatMatchesNothingPrintsNothing() {
    add(stick(0, 200, 1.5, 2));

    assertThat(section.lines(filtered(List.of("farming")))).isEmpty();
    assertThat(section.view(filtered(List.of("farming")))).isEmpty();
  }

  @Test
  void theChannelsChoiceAppliesWhenTheRequestNamesNothing() {
    add(farming(1, 1500));
    add(stick(0, 200, 1.5, 2));

    assertThat(section.lines(channelChoosing("farming")))
        .noneMatch(line -> line.contains("Magic Stick"))
        .anyMatch(line -> line.contains("Battle Fury"));
    assertThat(section.lines(channelChoosing(null))).hasSize(5);
  }

  @Test
  void theRequestOverridesTheChannelsChoice() {
    add(farming(1, 1500));
    add(stick(0, 200, 1.5, 2));
    ReplayReportInput input = channelChoosing("farming").withItemFilter(List.of("sustain"));

    assertThat(section.lines(input))
        .anyMatch(line -> line.contains("Magic Stick"))
        .noneMatch(line -> line.contains("Battle Fury"));
  }

  @Test
  void theLineCapAppliesToEachCategory() {
    properties.setMaxItemLines(1);
    add(stick(0, 200, 1.5, 2));
    add(stick(1, 300, 1.5, 2));
    add(midas(2, 600, 0.9));
    add(farming(1, 1500));

    assertThat(section.lines(input()))
        .hasSize(1 + 2 + 2)
        .containsSubsequence("**Items**", "**Farming**", "**Sustain and mana**");
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
  void saysEachKindInOneShortVerdictFirstSentenceInTimeOrder() {
    add(farming(1, 1500));
    add(midas(2, 600, 0.9));
    add(stick(0, 200, 1.5, 2));

    List<String> lines = section.lines(input());

    assertThat(lines)
        .containsExactly(
            "**Items**",
            "**Farming**",
            "Pudge's Hand Of Midas (bought at 10:00) paid for itself at 25:00 and earned 800 gold"
                + " (0.9x its cost)",
            "Lion's Battle Fury (bought at 25:00) paid back at 30:00: his creep gold rose from 200"
                + " to 350 per minute after buying it; camps per minute rose from 0.5 to 1.0",
            "**Sustain and mana**",
            "Axe's Magic Stick (bought at 3:20): 3 uses, 2 saves, 1.5x its cost");
    assertThat(lines).allSatisfy(line -> assertThat(line).hasSizeLessThan(200));
  }

  @Test
  void saysAFarmingItemThatDidNotPayBackByWhatHappenedToTheCreepGold() {
    add(farming(1, 900, 0.0, null, 133, 97, Map.of("netGoldPerMinute", -37.0)));

    assertThat(rowLines(input()).get(0))
        .isEqualTo(
            "Lion's Battle Fury (bought at 15:00) has not paid back: his creep gold fell from 133"
                + " to 97 per minute after buying it; camps per minute rose from 0.5 to 1.0");
  }

  @Test
  void keepsTheDamagePartOfAFarmingItemThatHasOne() {
    add(
        farming(
            1,
            900,
            0.6,
            null,
            133,
            97,
            Map.of("netGoldPerMinute", -37.0, "damageValue", 800.0, "creepGain", -900.0)));

    assertThat(rowLines(input()).get(0)).endsWith(", its damage was worth 0.3x its cost");
  }

  @Test
  void leavesTheWorthOutWhenTheCostIsNotKnown() {
    add(stick(0, 200, null, 0));
    add(midas(2, 600, null));
    add(farming(1, 900, null, null, 133, 97, Map.of()));

    List<String> lines = rowLines(input());

    assertThat(lines).hasSize(3);
    assertThat(lines.get(0)).doesNotContain("worth", "-").endsWith("and earned 800 gold");
    assertThat(lines.get(1))
        .startsWith("Lion's Battle Fury (bought at 15:00): his creep gold fell");
    assertThat(lines.get(2)).isEqualTo("Axe's Magic Stick (bought at 3:20): 3 uses, no saves");
    assertThat(String.join("\n", lines)).doesNotContain("its cost");
  }

  @Test
  void saysAPurchaseBeforeTheHornBoughtAtTheStartAndListsItFirst() {
    add(stick(0, 200, 1.5, 0));
    add(stick(1, -85, 1.5, 1));

    List<String> lines = rowLines(input());

    assertThat(lines.get(0)).startsWith("Lion's Magic Stick (bought at the start):");
    assertThat(lines.get(1)).startsWith("Axe's Magic Stick (bought at 3:20):");
    assertThat(String.join("\n", lines)).doesNotContain("-1:25");
  }

  @Test
  void listsTheTrackedPlayersFirstWhenTheCapBites() {
    properties.setMaxItemLines(2);
    add(stick(128, 100, 5.0, 3));
    add(stick(2, 300, 3.0, 1));
    add(stick(0, 200, 1.0, 0));
    add(stick(1, 250, 1.05, 0));

    List<String> lines = rowLines(input(0L, 1L));

    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).startsWith("Axe's");
    assertThat(lines.get(1)).startsWith("Lion's");
  }

  @Test
  void withoutTrackedPlayersTheRowsFurthestFromTheirCostComeFirst() {
    properties.setMaxItemLines(2);
    add(stick(0, 100, 1.0, 0));
    add(stick(1, 200, 0.1, 0));
    add(stick(2, 300, 4.0, 2));

    List<String> lines = rowLines(input());

    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).startsWith("Lion's");
    assertThat(lines.get(1)).startsWith("Pudge's");
  }

  @Test
  void capsAtTheConfiguredLines() {
    for (int i = 0; i < 6; i++) {
      add(stick(i % 3, 100 + i, 2.0, 0));
    }

    assertThat(rowLines(input())).hasSize(4);
  }

  @Test
  void aTieIsBrokenByTheRowIdWhateverTheOrderTheRepositoryReturns() {
    properties.setMaxItemLines(1);
    ItemEfficiencyDomain first = add(stick(0, 200, 1.5, 0));
    first.setEntryKey(null);
    first.setItemKey("magic_stick");
    ItemEfficiencyDomain second = add(stick(0, 200, 1.5, 0));
    second.setEntryKey(null);
    second.setItemKey("magic_wand");
    java.util.Collections.reverse(rows);

    assertThat(rowLines(input()).get(0)).contains("Magic Stick");
  }

  @Test
  void skipsUnknownKindsQuietlyAndRowsWithoutTheirFiguresWithOneWarnEach() {
    ListAppender<ILoggingEvent> log = warnings();
    ItemEfficiencyDomain unknown = add(stick(0, 100, 1.0, 0));
    unknown.setKind("new-kind");
    ItemEfficiencyDomain noFigures = add(stick(1, 100, 1.0, 0));
    noFigures.setFigures(null);
    ItemEfficiencyDomain noRates = add(farming(2, 100));
    noRates.setDetails("{}");
    ItemEfficiencyDomain noTime = add(stick(128, 100, 1.0, 0));
    noTime.setPurchaseSeconds(null);
    ItemEfficiencyDomain noGain = add(stick(0, 100, 1.0, 0));
    noGain.setGain(null);
    ItemEfficiencyDomain noSlot = add(stick(0, 100, 1.0, 0));
    noSlot.setPlayerSlot(null);
    rows.add(null);
    add(stick(0, 400, 2.0, 1));

    List<String> lines = rowLines(input());

    assertThat(lines).hasSize(1);
    assertThat(lines.get(0)).startsWith("Axe's Magic Stick (bought at 6:40)");
    assertThat(log.list)
        .hasSize(5)
        .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN))
        .allSatisfy(event -> assertThat(event.getFormattedMessage()).contains("match 1"))
        .allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    assertThat(log.list.get(0).getFormattedMessage()).contains("magic_stick");
  }

  @Test
  void aStickRowWithoutItsDetailsIsStillSaid() {
    add(stick(0, 200, 1.5, 1)).setDetails(null);

    assertThat(rowLines(input()).get(0))
        .isEqualTo("Axe's Magic Stick (bought at 3:20): 3 uses, 1 save, 1.5x its cost");
  }

  @Test
  void aMissingFigureInsideARowSkipsOnlyThatRow() {
    ItemEfficiencyDomain broken = add(midas(0, 100, 1.0));
    broken.setFigures("{\"gold\":null}");
    add(stick(1, 200, 1.0, 0));

    List<String> lines = rowLines(input());

    assertThat(lines).hasSize(1);
    assertThat(lines.get(0)).startsWith("Lion's");
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
    when(heroNames.iconBySlot(1L)).thenReturn(Map.of());
    add(
        new ItemEfficiency(
            129,
            "magic_stick",
            "magic_stick",
            "charge-restore",
            500.0,
            0,
            0,
            200L,
            0.0,
            null,
            Map.of(),
            List.of(),
            List.of()));

    assertThat(rowLines(input()).get(0))
        .isEqualTo("Dire player 2's Magic Stick (bought at 8:20) was never used");
  }

  @Test
  void cutsALongLineAtAWordBoundary() {
    properties.setMaxBehaviourLineLength(60);
    add(farming(0, 600));

    String line = rowLines(input()).get(0);

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

  @Test
  void view_countsTheItemsThatPaidBackAndKeepsPlainTextAsItWas() {
    add(farming(1, 1500));
    add(midas(2, 600, 0.9));
    add(stick(0, 200, 1.5, 2));

    SectionView view = section.view(input()).orElseThrow();

    ViewRendering.assertWithinLimits(view);
    assertThat(view.title()).isEqualTo("Items");
    assertThat(view.verdict()).isEqualTo("Items shown: 2 of 3 paid back their cost");
    assertThat(view.accent()).isEqualTo(Accent.WIN);
    assertThat(String.join("\n", section.lines(input())))
        .isEqualTo(
            "**Items**\n"
                + "**Farming**\n"
                + "Pudge's Hand Of Midas (bought at 10:00) paid for itself at 25:00 and earned 800"
                + " gold (0.9x its cost)\n"
                + "Lion's Battle Fury (bought at 25:00) paid back at 30:00: his creep gold rose"
                + " from 200 to 350 per minute after buying it; camps per minute rose from 0.5 to"
                + " 1.0\n"
                + "**Sustain and mana**\n"
                + "Axe's Magic Stick (bought at 3:20): 3 uses, 2 saves, 1.5x its cost");
    assertThat(new PlainViewRenderer().lines(view)).isEqualTo(section.lines(input()));
  }

  @Test
  void view_looksAtTheTrackedPlayersItemsFirst() {
    add(farming(1, 1500));
    add(midas(2, 600, 0.9));

    SectionView view = section.view(input(2L)).orElseThrow();

    assertThat(view.verdict()).isEqualTo("Your players' items shown: 0 of 1 paid back their cost");
    assertThat(view.accent()).isEqualTo(Accent.LOSS);
  }

  @Test
  void view_isEmptyWithoutUsableRows() {
    assertThat(section.view(input())).isEmpty();
  }

  @Test
  void view_countsOnlyTheItemsTheCardShows() {
    properties.setMaxItemLines(1);
    add(farming(1, 1500));
    add(midas(2, 600, 0.9));
    add(stick(0, 200, 1.5, 2));

    SectionView view = section.view(input()).orElseThrow();

    assertThat(view.bullets()).hasSize(4);
    assertThat(view.verdict()).matches("Items shown: [012] of 2 paid back their cost");
  }
}
