package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.SmokeEvents.RoshanKill;
import com.ako.dbuff.service.replay.analysis.behaviour.TeamClusters.Point;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter.State;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.ZONES;
import static org.assertj.core.api.Assertions.assertThat;

class ObjectiveFinderTest {
  private static final double GAME_END = 1000;
  private static final StandingTower FIRST =
      new StandingTower(
          "dota_badguys_tower1_mid", TeamSide.DIRE, Zone.Lane.MID, 1, new Point(1000, 0));
  private static final StandingTower SECOND =
      new StandingTower(
          "dota_badguys_tower2_mid", TeamSide.DIRE, Zone.Lane.MID, 2, new Point(3000, 0));
  private static final StandingTower OWN =
      new StandingTower(
          "dota_goodguys_tower1_mid", TeamSide.RADIANT, Zone.Lane.MID, 1, new Point(100, 0));

  private final ObjectiveFinder finder = new ObjectiveFinder(new BehaviourProperties(), ZONES);

  private static ObjectiveFight fight(State before) {
    return new ObjectiveFight(3, TeamSide.RADIANT, 80, 100, before);
  }

  private static Map<Long, Trace> traces() {
    return Map.of(
        0L,
        BehaviourTestData.stayingAt(0),
        1L,
        BehaviourTestData.stayingAt(500),
        2L,
        BehaviourTestData.stayingAt(5000),
        128L,
        BehaviourTestData.stayingAt(2000));
  }

  private static TowerFall fall(TeamSide owner, double seconds, String kind, String name) {
    return new TowerFall(owner, seconds, Zone.Lane.MID, false, Set.of(), kind, name, null);
  }

  private static ObjectiveFinder.Events events(List<TowerFall> buildings, List<RoshanKill> roshan) {
    return new ObjectiveFinder.Events(buildings, roshan, List.of(FIRST, SECOND, OWN), GAME_END);
  }

  private ObjectiveFinder.Found find(
      ObjectiveFight fight, List<TowerFall> buildings, List<RoshanKill> roshan) {
    return finder.find(List.of(fight), traces(), events(buildings, roshan));
  }

  @Test
  void find_aTowerOfTheLoserInTheWindowIsConverted() {
    ObjectiveConversion c =
        find(
                fight(null),
                List.of(fall(TeamSide.DIRE, 130, "TOWER", "npc_dota_badguys_tower1_mid")),
                List.of())
            .conversions()
            .getFirst();

    assertThat(c.result()).isEqualTo(ObjectiveResult.CONVERTED);
    assertThat(c.kind()).isEqualTo(ObjectiveKind.TOWER);
    assertThat(c.key()).isEqualTo("npc_dota_badguys_tower1_mid");
    assertThat(c.secondsAfter()).isEqualTo(30);
    assertThat(c.nearest()).isNull();
    assertThat(c.roshan()).isNull();
  }

  @Test
  void find_barracksAndTheAncientAreObjectivesToo() {
    assertThat(
            find(fight(null), List.of(fall(TeamSide.DIRE, 120, "BARRACKS_RANGED", "b")), List.of())
                .conversions()
                .getFirst()
                .kind())
        .isEqualTo(ObjectiveKind.BARRACKS);
    assertThat(
            find(fight(null), List.of(fall(TeamSide.DIRE, 120, "FORT", "f")), List.of())
                .conversions()
                .getFirst()
                .kind())
        .isEqualTo(ObjectiveKind.ANCIENT);
  }

  @Test
  void find_theEarliestObjectiveIsTheOneTold() {
    ObjectiveConversion c =
        find(
                fight(State.AHEAD),
                List.of(fall(TeamSide.DIRE, 150, "TOWER", "t")),
                List.of(new RoshanKill(0, 120)))
            .conversions()
            .getFirst();

    assertThat(c.kind()).isEqualTo(ObjectiveKind.ROSHAN);
    assertThat(c.secondsAfter()).isEqualTo(20);
  }

  @Test
  void find_aTowerThatFellInsideTheFightCountsWithZeroSeconds() {
    ObjectiveConversion c =
        find(fight(null), List.of(fall(TeamSide.DIRE, 95, "TOWER", "t")), List.of())
            .conversions()
            .getFirst();

    assertThat(c.result()).isEqualTo(ObjectiveResult.CONVERTED);
    assertThat(c.secondsAfter()).isZero();
  }

