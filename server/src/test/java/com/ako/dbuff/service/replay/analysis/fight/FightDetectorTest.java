package com.ako.dbuff.service.replay.analysis.fight;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FightDetectorTest {

  private static final Map<String, Integer> PLAYERS =
      IntStream.range(0, 10)
          .boxed()
          .collect(java.util.stream.Collectors.toMap(i -> "npc_dota_hero_h" + i, i -> i));

  private final FightDetector detector =
      new FightDetector(new ReplayAnalysisProperties().fightTuning());

  @Test
  void detect_callsThreeDeathsATeamfightAndKeepsTheirOrderAndVictims() {
    List<CombatEvent> events = new ArrayList<>();
    events.addAll(burst(100, 0, 5));
    events.add(death(102, 5));
    events.add(death(105, 6));
    events.add(death(108, 0));

    List<FightDetector.Detected> fights = detector.detect(events, PLAYERS);

    assertThat(fights).hasSize(1);
    assertThat(fights.getFirst().type()).isEqualTo(FightType.TEAMFIGHT);
    assertThat(fights.getFirst().deaths())
        .extracting(FightDetector.Death::victim)
        .containsExactly(5, 6, 0);
    assertThat(fights.getFirst().firstDeath()).isEqualTo(102);
  }

  @Test
  void detect_callsOneOrTwoDeathsASkirmish() {
    List<CombatEvent> events = new ArrayList<>(burst(100, 0, 5));
    events.add(death(103, 5));

    assertThat(detector.detect(events, PLAYERS))
        .extracting(FightDetector.Detected::type)
        .containsExactly(FightType.SKIRMISH);
  }

  @Test
  void detect_ignoresScatteredPokesThatNeverAddUpToABurst() {
    List<CombatEvent> events = new ArrayList<>();
    for (int t = 30; t < 120; t += 4) {
      events.add(damage(t, 0, 5, 60));
    }

    assertThat(detector.detect(events, PLAYERS)).isEmpty();
  }

  @Test
  void detect_findsAFightWhereNobodyDiedOnlyWhenBothSidesHitHardWithEnoughHeroes() {
    List<CombatEvent> both = new ArrayList<>();
    both.addAll(burst(200, 0, 5));
    both.addAll(burst(200.5, 1, 6));
    both.addAll(burst(201, 5, 0));
    both.addAll(burst(201.5, 6, 1));
    List<CombatEvent> oneSided = new ArrayList<>(burst(300, 0, 5));
    oneSided.addAll(burst(301, 0, 6));

    assertThat(detector.detect(both, PLAYERS))
        .extracting(FightDetector.Detected::type)
        .containsExactly(FightType.ENGAGEMENT);
    assertThat(detector.detect(oneSided, PLAYERS)).isEmpty();
  }

  @Test
  void detect_splitsFightsThatAreFarApartAndSkipsHeroesItDoesNotKnow() {
    List<CombatEvent> events = new ArrayList<>();
    events.addAll(burst(100, 0, 5));
    events.add(death(102, 5));
    events.addAll(burst(200, 1, 6));
    events.add(death(202, 6));
    events.add(death(203, 99));
    events.add(
        new CombatEvent(
            CombatEventKind.DEATH,
            150,
            "",
            "npc_dota_hero_stranger",
            "",
            CombatEvent.TARGET_HERO,
            0,
            0,
            0,
            0,
            0,
            0,
            0));

    assertThat(detector.detect(events, PLAYERS)).hasSize(2);
  }

  /** Heavy damage from {@code attacker} to {@code victim}, 800 within a second. */
  private static List<CombatEvent> burst(double start, int attacker, int victim) {
    return List.of(
        damage(start, attacker, victim, 300),
        damage(start + 0.5, attacker, victim, 300),
        damage(start + 1, attacker, victim, 300));
  }

  private static CombatEvent damage(double second, int attacker, int victim, int value) {
    return new CombatEvent(
        CombatEventKind.DAMAGE,
        second,
        "npc_dota_hero_h" + attacker,
        "npc_dota_hero_h" + victim,
        "",
        CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
        value,
        0,
        0,
        0,
        0,
        0,
        0);
  }

  private static CombatEvent death(double second, int victim) {
    return new CombatEvent(
        CombatEventKind.DEATH,
        second,
        "",
        "npc_dota_hero_h" + victim,
        "",
        CombatEvent.TARGET_HERO,
        0,
        0,
        0,
        0,
        0,
        0,
        0);
  }
}
