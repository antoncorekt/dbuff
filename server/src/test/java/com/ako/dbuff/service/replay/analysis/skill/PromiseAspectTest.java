package com.ako.dbuff.service.replay.analysis.skill;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.constant.data.AbilityAttribute;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
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

class PromiseAspectTest {
  private static final String SKILL = "oracle_false_promise";
  private static final String OWNER = "npc_dota_hero_sniper";
  private static final String ALLY = "npc_dota_hero_axe";
  private static final String OTHER_ALLY = "npc_dota_hero_lion";
  private static final String ENEMY = "npc_dota_hero_pudge";
  private static final int DIRE = 3;
  private static final int RADIANT = 2;
  private static final int HEROES = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;
  private static final int ALLY_HP = 2000;

  private final List<CombatEvent> events = new ArrayList<>();
  private final List<ReplayStateChangeDomain> changes = new ArrayList<>();
  private final List<FightDomain> fights = new ArrayList<>();
  private final PromiseAspect aspect = new PromiseAspect();

  private static CombatEvent event(
      CombatEventKind kind,
      double second,
      String attacker,
      String target,
      String inflictor,
      int value,
      int health,
      int assistMask) {
    return new CombatEvent(
        kind, second, attacker, target, inflictor, HEROES, value, health, 0, 0, 0, 0, assistMask);
  }

  private void cast(double second, String target) {
    events.add(event(CombatEventKind.ABILITY, second, OWNER, target, SKILL, 0, 0, 0));
  }

  private void timerEnds(double second, String target) {
    events.add(
        event(
            CombatEventKind.MODIFIER_REMOVE,
            second,
            OWNER,
            target,
            PromiseAspect.TIMER_MODIFIER,
            0,
            0,
            0));
  }

  private void hit(double second, String target, int value) {
    events.add(event(CombatEventKind.DAMAGE, second, ENEMY, target, "attack", value, 500, 0));
  }

  private void death(double second, String target, String killer, int assistMask) {
    events.add(event(CombatEventKind.DEATH, second, killer, target, "", 0, 0, assistMask));
  }

  private static ReplayPlayerTrackDomain track(int hpPercent, int x, int y) {
    int[] percent = new int[200];
    int[] flags = new int[200];
    int[] xs = new int[200];
    int[] ys = new int[200];
    int[] maxHp = new int[200];
    Arrays.fill(percent, hpPercent);
    Arrays.fill(flags, 1);
    Arrays.fill(xs, x);
    Arrays.fill(ys, y);
    Arrays.fill(maxHp, ALLY_HP);
    return ReplayPlayerTrackDomain.builder()
        .startSeconds(0)
        .stepSeconds(5)
        .sampleCount(200)
        .x(xs)
        .y(ys)
        .hpPercent(percent)
        .flags(flags)
        .maxHp(maxHp)
        .build();
  }

  private Hero hero(long slot, String name, int team, int x) {
    ReplayPlayerTrackDomain track = track(80, x, 0);
    return new Hero(slot, (int) slot * 2, name, team, track, new HeroHealth(name, events, track));
  }

  private ReplayStateChangeDomain change(StateChangeKind kind, double second) {
    return ReplayStateChangeDomain.builder()
        .kind(kind.name())
        .name(SKILL)
        .gameSeconds(second)
        .build();
  }

  private void learned(int level) {
    ReplayStateChangeDomain row = change(StateChangeKind.ABILITY_LEVEL, 10);
    row.setAbilityLevel(level);
    changes.add(row);
  }

  private void cooldownUntil(double started, double ready) {
    ReplayStateChangeDomain row = change(StateChangeKind.COOLDOWN, started);
    row.setReadyAtSeconds(ready);
    changes.add(row);
  }

  private List<Outcome> judge() {
    List<Hero> heroes =
        List.of(
            hero(0, OWNER, RADIANT, 0),
            hero(1, ALLY, RADIANT, 100),
            hero(2, OTHER_ALLY, RADIANT, 5000),
            hero(5, ENEMY, DIRE, 200));
    AbilityConstant constant =
        AbilityConstant.builder()
            .attrib(
                List.of(
                    new AbilityAttribute("duration", List.of("7", "8.5", "10")),
                    new AbilityAttribute("abilitycastrange", List.of("700", "800", "900"))))
            .build();
    Match match =
        new Match(
            heroes,
            events,
            fights,
            name -> SKILL.equals(name) ? Optional.of(constant) : Optional.empty(),
            List::of);
    SkillCatalogEntry entry =
        new SkillCatalogEntry("false_promise", List.of(SKILL), List.of("promise"), Map.of());
    return aspect.judge(
        new SkillContext(
            match,
            entry,
            heroes.getFirst(),
            SkillCasts.of(OWNER, events, changes, List.of(SKILL))));
  }