  @Test
  void find_aDenyAnOwnTowerAndAnObjectiveOutsideTheWindowAreNotConversions() {
    TowerFall denied =
        new TowerFall(TeamSide.DIRE, 120, Zone.Lane.MID, true, Set.of(), "TOWER", "d", null);
    ObjectiveFinder.Found found =
        find(
            fight(null),
            List.of(
                denied,
                fall(TeamSide.RADIANT, 120, "TOWER", "own"),
                fall(TeamSide.DIRE, 79, "TOWER", "before"),
                fall(TeamSide.DIRE, 191, "TOWER", "late")),
            List.of());

    assertThat(found.conversions().getFirst().result()).isEqualTo(ObjectiveResult.MISSED);
  }

  @Test
  void find_aMissNamesTheNearestStandingEnemyTowerAndHowFarTheClosestHeroWas() {
    ObjectiveConversion c = find(fight(null), List.of(), List.of()).conversions().getFirst();

    assertThat(c.result()).isEqualTo(ObjectiveResult.MISSED);
    assertThat(c.kind()).isNull();
    assertThat(c.secondsAfter()).isNull();
    assertThat(c.winnersAlive()).isEqualTo(3);
    assertThat(c.nearest().tower()).isEqualTo(FIRST);
    assertThat(c.nearest().distance()).isEqualTo(500);
    assertThat(c.nearest().heroSlot()).isEqualTo(1);
    assertThat(c.nearest().heroZone()).isEqualTo(BehaviourTestData.MID);
  }

  @Test
  void find_aTowerThatFellBeforeTheFightIsNotStanding() {
    ObjectiveConversion c =
        find(
                fight(null),
                List.of(fall(TeamSide.DIRE, 60, "TOWER", "npc_dota_badguys_tower1_mid")),
                List.of())
            .conversions()
            .getFirst();

    assertThat(c.nearest().tower()).isEqualTo(SECOND);
  }

  @Test
  void find_aMissWithNoLivingWinnerHasNoDistance() {
    Map<Long, Trace> dead = Map.of(0L, BehaviourTestData.trace(i -> 0, i -> 1));

    ObjectiveConversion c =
        finder
            .find(List.of(fight(null)), dead, events(List.of(), List.of()))
            .conversions()
            .getFirst();

    assertThat(c.winnersAlive()).isZero();
    assertThat(c.nearest()).isNull();
  }

  @Test
  void find_roshanKilledByTheWinnerWhileAheadIsExpectedWithWhoStoodAtThePit() {
    ObjectiveConversion c =
        find(fight(State.AHEAD), List.of(), List.of(new RoshanKill(0, 140)))
            .conversions()
            .getFirst();

    assertThat(c.result()).isEqualTo(ObjectiveResult.CONVERTED);
    assertThat(c.kind()).isEqualTo(ObjectiveKind.ROSHAN);
    assertThat(c.key()).isEqualTo("npc_dota_roshan");
    assertThat(c.secondsAfter()).isEqualTo(40);
    assertThat(c.roshan().nearSlots()).containsExactly(0L, 1L);
    assertThat(c.roshan().call()).isEqualTo(RoshanCall.EXPECTED);
  }

  @Test
  void find_roshanWhileBehindIsAGambleAndWithoutAStateHasNoCall() {
    List<RoshanKill> kill = List.of(new RoshanKill(0, 140));

    assertThat(find(fight(State.BEHIND), List.of(), kill).conversions().getFirst().roshan().call())
        .isEqualTo(RoshanCall.GAMBLE);
    assertThat(find(fight(State.EVEN), List.of(), kill).conversions().getFirst().roshan().call())
        .isEqualTo(RoshanCall.EVEN);
    assertThat(find(fight(null), List.of(), kill).conversions().getFirst().roshan().call())
        .isNull();
  }

  @Test
  void find_roshanKilledByTheLoserOrNotKilledIsAMiss() {
    assertThat(
            find(fight(null), List.of(), List.of(new RoshanKill(128, 140)))
                .conversions()
                .getFirst()
                .result())
        .isEqualTo(ObjectiveResult.MISSED);
    assertThat(find(fight(null), List.of(), List.of()).conversions().getFirst().result())
        .isEqualTo(ObjectiveResult.MISSED);
  }

  @Test
  void find_aKillerWithNoTrackSampleLeavesNobodyNearTheRoshanPit() {
    ObjectiveConversion c =
        find(fight(State.EVEN), List.of(), List.of(new RoshanKill(4, 140)))
            .conversions()
            .getFirst();

    assertThat(c.result()).isEqualTo(ObjectiveResult.CONVERTED);
    assertThat(c.roshan().nearSlots()).isNull();
    assertThat(c.roshan().call()).isEqualTo(RoshanCall.EVEN);
  }

