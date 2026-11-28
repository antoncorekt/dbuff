package com.ako.dbuff.service.replay.defenseloop;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MissingDefenseItemDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.TeamDefenseItemDomain;
import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.defense.DefenseItemBonuses;
import com.ako.dbuff.service.replay.analysis.defense.DefenseProperties;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline.Change;
import com.ako.dbuff.service.replay.defenseloop.DefenseLoopTopic.MatchData;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopMatchList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefenseLoopTopicTest {
  private static final Map<String, ItemConstant> ITEMS =
      Map.of(
          "shivas_guard",
          ItemConstant.builder()
              .attrib(
                  List.of(
                      Map.of("key", "bonus_armor", "value", "17", "display", "+ {value} Armor")))
              .build(),
          "hood_of_defiance",
          ItemConstant.builder()
              .attrib(
                  List.of(
                      Map.of(
                          "key",
                          "tooltip_resist",
                          "value",
                          "18",
                          "display",
                          "+ {value}% Magic Resistance")))
              .build());

  private static MatchData data() {
    MatchDomain match = new MatchDomain();
    match.setId(1L);
    HeroStateTimeline timeline = new HeroStateTimeline();
    timeline.add(new Change(300, StateChangeKind.ITEM_ADDED, "item_shivas_guard", 0));
    timeline.add(new Change(300, StateChangeKind.ITEM_ADDED, "item_hood_of_defiance", 0));
    ReplayPlayerTrackDomain track =
        ReplayPlayerTrackDomain.builder()
            .playerSlot(0L)
            .startSeconds(0)
            .stepSeconds(60)
            .sampleCount(31)
            .baseArmorX10(filled(20))
            .baseMagicResistX10(filled(250))
            .agilityTotalX10(filled(300))
            .build();
    return new MatchData(
        1,
        match,
        "All Pick",
        Map.of(0L, "Dark Seer"),
        List.of(
            TeamDefensePointDomain.builder()
                .team("RADIANT")
                .minute(20)
                .checkpoint("M20")
                .maxHpSum(4000)
                .armorAvgX10(120)
                .magicResistAvgX10(350)
                .ehpPhys(6000)
                .ehpMag(6500)
                .ehpMix(6200)
                .enemyPhysShare(0.6)
                .enemyMagShare(0.3)
                .enemyPureShare(0.1)
                .netWorth(30000)
                .build()),
        List.of(
            TeamDefenseItemDomain.builder()
                .team("RADIANT")
                .playerSlot(0L)
                .item("shivas_guard")
                .kind("PHYSICAL")
                .atSecond(300)
                .build()),
        List.of(
            MissingDefenseItemDomain.builder()
                .team("DIRE")
                .checkpoint("M20")
                .kind("MAGIC")
                .minute(20)
                .enemyShare(0.7)
                .withGames(10L)
                .withWins(7L)
                .withoutGames(12L)
                .withoutWins(4L)
                .evidence("no magic defence")
                .build()),
        List.of(track),
        Map.of(0, timeline),
        new DefenseItemBonuses(key -> Optional.ofNullable(ITEMS.get(key))),
        new DefenseProperties(),
        "1-graph-defense.png");
  }

  private static int[] filled(int value) {
    int[] values = new int[31];
    java.util.Arrays.fill(values, value);
    return values;
  }

  @Test
  void theCommittedListHoldsTheThreeAbilityDraftAndThreeAllPickFixtures() {
    assertThat(ReviewLoopMatchList.read(DefenseLoopTopic.MATCH_LIST))
        .containsExactly(
            9037038717L, 9038725154L, 9044256304L, 9040889566L, 9044278208L, 9044287653L);
  }

  @Test
  void rendersTheTablesAndTheBreakdownRow() {
    String report = DefenseLoopTopic.render(data());

    assertThat(report)
        .contains("## Team defense")
        .contains(
            "| RADIANT | M20 | 20 | 4000 | 12.0 | 35.0% | 6000 | 6500 | 6200 | 60.0% | 30.0% |")
        .contains("| RADIANT | Dark Seer | Shivas Guard | 5:00 | PHYSICAL |")
        .contains("| DIRE | M20 | MAGIC | 20 | 70.0% | 7/10 | 4/12 | no magic defence |")
        .contains("1-graph-defense.png");
    assertThat(report)
        .contains(
            "| RADIANT | Dark Seer | M20 | 2.0 | 5.0 | 17.0 | Shivas Guard +17.0 | 24.0 | 25.0% |"
                + " 18.0% | Hood Of Defiance 18.0% | 38.5% |");
  }
}
