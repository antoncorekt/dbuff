package com.ako.dbuff.service.replay.analysis.fight;

import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FightAssessorTest {

  private final FightAssessor assessor = new FightAssessor(1500, 10);

  @Test
  void assess_decidesTheWinnerByTheLevelsOfTheHeroesKilled() {
    var fight =
        fight(
            Set.of(0, 5, 6),
            List.of(new FightDetector.Death(101, 5), new FightDetector.Death(102, 6)),
            0,
            0);

    var assessment = assessor.assess(fight, traces(), Map.of());

    assertThat(assessment.winner()).isEqualTo("RADIANT");
    assertThat(assessment.direDeaths()).isEqualTo(2);
    assertThat(assessment.radiantDeaths()).isZero();
  }

  @Test
  void assess_fallsBackToDamageAndCallsAnEvenFightADraw() {
    var evenDamage = fight(Set.of(0, 5), List.of(), 1000, 1000);
    var radiantDamage = fight(Set.of(0, 5), List.of(), 3000, 1000);

    assertThat(assessor.assess(evenDamage, traces(), Map.of()).winner()).isEqualTo("DRAW");
    assertThat(assessor.assess(radiantDamage, traces(), Map.of()).winner()).isEqualTo("RADIANT");
  }

  @Test
  void assess_explainsWhyAPlayerWasNotInTheFight() {
    Map<Integer, FightAssessor.Trace> traces = traces();
    traces.put(1, trace(9000, 9000, 1, 1));
    traces.put(2, trace(100, 100, 1, 1));
    traces.put(3, trace(0, 0, 1, 0));
    traces.put(4, trace(9000, 9000, 1, 1));
    traces.put(7, trace(9000, 9000, 1, 1));
    Map<Integer, List<ActivitySegment>> activities =
        Map.of(
            4, List.of(segment(Activity.JUNGLE_FARM)),
            7, List.of(segment(Activity.TELEPORTING)));
    var fight = fight(Set.of(0, 5), List.of(), 1000, 1000);

    var parts = new HashMap<Integer, FightAssessor.PlayerPart>();
    assessor.assess(fight, traces, activities).parts().forEach(p -> parts.put(p.player(), p));

    assertThat(parts.get(0).presence()).isEqualTo(Presence.ACTIVE);
    assertThat(parts.get(2).presence()).isEqualTo(Presence.PRESENT_PASSIVE);
    assertThat(parts.get(1).presence()).isEqualTo(Presence.ABSENT_FAR);
    assertThat(parts.get(3).presence()).isEqualTo(Presence.ABSENT_DEAD);
    assertThat(parts.get(4).presence()).isEqualTo(Presence.ABSENT_FARMING);
    assertThat(parts.get(7).presence()).isEqualTo(Presence.ABSENT_TELEPORTING);
    assertThat(parts.get(4).activityAtStart()).isEqualTo("JUNGLE_FARM");
    assertThat(parts.get(1).distanceToCenter()).isGreaterThan(5000);
  }

  private static ActivitySegment segment(Activity activity) {
    return new ActivitySegment(0, 1000, activity, Zone.Kind.JUNGLE, null, ActivitySegment.Side.OWN);
  }

  private static FightDetector.Detected fight(
      Set<Integer> participants,
      List<FightDetector.Death> deaths,
      long radiantDamage,
      long direDamage) {
    Map<Integer, Long> dealt = new TreeMap<>();
    dealt.put(0, radiantDamage);
    dealt.put(5, direDamage);
    return new FightDetector.Detected(
        deaths.isEmpty() ? FightType.ENGAGEMENT : FightType.SKIRMISH,
        100,
        110,
        deaths,
        dealt,
        Map.of(),
        participants);
  }

  private static Map<Integer, FightAssessor.Trace> traces() {
    Map<Integer, FightAssessor.Trace> traces = new HashMap<>();
    for (int p = 0; p < 10; p++) {
      traces.put(p, trace(0, 0, 10, 1));
    }
    return traces;
  }

  private static FightAssessor.Trace trace(int x, int y, int level, int flags) {
    int[] xs = new int[40];
    int[] ys = new int[40];
    int[] levels = new int[40];
    int[] alive = new int[40];
    java.util.Arrays.fill(xs, x);
    java.util.Arrays.fill(ys, y);
    java.util.Arrays.fill(levels, level);
    java.util.Arrays.fill(alive, flags);
    return new FightAssessor.Trace(0, 5, xs, ys, levels, alive);
  }
}
