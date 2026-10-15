package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ObjectiveConversionRepo;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObjectivesSectionTest {
  private final ObjectiveConversionRepo repo = mock(ObjectiveConversionRepo.class);
  private final FightRepo fightRepo = mock(FightRepo.class);
  private final FightImportanceRepo importanceRepo = mock(FightImportanceRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final ObjectivesSection section =
      new ObjectivesSection(repo, fightRepo, importanceRepo, heroNames, properties);
  private final List<ObjectiveConversionDomain> rows = new ArrayList<>();
  private final List<FightDomain> fights = new ArrayList<>();
  private final List<FightImportanceDomain> importance = new ArrayList<>();
  private long nextId = 1;

  ObjectivesSectionTest() {
    when(repo.findByMatchId(1L)).thenReturn(rows);
    when(fightRepo.findByMatchId(1L)).thenReturn(fights);
    when(importanceRepo.findByMatchId(1L)).thenReturn(importance);
    when(heroNames.bySlot(1L)).thenReturn(Map.of(0L, "Axe", 1L, "Lion", 128L, "Sven"));
  }

  private ObjectiveConversionDomain fight(int index, int start, String winner, String result) {
    fights.add(
        FightDomain.builder().id(nextId++).fightIndex(index).startSeconds((double) start).build());
    ObjectiveConversionDomain row =
        ObjectiveConversionDomain.builder()
            .id(nextId++)
            .matchId(1L)
            .fightIndex(index)
            .winnerTeam(winner)
            .result(result)
            .windowSeconds(90)
            .build();
    rows.add(row);
    return row;
  }

  private ObjectiveConversionDomain missed(int index, int start, long distance) {
    ObjectiveConversionDomain row = fight(index, start, "RADIANT", "MISSED");
    row.setNearestTower("npc_dota_badguys_tower1_top");
    row.setTowerLane("TOP");
    row.setTowerDistance((int) distance);
    row.setNearestHeroSlot(0L);
    row.setHeroZone("LANE");
    row.setHeroLane("TOP");
    row.setWinnersAlive(4);
    return row;
  }

  private void swing(int index, long gold) {
    importance.add(FightImportanceDomain.builder().fightIndex(index).goldChange(gold).build());
  }

  private ReplayReportInput input(Long... focus) {
    return new ReplayReportInput(1L, null, Set.of(focus), Map.of(), 3);
  }

  @Test
  void isOffByDefaultAndStandardDetail() {
    assertThat(section.name()).isEqualTo("objectives");
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(section.minimumDetail()).isEqualTo(com.ako.dbuff.dao.model.ReportDetail.STANDARD);
  }

  @Test
  void writesTheSummaryThenAMissedFightWithTheTowerAndHowFarTheClosestHeroWas() {
    missed(1, 760, 4200);
    fight(2, 900, "DIRE", "CONVERTED");

    List<String> lines = section.lines(input(1L));

    assertThat(lines)
        .containsExactly(
            "**Objectives**",
            "Won fights turned into objectives: 1 of 2",
            "12:40 Radiant (your team) won the fight but did not take an objective in the next 90"
                + " seconds; the nearest Dire tower still standing was the top lane tier 1 tower"
                + " and Axe, the winner's closest hero, stood in the top lane, far from it.");
  }

  @Test
  void describesTheDistanceInThreeBandsFromTheProperties() {
    missed(1, 100, 1500);
    missed(2, 200, 1501);
    missed(3, 300, 4000);

    List<String> lines = section.lines(input());

    assertThat(lines.get(2)).endsWith("stood in the top lane, close to it.");
    assertThat(lines.get(3)).endsWith("stood in the top lane, a fair way from it.");
    assertThat(lines.get(4)).endsWith("stood in the top lane, far from it.");
    assertThat(lines.get(2)).contains("Radiant won the fight").doesNotContain("your team");
  }

  @Test
  void capsAtThreeByTheBiggestGoldSwingAndListsInTimeOrder() {
    for (int i = 0; i < 5; i++) {
      missed(i, 100 * (i + 1), 1000);
    }
    swing(0, 500);
    swing(1, 9000);
    swing(2, 100);
    swing(3, 7000);
    swing(4, 8000);

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(6);
    assertThat(lines.get(1)).endsWith("0 of 5");
    assertThat(lines.get(5)).isEqualTo("and 2 more missed fights");
    assertThat(lines.get(2)).startsWith("3:20");
    assertThat(lines.get(3)).startsWith("6:40");
    assertThat(lines.get(4)).startsWith("8:20");
  }

  @Test
  void withoutImportanceRowsTheMostRecentFightsAreListed() {
    for (int i = 0; i < 4; i++) {
      missed(i, 100 * (i + 1), 1000);
    }

    List<String> lines = section.lines(input());

    assertThat(lines.subList(2, 5))
        .extracting(l -> l.substring(0, 4))
        .containsExactly("3:20", "5:00", "6:40");
    assertThat(lines.get(5)).isEqualTo("and 1 more missed fight");
  }

  @Test
  void aTieBreaksOnTheRowId() {
    properties.setMaxObjectiveLines(1);
    missed(1, 100, 1000).setHeroZone("RIVER");
    missed(2, 100, 1000);
    List<ObjectiveConversionDomain> reversed = new ArrayList<>(rows);
    java.util.Collections.reverse(reversed);
    when(repo.findByMatchId(1L)).thenReturn(reversed);

    assertThat(section.lines(input()).get(2)).contains("stood in the river");
  }

  @Test
  void twoRowsOfTheSameFightIndexKeepTheLargerIdWhateverTheRepositoryOrder() {
    ObjectiveConversionDomain old = missed(1, 100, 1000);
    old.setHeroZone("RIVER");
    ObjectiveConversionDomain latest = missed(1, 100, 1000);
    latest.setHeroZone("BASE");
    for (boolean reversed : new boolean[] {false, true}) {
      List<ObjectiveConversionDomain> order = new ArrayList<>(List.of(old, latest));
      List<FightDomain> fightOrder = new ArrayList<>(fights);
      if (reversed) {
        java.util.Collections.reverse(order);
        java.util.Collections.reverse(fightOrder);
      }
      when(repo.findByMatchId(1L)).thenReturn(order);
      when(fightRepo.findByMatchId(1L)).thenReturn(fightOrder);

      List<String> lines = section.lines(input());

      assertThat(lines).hasSize(3);
      assertThat(lines.get(1)).endsWith("0 of 1");
      assertThat(lines.get(2)).contains("stood in the base");
    }
  }

  @Test
  void aRowWithoutTheStoredWindowSaysSoonAfterAndANewWindowIsNotRestated() {
    ObjectiveConversionDomain old = missed(1, 100, 1000);
    old.setWindowSeconds(null);
    ObjectiveConversionDomain changed = missed(2, 200, 1000);
    changed.setWindowSeconds(60);

    List<String> lines = section.lines(input());

    assertThat(lines.get(2)).contains("did not take an objective soon after;").doesNotContain("90");
    assertThat(lines.get(3)).contains("did not take an objective in the next 60 seconds;");
  }

  @Test
  void aFightWithoutAClockIsTheOldestForTheCapAndNeverTheRoshanSentence() {
    properties.setMaxObjectiveLines(1);
    ObjectiveConversionDomain unknown = missed(1, 100, 1000);
    fights.removeIf(f -> f.getFightIndex() == 1);
    unknown.setHeroZone("RIVER");
    missed(2, 50, 1000).setHeroZone("BASE");
    ObjectiveConversionDomain roshan = fight(3, 900, "RADIANT", "CONVERTED");
    roshan.setObjectiveKind("ROSHAN");
    roshan.setRoshanNearSlots("0");
    ObjectiveConversionDomain lost = fight(4, 800, "RADIANT", "CONVERTED");
    lost.setObjectiveKind("ROSHAN");
    lost.setRoshanNearSlots("1");
    fights.removeIf(f -> f.getFightIndex() == 4);

    List<String> lines = section.lines(input());

    assertThat(lines.get(2)).startsWith("0:50").contains("stood in the base");
    assertThat(lines.get(3)).isEqualTo("and 1 more missed fight");
    assertThat(lines.get(4)).startsWith("After the won fight at 15:00").contains("Axe stood");

    fights.removeIf(f -> f.getFightIndex() == 3);
    assertThat(section.lines(input())).noneMatch(l -> l.startsWith("After the won fight"));
  }

  @Test
  void aNegativeOrMissingDistanceIsNotKnown() {
    missed(1, 100, -5);
    missed(2, 200, 1000).setTowerDistance(null);

    List<String> lines = section.lines(input());

    assertThat(lines.get(2)).endsWith("stood in the top lane, how far from it is not known.");
    assertThat(lines.get(3)).endsWith("stood in the top lane, how far from it is not known.");
  }

  @Test
  void aRoshanKillAfterAWonFightGetsOneSentenceAboutWhoWasAtThePit() {
    ObjectiveConversionDomain a = fight(1, 1300, "RADIANT", "CONVERTED");
    a.setObjectiveKind("ROSHAN");
    a.setRoshanNearSlots("0,1");
    a.setRoshanCall("EXPECTED");
    ObjectiveConversionDomain b = fight(2, 2000, "DIRE", "CONVERTED");
    b.setObjectiveKind("ROSHAN");
    b.setRoshanNearSlots("128");
    b.setRoshanCall("GAMBLE");

    List<String> lines = section.lines(input(128L));

    assertThat(lines).hasSize(3);
    assertThat(lines.get(1)).isEqualTo("Won fights turned into objectives: 2 of 2");
    assertThat(lines.get(2))
        .isEqualTo(
            "After the won fight at 33:20 Dire (your team) took Roshan: Sven stood at the pit; a"
                + " gamble, the winner was behind.");
  }

  @Test
  void aTowerObjectiveIsNotListedAndMissingPitOrCallAreAdmitted() {
    ObjectiveConversionDomain a = fight(1, 100, "RADIANT", "CONVERTED");
    a.setObjectiveKind("TOWER");
    assertThat(section.lines(input())).hasSize(2);

    a.setObjectiveKind("ROSHAN");
    assertThat(section.lines(input()).get(2))
        .contains("where the killer stood is not known; the state before the fight is not known");
  }

  @Test
  void aMissedFightWithNoTowerSaysWhy() {
    ObjectiveConversionDomain a = fight(1, 100, "RADIANT", "MISSED");
    a.setWinnersAlive(0);
    fight(2, 200, "RADIANT", "MISSED");

    List<String> lines = section.lines(input());

    assertThat(lines.get(2)).endsWith("; no hero of the winner was alive when the fight ended.");
    assertThat(lines.get(3)).endsWith("; no enemy lane tower was left to measure from.");
  }

  @Test
  void aMatchWithNoImportantFightsPrintsNothing() {
    assertThat(section.lines(input())).isEmpty();
  }

  @Test
  void nullDataAndUnknownRowsDoNotThrowAndAreSkipped() {
    when(repo.findByMatchId(1L)).thenReturn(null);
    assertThat(section.lines(input())).isEmpty();

    when(repo.findByMatchId(1L)).thenReturn(rows);
    when(fightRepo.findByMatchId(1L)).thenReturn(null);
    when(importanceRepo.findByMatchId(1L)).thenReturn(null);
    rows.add(null);
    rows.add(ObjectiveConversionDomain.builder().build());
    rows.add(ObjectiveConversionDomain.builder().id(5L).result("WHATEVER").build());
    assertThat(section.lines(input())).isEmpty();

    rows.add(ObjectiveConversionDomain.builder().result("MISSED").nearestTower("x").build());
    List<String> lines = section.lines(new ReplayReportInput(1L, null, null, null, 3));

    assertThat(lines).hasSize(3);
    assertThat(lines.get(1)).endsWith("0 of 1");
    assertThat(lines.get(2))
        .startsWith("? An unknown team won the fight")
        .contains("a tower")
        .contains("how far from it is not known");
  }

  @Test
  void validatesTheCapAndTheDistanceCutOffsForAnyDouble() {
    for (int bad : new int[] {0, 11}) {
      properties.setMaxObjectiveLines(bad);
      assertThatThrownBy(properties::validate).hasMessageContaining("max-objective-lines");
    }
    properties.setMaxObjectiveLines(10);
    properties.validate();

    double[][] bad = {
      {Double.NaN, 4000},
      {1500, Double.NaN},
      {0, 4000},
      {-1, 4000},
      {4000, 1500},
      {1500, 1500},
      {1500, 20001},
      {1500, Double.POSITIVE_INFINITY},
      {Double.NEGATIVE_INFINITY, 4000}
    };
    for (double[] pair : bad) {
      properties.setObjectiveCloseDistance(pair[0]);
      properties.setObjectiveFarDistance(pair[1]);
      assertThatThrownBy(properties::validate)
          .as("%s %s", pair[0], pair[1])
          .hasMessageContaining("objective-close-distance");
    }
    properties.setObjectiveCloseDistance(1);
    properties.setObjectiveFarDistance(20000);
    properties.validate();
  }

  @Test
  void view_countsTheTrackedTeamsConversionsAndKeepsPlainTextAsItWas() {
    missed(1, 760, 4200);
    fight(2, 900, "DIRE", "CONVERTED");

    SectionView view = section.view(input(1L)).orElseThrow();

    ViewRendering.assertWithinLimits(view);
    assertThat(view.title()).isEqualTo("Objectives");
    assertThat(view.verdict())
        .isEqualTo("Radiant (your team) turned 0 of 1 won fight into objectives");
    assertThat(view.accent()).isEqualTo(Accent.LOSS);
    assertThat(view.bullets()).hasSize(1);
    assertThat(String.join("\n", section.lines(input(1L))))
        .isEqualTo(
            "**Objectives**\n"
                + "Won fights turned into objectives: 1 of 2\n"
                + "12:40 Radiant (your team) won the fight but did not take an objective in the"
                + " next 90 seconds; the nearest Dire tower still standing was the top lane tier 1"
                + " tower and Axe, the winner's closest hero, stood in the top lane, far from"
                + " it.");
    assertThat(view.bullets()).containsExactlyElementsOf(section.lines(input(1L)).subList(2, 3));
  }

  @Test
  void view_usesTheSummaryAndIsGreyWithoutATrackedTeam() {
    missed(1, 760, 4200);
    fight(2, 900, "DIRE", "CONVERTED");

    SectionView view = section.view(input()).orElseThrow();

    assertThat(view.verdict()).isEqualTo("Won fights turned into objectives: 1 of 2");
    assertThat(view.accent()).isEqualTo(Accent.NEUTRAL);
  }

  @Test
  void view_isEmptyWithoutWonFights() {
    assertThat(section.view(input())).isEmpty();
  }
}