  @Test
  void find_aFightAtTheEndOfTheGameWithNothingTakenIsLeftOutNotCalledMissed() {
    ObjectiveFinder.Found found =
        finder.find(
            List.of(fight(null)),
            traces(),
            new ObjectiveFinder.Events(List.of(), List.of(), List.of(FIRST), 150));

    assertThat(found.conversions()).isEmpty();
    assertThat(found.cutShort()).isEqualTo(1);
  }

  @Test
  void find_aFightAtTheEndOfTheGameThatEndedItWithTheAncientIsConverted() {
    ObjectiveFinder.Found found =
        finder.find(
            List.of(fight(null)),
            traces(),
            new ObjectiveFinder.Events(
                List.of(fall(TeamSide.DIRE, 150, "FORT", "f")), List.of(), List.of(FIRST), 150));

    assertThat(found.conversions().getFirst().kind()).isEqualTo(ObjectiveKind.ANCIENT);
    assertThat(found.cutShort()).isZero();
  }

  @Test
  void find_noImportantFightsIsNothing() {
    ObjectiveFinder.Found found = finder.find(List.of(), traces(), events(List.of(), List.of()));

    assertThat(found.conversions()).isEmpty();
    assertThat(found.cutShort()).isZero();
  }

  @Test
  void find_aFightWhoseWindowFitsBeforeTheRealEndIsMissedNotCutShort() {
    ObjectiveFinder.Found found =
        finder.find(
            List.of(fight(null)),
            traces(),
            new ObjectiveFinder.Events(List.of(), List.of(), List.of(FIRST), 190));

    assertThat(found.conversions().getFirst().result()).isEqualTo(ObjectiveResult.MISSED);
    assertThat(found.cutShort()).isZero();
  }

  @Test
  void find_aFallenTowerWithoutANameIsMatchedByOwnerLaneAndTier() {
    TowerFall unnamed =
        new TowerFall(TeamSide.DIRE, 60, Zone.Lane.MID, false, Set.of(), "TOWER", null, 1);

    ObjectiveConversion c = find(fight(null), List.of(unnamed), List.of()).conversions().getFirst();

    assertThat(c.nearest().tower()).isEqualTo(SECOND);
  }

  @Test
  void find_aFallenTowerOfAnotherOwnerLaneOrTierDoesNotRemoveAStandingOne() {
    List<TowerFall> others =
        List.of(
            new TowerFall(TeamSide.RADIANT, 60, Zone.Lane.MID, false, Set.of(), "TOWER", null, 1),
            new TowerFall(TeamSide.DIRE, 60, Zone.Lane.TOP, false, Set.of(), "TOWER", null, 1),
            new TowerFall(TeamSide.DIRE, 60, Zone.Lane.MID, false, Set.of(), "TOWER", null, 3));

    assertThat(find(fight(null), others, List.of()).conversions().getFirst().nearest().tower())
        .isEqualTo(FIRST);
  }

  @Test
  void find_oneObjectiveConvertsOnlyTheEarliestFightThatCoversIt() {
    ObjectiveFight first = new ObjectiveFight(1, TeamSide.RADIANT, 80, 100, null);
    ObjectiveFight second = new ObjectiveFight(2, TeamSide.RADIANT, 120, 140, null);

    List<ObjectiveConversion> found =
        finder
            .find(
                List.of(second, first),
                traces(),
                events(List.of(fall(TeamSide.DIRE, 150, "TOWER", "t")), List.of()))
            .conversions();

    assertThat(found).extracting(ObjectiveConversion::fightIndex).containsExactly(1, 2);
    assertThat(found.get(0).result()).isEqualTo(ObjectiveResult.CONVERTED);
    assertThat(found.get(1).result()).isEqualTo(ObjectiveResult.MISSED);
  }

  @Test
  void find_theLaterFightIsConvertedByALaterObjective() {
    ObjectiveFight first = new ObjectiveFight(1, TeamSide.RADIANT, 80, 100, null);
    ObjectiveFight second = new ObjectiveFight(2, TeamSide.RADIANT, 120, 140, null);

    List<ObjectiveConversion> found =
        finder
            .find(
                List.of(first, second),
                traces(),
                events(
                    List.of(
                        fall(TeamSide.DIRE, 150, "TOWER", "t"),
                        fall(TeamSide.DIRE, 220, "TOWER", "u")),
                    List.of()))
            .conversions();

    assertThat(found).extracting(ObjectiveConversion::key).containsExactly("t", "u");
  }
}