  private List<String> outcomes() {
    return judge().stream().map(Outcome::outcome).toList();
  }

  @Test
  void aPromiseOverHeavyDamageThatTheAllyLivedThroughIsASave() {
    learned(2);
    cast(100, ALLY);
    timerEnds(108.5, ALLY);
    hit(108.5, ALLY, 1700);

    assertThat(outcomes()).containsExactly(PromiseAspect.SAVE);
  }

  @Test
  void anAllyWhoDiedAfterDealingRealDamageIsAnImpact() {
    learned(1);
    cast(100, ALLY);
    timerEnds(107, ALLY);
    events.add(event(CombatEventKind.DAMAGE, 103, ALLY, ENEMY, "attack", 600, 100, 0));
    hit(107, ALLY, 2500);
    death(107.1, ALLY, ENEMY, 0);

    List<Outcome> result = judge();

    assertThat(result).extracting(Outcome::outcome).containsExactly(PromiseAspect.IMPACT);
    assertThat(result.getFirst().numbers()).containsEntry("damageDealt", 600.0);
  }

  @Test
  void anAssistDuringThePromiseIsAnImpactToo() {
    learned(1);
    cast(100, ALLY);
    timerEnds(107, ALLY);
    death(104, ENEMY, OTHER_ALLY, 1 << 1);
    hit(107, ALLY, 2500);
    death(107.1, ALLY, ENEMY, 0);

    assertThat(outcomes()).containsExactly(PromiseAspect.IMPACT);
  }

  @Test
  void aCastWithoutDangerIsNotCounted() {
    learned(1);
    cast(100, ALLY);
    timerEnds(107, ALLY);
    hit(107, ALLY, 50);

    assertThat(outcomes()).containsExactly(PromiseAspect.NOT_COUNTED);
  }

  @Test
  void aDeathWithoutImpactIsNotCounted() {
    learned(1);
    cast(100, ALLY);
    timerEnds(107, ALLY);
    hit(107, ALLY, 2500);
    death(107.1, ALLY, ENEMY, 0);

    assertThat(outcomes()).containsExactly(PromiseAspect.NOT_COUNTED);
  }

  @Test
  void aFightMakesThirtyPercentOfMaxHealthDangerousEvenAboveCurrentHealth() {
    learned(1);
    fights.add(FightDomain.builder().startSeconds(95.0).endSeconds(120.0).build());
    cast(100, ALLY);
    timerEnds(107, ALLY);
    hit(107, ALLY, 700);

    assertThat(outcomes()).containsExactly(PromiseAspect.SAVE);
  }

  @Test
  void theWindowFallsBackToTheConstantsDurationWithoutTheTimerModifier() {
    learned(3);
    cast(100, ALLY);
    hit(109.5, ALLY, 1700);

    assertThat(outcomes()).containsExactly(PromiseAspect.SAVE);
  }

  @Test
  void anAllyDeathInRangeWithTheSkillReadyIsAMissedChance() {
    learned(1);
    death(200, ALLY, ENEMY, 0);

    List<Outcome> result = judge();

    assertThat(result).extracting(Outcome::outcome).containsExactly(PromiseAspect.MISSED);
    assertThat(result.getFirst().cast()).isFalse();
  }

  @Test
  void aDeathOutOfRangeOrOnCooldownOrBeforeLearningIsNoMissedChance() {
    ReplayStateChangeDomain late = change(StateChangeKind.ABILITY_LEVEL, 150);
    late.setAbilityLevel(1);
    changes.add(late);
    death(50, ALLY, ENEMY, 0);
    death(200, OTHER_ALLY, ENEMY, 0);
    cooldownUntil(190, 250);
    death(200, ALLY, ENEMY, 0);

    assertThat(outcomes()).isEmpty();
  }

  @Test
  void aDeathAfterTheCooldownEndedIsAMissedChanceAgain() {
    learned(1);
    cooldownUntil(190, 250);
    death(300, ALLY, ENEMY, 0);

    assertThat(outcomes()).containsExactly(PromiseAspect.MISSED);
  }

  @Test
  void anEnemyDeathIsNeverAMissedChance() {
    learned(1);
    death(200, ENEMY, ALLY, 0);

    assertThat(outcomes()).isEmpty();
  }
}
