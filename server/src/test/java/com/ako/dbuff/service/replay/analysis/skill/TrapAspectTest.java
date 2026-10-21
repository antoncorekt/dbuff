package com.ako.dbuff.service.replay.analysis.skill;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.replay.CastSnapshot;
import com.ako.dbuff.service.replay.CastSnapshot.HeroPoint;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.health.HeroHealth;
import com.ako.dbuff.service.replay.analysis.skill.SkillAspect.Outcome;
import com.ako.dbuff.service.replay.analysis.skill.SkillContext.Hero;
import com.ako.dbuff.service.replay.analysis.skill.SkillContext.Match;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrapAspectTest {
  private static final String SKILL = "tusk_ice_shards";
  private static final String TUSK = "npc_dota_hero_tusk";
  private static final String ALLY = "npc_dota_hero_axe";
  private static final String ENEMY = "npc_dota_hero_lion";
  private static final String OTHER = "npc_dota_hero_sven";
  private static final int RADIANT = 2;
  private static final int DIRE = 3;
  private static final int HEROES = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;
  private static final int TUSK_ID = 0;
  private static final int ALLY_ID = 2;
  private static final int ENEMY_ID = 10;
  private static final int OTHER_ID = 12;
  private static final double CAST = 100;
  private static final double FORM = 101;
  private static final int CENTRE_X = 1200;

  private final List<CombatEvent> events = new ArrayList<>();
  private final List<CastSnapshot> snapshots = new ArrayList<>();
  private final TrapAspect aspect = new TrapAspect();

  private static HeroPoint at(int id, int x, int y) {
    return new HeroPoint(id, x, y, true);
  }

  private void cast() {
    events.add(
        new CombatEvent(
            CombatEventKind.ABILITY,
            CAST,
            TUSK,
            "dota_unknown",
            SKILL,
            HEROES,
            0,
            500,
            0,
            0,
            0,
            0,
            0));
  }

  private void frame(double second, int facing, HeroPoint... heroes) {
    snapshots.add(new CastSnapshot(SKILL, CAST, second, TUSK_ID, facing, List.of(heroes)));
  }

  private void start(HeroPoint... others) {
    List<HeroPoint> all = new ArrayList<>(List.of(at(TUSK_ID, 0, 0)));
    all.addAll(List.of(others));
    frame(CAST, 0, all.toArray(new HeroPoint[0]));
  }

  private void enemyAt(double second, int x, int y) {
    frame(second, 0, at(TUSK_ID, 0, 0), at(ENEMY_ID, x, y));
  }

  private Hero hero(long slot, int id, String name, int team) {
    return new Hero(slot, id, name, team, null, new HeroHealth(name, events, null));
  }

  private List<Outcome> judge() {
    List<Hero> heroes =
        List.of(
            hero(0, TUSK_ID, TUSK, RADIANT),
            hero(1, ALLY_ID, ALLY, RADIANT),
            hero(5, ENEMY_ID, ENEMY, DIRE),
            hero(6, OTHER_ID, OTHER, DIRE));
    Match match =
        new Match(
            heroes, events, List.of(), name -> Optional.<AbilityConstant>empty(), () -> snapshots);
    SkillCatalogEntry entry =
        new SkillCatalogEntry("ice_shards", List.of(SKILL), List.of("trap"), Map.of());
    return aspect.judge(
        new SkillContext(
            match,
            entry,
            heroes.getFirst(),
            SkillCasts.of(TUSK, events, List.of(), List.of(SKILL))));
  }

  private List<String> outcomes() {
    return judge().stream().map(Outcome::outcome).toList();
  }

  @Test
  void anEnemyWhoStaysInTheRingIsTrapped() {
    cast();
    start(at(ENEMY_ID, CENTRE_X, 0));
    for (int i = 0; i < 10; i++) {
      enemyAt(FORM + i * 0.4, CENTRE_X + 50, 20);
    }

    List<Outcome> result = judge();

    assertThat(result).extracting(Outcome::outcome).containsExactly(TrapAspect.TRAP);
    assertThat(result.getFirst().target()).isEqualTo(ENEMY);
  }

  @Test
  void aKillOfATrappedEnemyIsNoted() {
    cast();
    start(at(ENEMY_ID, CENTRE_X, 0));
    for (int i = 0; i < 10; i++) {
      enemyAt(FORM + i * 0.4, CENTRE_X, 0);
    }
    events.add(
        new CombatEvent(
            CombatEventKind.DEATH, FORM + 3, TUSK, ENEMY, "", HEROES, 0, 0, 0, 0, 0, 0, 0));

    assertThat(judge().getFirst().numbers()).containsEntry("killed", 1.0);
  }

  @Test
  void anEnemyWhoWalksOutSidewaysIsAMiss() {
    cast();
    start(at(ENEMY_ID, CENTRE_X, 0), at(ALLY_ID, 200, 0));
    enemyAt(FORM, CENTRE_X, 0);
    for (int i = 1; i < 10; i++) {
      enemyAt(FORM + i * 0.4, CENTRE_X, 800);
    }

    List<Outcome> result = judge();

    assertThat(result).extracting(Outcome::outcome).containsExactly(TrapAspect.MISS);
    assertThat(result.getFirst().target()).isEqualTo(ENEMY);
  }

  @Test
  void anEnemyWhoOnlyRetreatsTowardsTheCasterIsTrapped() {
    cast();
    start(at(ENEMY_ID, CENTRE_X, 0));
    enemyAt(FORM, CENTRE_X, 0);
    for (int i = 1; i < 10; i++) {
      enemyAt(FORM + i * 0.4, 400, 0);
    }

    assertThat(outcomes()).containsExactly(TrapAspect.TRAP);
  }

  @Test
  void anEnemyWhoRunsAwayFromTheCasterIsNotTrapped() {
    cast();
    start(at(ENEMY_ID, CENTRE_X, 0), at(ALLY_ID, 200, 0));
    enemyAt(FORM, CENTRE_X, 0);
    for (int i = 1; i < 10; i++) {
      enemyAt(FORM + i * 0.4, 2600, 0);
    }

    assertThat(outcomes()).containsExactly(TrapAspect.MISS);
  }

  @Test
  void noEnemyNearIsNotCounted() {
    cast();
    start(at(ENEMY_ID, 6000, 0), at(ALLY_ID, 200, 0));
    for (int i = 0; i < 10; i++) {
      enemyAt(FORM + i * 0.4, 6000, 0);
    }

    assertThat(outcomes()).containsExactly(TrapAspect.NOT_COUNTED);
  }

  @Test
  void anEnemyOnlyTheCasterSeesIsNotAMiss() {
    cast();
    start(at(ENEMY_ID, 1000, 700));
    enemyAt(FORM, 1000, 700);

    assertThat(outcomes()).containsExactly(TrapAspect.NOT_COUNTED);
  }

  @Test
  void shardDamageAloneIsNotATrap() {
    cast();
    start(at(ENEMY_ID, CENTRE_X, 0), at(ALLY_ID, 200, 0));
    events.add(
        new CombatEvent(
            CombatEventKind.DAMAGE, FORM, TUSK, ENEMY, SKILL, HEROES, 80, 500, 0, 0, 0, 0, 0));
    enemyAt(FORM, CENTRE_X, 900);
    enemyAt(FORM + 1, CENTRE_X, 1500);

    assertThat(outcomes()).containsExactly(TrapAspect.MISS);
  }

  @Test
  void aHeroMissingFromTheFormingFrameIsUnknownNotTrapped() {
    cast();
    start(at(ENEMY_ID, CENTRE_X, 0));
    frame(FORM, 0, at(TUSK_ID, 0, 0));
    frame(FORM + 1, 0, at(TUSK_ID, 0, 0));

    assertThat(outcomes()).containsExactly(TrapAspect.NOT_COUNTED);
  }

  @Test
  void aCastWithoutSnapshotsIsNotCounted() {
    cast();

    List<Outcome> result = judge();

    assertThat(result).extracting(Outcome::outcome).containsExactly(TrapAspect.NOT_COUNTED);
    assertThat(result.getFirst().numbers()).containsEntry("noSnapshot", 1.0);
  }

  @Test
  void aCastWithoutFacingIsNotCounted() {
    cast();
    frame(CAST, CastSnapshot.NO_FACING, at(TUSK_ID, 0, 0), at(ENEMY_ID, CENTRE_X, 0));
    enemyAt(FORM, CENTRE_X, 0);

    assertThat(outcomes()).containsExactly(TrapAspect.NOT_COUNTED);
  }

  @Test
  void aCastWithNoFrameAfterTheFormingIsNotCounted() {
    cast();
    start(at(ENEMY_ID, CENTRE_X, 0));

    assertThat(outcomes()).containsExactly(TrapAspect.NOT_COUNTED);
  }

  @Test
  void aDeadEnemyInTheRingIsNotTrapped() {
    cast();
    start(at(ENEMY_ID, CENTRE_X, 0));
    frame(FORM, 0, at(TUSK_ID, 0, 0), new HeroPoint(ENEMY_ID, CENTRE_X, 0, false));

    assertThat(outcomes()).containsExactly(TrapAspect.NOT_COUNTED);
  }
}
