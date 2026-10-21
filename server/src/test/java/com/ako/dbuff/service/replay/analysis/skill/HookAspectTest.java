package com.ako.dbuff.service.replay.analysis.skill;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.constant.data.AbilityAttribute;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HookAspectTest {
  private static final String SKILL = "pudge_meat_hook";
  private static final String PUDGE = "npc_dota_hero_pudge";
  private static final String ALLY = "npc_dota_hero_axe";
  private static final String ENEMY = "npc_dota_hero_lion";
  private static final String FAR_ENEMY = "npc_dota_hero_sven";
  private static final int RADIANT = 2;
  private static final int DIRE = 3;
  private static final int HEROES = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;
  private static final int PUDGE_ID = 0;
  private static final int ALLY_ID = 2;
  private static final int ENEMY_ID = 10;
  private static final int FAR_ENEMY_ID = 12;
  private static final double CAST = 100;
  private static final int MAX_HP = 2000;

  private final List<CombatEvent> events = new ArrayList<>();
  private final List<FightDomain> fights = new ArrayList<>();
  private final List<CastSnapshot> snapshots = new ArrayList<>();
  private final HookAspect aspect = new HookAspect();

  private static CombatEvent event(
      CombatEventKind kind,
      double second,
      String attacker,
      String target,
      String inflictor,
      int value) {
    return new CombatEvent(
        kind, second, attacker, target, inflictor, HEROES, value, 500, 0, 0, 0, 0, 0);
  }

  private void cast() {
    events.add(event(CombatEventKind.ABILITY, CAST, PUDGE, "dota_unknown", SKILL, 0));
  }

  private void hookHit(String target, double after) {
    events.add(
        event(
            CombatEventKind.MODIFIER_ADD,
            CAST + after,
            PUDGE,
            target,
            HookAspect.HOOK_MODIFIER,
            0));
    events.add(event(CombatEventKind.DAMAGE, CAST + after, PUDGE, target, SKILL, 200));
  }

  private void damage(double second, String target, int value) {
    events.add(event(CombatEventKind.DAMAGE, second, ENEMY, target, "attack", value));
  }

  private void frame(int facing, HeroPoint... heroes) {
    snapshots.add(new CastSnapshot(SKILL, CAST, CAST, PUDGE_ID, facing, List.of(heroes)));
  }

  private static HeroPoint at(int id, int x, int y) {
    return new HeroPoint(id, x, y, true);
  }

  private Hero hero(long slot, int id, String name, int team) {
    int[] values = new int[200];
    int[] flags = new int[200];
    int[] percent = new int[200];
    int[] maxHp = new int[200];
    Arrays.fill(flags, 1);
    Arrays.fill(percent, 80);
    Arrays.fill(maxHp, MAX_HP);
    ReplayPlayerTrackDomain track =
        ReplayPlayerTrackDomain.builder()
            .startSeconds(0)
            .stepSeconds(5)
            .sampleCount(200)
            .x(values)
            .y(values)
            .hpPercent(percent)
            .flags(flags)
            .maxHp(maxHp)
            .build();
    return new Hero(slot, id, name, team, track, new HeroHealth(name, events, track));
  }

  private List<Outcome> judge(Optional<AbilityConstant> constant) {
    List<Hero> heroes =
        List.of(
            hero(0, PUDGE_ID, PUDGE, RADIANT),
            hero(1, ALLY_ID, ALLY, RADIANT),
            hero(5, ENEMY_ID, ENEMY, DIRE),
            hero(6, FAR_ENEMY_ID, FAR_ENEMY, DIRE));
    Match match = new Match(heroes, events, fights, name -> constant, () -> snapshots);
    SkillCatalogEntry entry =
        new SkillCatalogEntry("meat_hook", List.of(SKILL), List.of("hook"), Map.of());
    return aspect.judge(
        new SkillContext(
            match,
            entry,
            heroes.getFirst(),
            SkillCasts.of(PUDGE, events, List.of(), List.of(SKILL))));
  }

  private List<Outcome> judge() {
    AbilityConstant constant =
        AbilityConstant.builder()
            .attrib(
                List.of(
                    new AbilityAttribute("hook_speed", List.of("1600")),
                    new AbilityAttribute("abilitycastrange", List.of("1300"))))
            .build();
    return judge(Optional.of(constant));
  }

  private List<String> outcomes() {
    return judge().stream().map(Outcome::outcome).toList();
  }

  @Test
  void aHitOnAnEnemyTheTeamSawIsASeenHit() {
    cast();
    hookHit(ENEMY, 0.5);
    frame(0, at(PUDGE_ID, 0, 0), at(ENEMY_ID, 900, 0));

    assertThat(outcomes()).containsExactly(HookAspect.SEEN_HIT);
  }

  @Test
  void aHitOnAnEnemyFarFromEveryAllyIsADarkHit() {
    cast();
    hookHit(ENEMY, 0.5);
    frame(0, at(PUDGE_ID, 0, 0), at(ENEMY_ID, 2500, 0));

    assertThat(outcomes()).containsExactly(HookAspect.DARK_HIT);
  }

  @Test
  void aHitWithoutSnapshotsIsADarkHit() {
    cast();
    hookHit(ENEMY, 0.5);

    List<Outcome> result = judge();

    assertThat(result).extracting(Outcome::outcome).containsExactly(HookAspect.DARK_HIT);
    assertThat(result.getFirst().numbers()).containsEntry("noSnapshot", 1.0);
  }

  @Test
  void aMissAimedAtASeenEnemyIsASeenMiss() {
    cast();
    frame(0, at(PUDGE_ID, 0, 0), at(ENEMY_ID, 1000, 50));

    assertThat(outcomes()).containsExactly(HookAspect.SEEN_MISS);
  }

  @Test
  void aMissWithTheEnemyOutsideTheAimAngleIsNotCounted() {
    cast();
    frame(0, at(PUDGE_ID, 0, 0), at(ENEMY_ID, 0, 1000));

    assertThat(outcomes()).containsExactly(HookAspect.NOT_COUNTED);
  }

  @Test
  void anEnemyOutOfSightIsNeverAimedAt() {
    cast();
    frame(0, at(PUDGE_ID, 0, 0), at(FAR_ENEMY_ID, 1400, 0), at(ENEMY_ID, 3500, 0));

    assertThat(outcomes()).containsExactly(HookAspect.SEEN_MISS);
    snapshots.clear();
    frame(0, at(PUDGE_ID, 0, 0), at(ENEMY_ID, 3500, 0));
    assertThat(outcomes()).containsExactly(HookAspect.NOT_COUNTED);
  }

  @Test
  void aMissWithoutFacingIsNotCounted() {
    cast();
    frame(CastSnapshot.NO_FACING, at(PUDGE_ID, 0, 0), at(ENEMY_ID, 1000, 0));

    assertThat(outcomes()).containsExactly(HookAspect.NOT_COUNTED);
  }

  @Test
  void aHeroMissingFromTheFrameIsUnknownNotSeen() {
    cast();
    hookHit(ENEMY, 0.5);
    frame(0, at(PUDGE_ID, 0, 0));

    assertThat(outcomes()).containsExactly(HookAspect.DARK_HIT);
  }

  @Test
  void aHookOnAnAllyUnderHeavyDamageAndLowHealthIsASave() {
    cast();
    hookHit(ALLY, 0.5);
    damage(98, ALLY, 1900);
    frame(0, at(PUDGE_ID, 0, 0), at(ALLY_ID, 900, 0));

    assertThat(outcomes()).containsExactly(HookAspect.ALLY_SAVE);
  }

  @Test
  void anAllyInAFightUnderHeavyDamageIsASaveEvenAboveLowHealth() {
    cast();
    hookHit(ALLY, 0.5);
    damage(99, ALLY, 700);
    fights.add(FightDomain.builder().startSeconds(90.0).endSeconds(120.0).build());
    frame(0, at(PUDGE_ID, 0, 0), at(ALLY_ID, 900, 0));

    assertThat(outcomes()).containsExactly(HookAspect.ALLY_SAVE);
  }

  @Test
  void anAllyHookWithoutDamageIsNotCounted() {
    cast();
    hookHit(ALLY, 0.5);
    frame(0, at(PUDGE_ID, 0, 0), at(ALLY_ID, 900, 0));

    assertThat(outcomes()).containsExactly(HookAspect.NOT_COUNTED);
  }

  @Test
  void anAllyWhoDiesAfterTheHookIsNotASave() {
    cast();
    hookHit(ALLY, 0.5);
    damage(99, ALLY, 1900);
    events.add(event(CombatEventKind.DEATH, 104, ENEMY, ALLY, "", 0));

    assertThat(outcomes()).containsExactly(HookAspect.NOT_COUNTED);
  }

  @Test
  void aHookOnACreepIsNotCounted() {
    cast();
    events.add(
        new CombatEvent(
            CombatEventKind.MODIFIER_ADD,
            CAST + 0.5,
            PUDGE,
            "npc_dota_creep",
            HookAspect.HOOK_MODIFIER,
            CombatEvent.ATTACKER_HERO,
            0,
            0,
            0,
            0,
            0,
            0,
            0));
    frame(0, at(PUDGE_ID, 0, 0), at(ENEMY_ID, 3500, 0));

    assertThat(outcomes()).containsExactly(HookAspect.NOT_COUNTED);
  }

  @Test
  void aHitAfterTheFlightWindowIsNotThisCasts() {
    cast();
    hookHit(ENEMY, 3);
    frame(0, at(PUDGE_ID, 0, 0), at(ENEMY_ID, 1000, 0));

    assertThat(outcomes()).containsExactly(HookAspect.SEEN_MISS);
  }

  @Test
  void withoutConstantsTheWindowFallsBackToOneAndAHalfSeconds() {
    cast();
    hookHit(ENEMY, 1.4);
    frame(0, at(PUDGE_ID, 0, 0), at(ENEMY_ID, 900, 0));

    assertThat(judge(Optional.empty()))
        .extracting(Outcome::outcome)
        .containsExactly(HookAspect.SEEN_HIT);
  }

  @Test
  void accuracyIsSeenHitsOverSeenHitsAndMisses() {
    assertThat(aspect.derivedFigures(Map.of(HookAspect.SEEN_HIT, 7, HookAspect.SEEN_MISS, 5)))
        .containsEntry(HookAspect.ACCURACY, 58);
    assertThat(aspect.derivedFigures(Map.of(HookAspect.DARK_HIT, 2))).isEmpty();
  }
}
