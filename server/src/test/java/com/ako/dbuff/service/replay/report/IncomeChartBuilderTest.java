package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.BuybackEventDomain;
import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.DeathEventDomain;
import com.ako.dbuff.dao.model.ItemPurchaseEventDomain;
import com.ako.dbuff.dao.model.PlayerOutlierDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.DeathEventRepo;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.dao.repo.ItemPurchaseEventRepo;
import com.ako.dbuff.dao.repo.PlayerOutlierRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyJson;
import com.ako.dbuff.service.replay.analysis.item.ItemKits;
import com.ako.dbuff.service.replay.report.IncomeChartBuilder.IncomeChart;
import com.ako.dbuff.service.replay.report.chart.ChartRenderer;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import com.ako.dbuff.service.replay.report.chart.ChartSpec.PinShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncomeChartBuilderTest {
  private static final long MATCH = 1L;
  private static final int LINE = 400;

  private final PlayerTimelineRepo timelines = mock(PlayerTimelineRepo.class);
  private final DeathEventRepo deathEvents = mock(DeathEventRepo.class);
  private final BuybackEventRepo buybacks = mock(BuybackEventRepo.class);
  private final ItemEfficiencyRepo efficiency = mock(ItemEfficiencyRepo.class);
  private final ItemPurchaseEventRepo purchases = mock(ItemPurchaseEventRepo.class);
  private final DeathAnalysisRepo deathAnalysis = mock(DeathAnalysisRepo.class);
  private final PlayerOutlierRepo outliers = mock(PlayerOutlierRepo.class);
  private final TeamRotationRepo rotations = mock(TeamRotationRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final ItemKits kits = mock(ItemKits.class);
  private final IncomeChartBuilder builder =
      new IncomeChartBuilder(
          timelines,
          heroNames,
          new IncomeReasons(deathAnalysis, outliers, rotations),
          new IncomePins(
              deathEvents,
              buybacks,
              efficiency,
              purchases,
              kits,
              key -> key.replace("item_", "").replace('_', ' '),
              ItemKindWordings.standard()));

  IncomeChartBuilderTest() {
    when(heroNames.bySlot(MATCH)).thenReturn(Map.of(0L, "Axe", 1L, "Lion", 128L, "Puck"));
    when(timelines.findByMatchId(MATCH)).thenReturn(List.of());
    when(deathEvents.findByMatchId(MATCH)).thenReturn(List.of());
    when(buybacks.findByMatchId(MATCH)).thenReturn(List.of());
    when(efficiency.findByMatchId(MATCH)).thenReturn(List.of());
    when(purchases.findByMatchId(MATCH)).thenReturn(List.of());
    when(deathAnalysis.findByMatchId(MATCH)).thenReturn(List.of());
    when(outliers.findByMatchId(MATCH)).thenReturn(List.of());
    when(rotations.findByMatchId(MATCH)).thenReturn(List.of());
    when(kits.cost(org.mockito.ArgumentMatchers.anyString())).thenReturn(OptionalLong.empty());
  }

  private static List<PlayerTimelineDomain> gold(long slot, long... gainPerMinute) {
    List<PlayerTimelineDomain> rows = new ArrayList<>();
    long total = 0;
    rows.add(
        PlayerTimelineDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .gameMinute(0)
            .gold(0L)
            .build());
    for (int m = 0; m < gainPerMinute.length; m++) {
      total += gainPerMinute[m];
      rows.add(
          PlayerTimelineDomain.builder()
              .matchId(MATCH)
              .playerSlot(slot)
              .gameMinute(m + 1)
              .gold(total)
              .build());
    }
    return rows;
  }

  private static long[] steady(int minutes, long gain) {
    long[] gains = new long[minutes];
    java.util.Arrays.fill(gains, gain);
    return gains;
  }

  private static long[] withFall() {
    long[] gains = steady(14, 800);
    for (int m = 7; m < 11; m++) {
      gains[m] = 200;
    }
    return gains;
  }

  private void timeline(List<PlayerTimelineDomain>... slots) {
    List<PlayerTimelineDomain> all = new ArrayList<>();
    for (List<PlayerTimelineDomain> rows : slots) {
      all.addAll(rows);
    }
    when(timelines.findByMatchId(MATCH)).thenReturn(all);
  }

  @Test
  @SuppressWarnings("unchecked")
  void isEmptyWithoutTimelinesOrWithOnlyAFewMinutes() {
    assertThat(builder.build(MATCH, Set.of(0L), 2, 3, LINE)).isEmpty();

    timeline(gold(0, 500, 500));

    assertThat(builder.build(MATCH, Set.of(0L), 2, 3, LINE)).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void drawsTrackedHeroesFirstAndAtMostTheCap() {
    timeline(gold(0, steady(10, 500)), gold(1, steady(10, 900)), gold(128, steady(10, 2000)));

    IncomeChart tracked = builder.build(MATCH, Set.of(0L, 1L), 1, 3, LINE).orElseThrow();
    IncomeChart nobody = builder.build(MATCH, Set.of(), 2, 3, LINE).orElseThrow();

    assertThat(tracked.spec().panels()).hasSize(1);
    assertThat(tracked.spec().panels().getFirst().label()).startsWith("Lion");
    assertThat(nobody.spec().panels())
        .extracting(ChartSpec.Panel::label)
        .first()
        .asString()
        .startsWith("Puck");
    assertThat(nobody.spec().panels()).hasSize(2);
    assertThat(tracked.verdict()).isEqualTo("No hero's income fell sharply.");
    assertThat(tracked.bullets()).containsExactly("1 more tracked hero is not shown.");
  }

  @Test
  @SuppressWarnings("unchecked")
  void drawsEveryTrackedHeroTwoToAPictureAndNamesTheOnesPastTheImageCap() {
    timeline(
        gold(0, steady(10, 500)),
        gold(1, steady(10, 600)),
        gold(2, steady(10, 700)),
        gold(3, steady(10, 800)),
        gold(4, steady(10, 900)));
    Set<Long> all = Set.of(0L, 1L, 2L, 3L, 4L);

    List<IncomeChart> five = builder.pages(MATCH, all, 2, 5, 3, LINE);
    List<IncomeChart> capped = builder.pages(MATCH, all, 2, 2, 3, LINE);

    assertThat(five).extracting(c -> c.spec().panels().size()).containsExactly(2, 2, 1);
    assertThat(five).allSatisfy(c -> assertThat(c.bullets()).isEmpty());
    assertThat(capped).hasSize(2);
    assertThat(capped.getFirst().bullets()).isEmpty();
    assertThat(capped.getLast().bullets()).containsExactly("1 more tracked hero is not shown.");
  }

  @Test
  @SuppressWarnings("unchecked")
  void withNobodyTrackedTheRichestHeroesFillOnlyOnePicture() {
    timeline(gold(0, steady(10, 500)), gold(1, steady(10, 600)), gold(2, steady(10, 700)));

    assertThat(builder.pages(MATCH, Set.of(), 2, 5, 3, LINE)).hasSize(1);
  }

  @Test
  @SuppressWarnings("unchecked")
  void trackedHeroesWithoutIncomeData_saysThatTheRichestHeroesAreShown() {
    timeline(gold(0, 500, 500), gold(128, steady(10, 2000)));

    IncomeChart chart = builder.build(MATCH, Set.of(0L), 2, 3, LINE).orElseThrow();
    IncomeChart nobodyTracked = builder.build(MATCH, Set.of(), 2, 3, LINE).orElseThrow();

    assertThat(chart.verdict())
        .startsWith("None of your players have income data; showing the richest heroes. ");
    assertThat(nobodyTracked.verdict())
        .startsWith("No players are tracked; showing the richest heroes. ");
    assertThat(chart.spec().panels()).hasSize(1);
  }

  @Test
  @SuppressWarnings("unchecked")
  void aSmallLineLengthCutsTheVerdictButNeverTheFallbackNote() {
    timeline(gold(0, 500, 500), gold(128, steady(10, 2000)));

    IncomeChart chart = builder.build(MATCH, Set.of(0L), 2, 3, 12).orElseThrow();

    assertThat(chart.verdict())
        .isEqualTo("None of your players have income data; showing the richest heroes. No...");
  }

  @Test
  @SuppressWarnings("unchecked")
  void aFallIsShadedAndExplainedByTheDeathInsideIt() {
    timeline(gold(0, withFall()));
    when(deathAnalysis.findByMatchId(MATCH))
        .thenReturn(
            List.of(
                DeathAnalysisDomain.builder()
                    .playerSlot(0L)
                    .deathSeconds(8 * 60 + 5.0)
                    .killerName("npc_dota_hero_lion")
                    .reason("TOWER")
                    .build()));
    when(deathEvents.findByMatchId(MATCH))
        .thenReturn(List.of(DeathEventDomain.builder().playerSlot(0L).deathTime(485L).build()));
    when(buybacks.findByMatchId(MATCH))
        .thenReturn(List.of(BuybackEventDomain.builder().playerSlot(0L).seconds(500.0).build()));

    IncomeChart chart = builder.build(MATCH, Set.of(0L), 2, 3, LINE).orElseThrow();

    assertThat(chart.verdict())
        .startsWith("Biggest income drop: Axe's income fell from 800 to 200 gold per minute")
        .contains("Axe died at 8:05 (to Lion, killed by a tower)");
    ChartSpec.Panel panel = chart.spec().panels().getFirst();
    assertThat(panel.bands()).hasSize(1);
    assertThat(panel.pins())
        .extracting(ChartSpec.Pin::shape)
        .contains(PinShape.CROSS, PinShape.DIAMOND);
    assertThat(chart.spec().legend())
        .extracting(ChartSpec.LegendEntry::text)
        .contains("Income fell", "Died", "Bought back");
    assertThat(new ChartRenderer().render(chart.spec())).isNotEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void aFallWithoutAStoredReasonSaysSo() {
    timeline(gold(0, withFall()));

    assertThat(builder.build(MATCH, Set.of(0L), 2, 3, LINE).orElseThrow().verdict())
        .endsWith("(no stored reason).");
  }

  @Test
  @SuppressWarnings("unchecked")
  void theOutlierExplanationAndAWastedRotationAreUsedWhenNoOneDied() {
    timeline(gold(0, withFall()));
    when(outliers.findByMatchId(MATCH))
        .thenReturn(
            List.of(
                PlayerOutlierDomain.builder()
                    .playerSlot(0L)
                    .incomeBreakSeconds(420)
                    .explanation("His income broke away from his team's at 7:00.")
                    .build()));
    when(rotations.findByMatchId(MATCH))
        .thenReturn(
            List.of(
                TeamRotationDomain.builder()
                    .playerSlot(0L)
                    .startSeconds(430)
                    .endSeconds(520)
                    .verdict("WASTED")
                    .build()));

    String verdict = builder.build(MATCH, Set.of(0L), 2, 3, LINE).orElseThrow().verdict();

    assertThat(verdict)
        .contains("His income broke away from his team's at 7:00.")
        .contains("Axe's rotation at 7:10 did not pay off");
  }

  @Test
  @SuppressWarnings("unchecked")
  void aMidasBuyerGetsThePurchaseThePaybackAndASentence() {
    timeline(gold(0, steady(14, 800)));
    ItemEfficiency midas =
        new ItemEfficiency(
            0,
            "hand_of_midas",
            "hand_of_midas",
            IncomePins.MIDAS_KIND,
            200.0,
            5,
            2000,
            2200L,
            0.9,
            600.0,
            Map.of(
                "gold",
                2000.0,
                "xp",
                500.0,
                "idleSeconds",
                60.0,
                "farmingSeconds",
                300.0,
                "heroGold",
                8000.0),
            List.of(),
            List.of());
    when(efficiency.findByMatchId(MATCH))
        .thenReturn(List.of(ItemEfficiencyJson.toRow(MATCH, midas, 1)));
    when(purchases.findByMatchId(MATCH))
        .thenReturn(
            List.of(
                ItemPurchaseEventDomain.builder()
                    .playerSlot(0L)
                    .purchaseTime(200L)
                    .itemKey("hand_of_midas")
                    .build(),
                ItemPurchaseEventDomain.builder()
                    .playerSlot(0L)
                    .purchaseTime(400L)
                    .itemKey("blink")
                    .build(),
                ItemPurchaseEventDomain.builder()
                    .playerSlot(0L)
                    .purchaseTime(300L)
                    .itemKey("tango")
                    .build()));
    when(kits.cost("hand_of_midas")).thenReturn(OptionalLong.of(2200));
    when(kits.cost("blink")).thenReturn(OptionalLong.of(2250));

    IncomeChart chart = builder.build(MATCH, Set.of(0L), 2, 3, LINE).orElseThrow();

    List<ChartSpec.Pin> pins = chart.spec().panels().getFirst().pins();
    assertThat(pins)
        .extracting(ChartSpec.Pin::label)
        .containsExactlyInAnyOrder("hand of midas", "hand of midas paid back", "blink");
    assertThat(chart.bullets()).anyMatch(b -> b.contains("paid for itself at 10:00"));
    assertThat(builder.build(MATCH, Set.of(0L), 2, 0, LINE).orElseThrow().bullets()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void badRowsAreSkippedInsteadOfThrowing() {
    List<PlayerTimelineDomain> rows = new ArrayList<>(gold(0, withFall()));
    rows.add(null);
    rows.add(PlayerTimelineDomain.builder().playerSlot(0L).gameMinute(null).gold(5L).build());
    rows.add(PlayerTimelineDomain.builder().playerSlot(null).gameMinute(3).gold(5L).build());
    rows.add(PlayerTimelineDomain.builder().playerSlot(0L).gameMinute(99999).gold(5L).build());
    rows.add(PlayerTimelineDomain.builder().playerSlot(0L).gameMinute(4).gold(null).build());
    when(timelines.findByMatchId(MATCH)).thenReturn(rows);
    when(deathEvents.findByMatchId(MATCH))
        .thenReturn(
            java.util.Arrays.asList(
                null,
                DeathEventDomain.builder().playerSlot(null).deathTime(5L).build(),
                DeathEventDomain.builder().playerSlot(0L).deathTime(null).build()));
    when(deathAnalysis.findByMatchId(MATCH))
        .thenReturn(
            List.of(DeathAnalysisDomain.builder().playerSlot(0L).deathSeconds(485.0).build()));
    when(efficiency.findByMatchId(MATCH))
        .thenReturn(
            List.of(
                com.ako.dbuff.dao.model.ItemEfficiencyDomain.builder()
                    .playerSlot(0L)
                    .details("{not json")
                    .build()));

    IncomeChart chart = builder.build(MATCH, Set.of(0L), 2, 3, LINE).orElseThrow();

    assertThat(chart.verdict()).contains("Axe died at 8:05");
    assertThat(new ChartRenderer().render(chart.spec())).isNotEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void theVerdictAndLinesStayWithinTheLineLength() {
    timeline(gold(0, withFall()));

    IncomeChart chart = builder.build(MATCH, Set.of(0L), 2, 3, 60).orElseThrow();

    assertThat(chart.verdict().length()).isLessThanOrEqualTo(60);
    assertThat(chart.bullets()).allMatch(b -> b.length() <= 60);
  }

  @Test
  @SuppressWarnings("unchecked")
  void theSameDataGivesTheSameFingerprintAndChangedDataDoesNot() {
    timeline(gold(0, withFall()));
    String first = builder.build(MATCH, Set.of(0L), 2, 3, LINE).orElseThrow().spec().fingerprint();
    String again = builder.build(MATCH, Set.of(0L), 2, 3, LINE).orElseThrow().spec().fingerprint();
    timeline(gold(0, steady(14, 700)));
    String changed =
        builder.build(MATCH, Set.of(0L), 2, 3, LINE).orElseThrow().spec().fingerprint();

    assertThat(again).isEqualTo(first);
    assertThat(changed).isNotEqualTo(first);
  }

  @Test
  @SuppressWarnings("unchecked")
  void aPurchaseNeverMakesADipAndRepeatedEventsAreDrawnOnce() {
    timeline(gold(0, steady(14, 800)));
    when(purchases.findByMatchId(MATCH))
        .thenReturn(
            List.of(
                ItemPurchaseEventDomain.builder()
                    .playerSlot(0L)
                    .purchaseTime(400L)
                    .itemKey("blink")
                    .build(),
                ItemPurchaseEventDomain.builder()
                    .playerSlot(0L)
                    .purchaseTime(402L)
                    .itemKey("blink")
                    .build(),
                ItemPurchaseEventDomain.builder()
                    .playerSlot(0L)
                    .purchaseTime(400L)
                    .itemKey("recipe_blink")
                    .build()));
    when(kits.cost("blink")).thenReturn(OptionalLong.of(2250));
    when(kits.cost("recipe_blink")).thenReturn(OptionalLong.of(2250));
    when(deathEvents.findByMatchId(MATCH))
        .thenReturn(
            List.of(
                DeathEventDomain.builder().playerSlot(0L).deathTime(300L).build(),
                DeathEventDomain.builder().playerSlot(0L).deathTime(300L).build()));

    IncomeChart chart = builder.build(MATCH, Set.of(0L), 2, 3, LINE).orElseThrow();

    assertThat(chart.verdict()).isEqualTo("No hero's income fell sharply.");
    assertThat(chart.spec().panels().getFirst().pins())
        .extracting(ChartSpec.Pin::shape)
        .containsExactlyInAnyOrder(PinShape.CROSS, PinShape.TRIANGLE);
  }

  @Test
  @SuppressWarnings("unchecked")
  void midasLinesCountAgainstTheFindingCapAndStayWithinTheLineLength() {
    timeline(gold(0, withFall()));
    ItemEfficiency midas =
        new ItemEfficiency(
            0,
            "hand_of_midas",
            "hand_of_midas",
            IncomePins.MIDAS_KIND,
            200.0,
            5,
            2000,
            2200L,
            0.9,
            600.0,
            Map.of(
                "gold",
                2000.0,
                "xp",
                500.0,
                "idleSeconds",
                60.0,
                "farmingSeconds",
                300.0,
                "heroGold",
                8000.0),
            List.of(),
            List.of());
    when(efficiency.findByMatchId(MATCH))
        .thenReturn(List.of(ItemEfficiencyJson.toRow(MATCH, midas, 1)));

    IncomeChart one = builder.build(MATCH, Set.of(0L), 2, 1, LINE).orElseThrow();
    IncomeChart tight = builder.build(MATCH, Set.of(0L), 2, 3, 50).orElseThrow();

    assertThat(one.bullets()).hasSize(1);
    assertThat(tight.bullets()).allMatch(b -> b.length() <= 50 && (b.endsWith(".")));
    assertThat(tight.bullets().getFirst()).doesNotContain("....");
  }
}
