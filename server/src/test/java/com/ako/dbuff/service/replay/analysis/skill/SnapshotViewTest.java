package com.ako.dbuff.service.replay.analysis.skill;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.replay.CastSnapshot;
import com.ako.dbuff.service.replay.CastSnapshot.HeroPoint;
import com.ako.dbuff.service.replay.analysis.health.HeroHealth;
import com.ako.dbuff.service.replay.analysis.skill.SkillCasts.Cast;
import com.ako.dbuff.service.replay.analysis.skill.SkillContext.Hero;
import com.ako.dbuff.service.replay.analysis.skill.SkillContext.Match;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotViewTest {
  private static final String SKILL = "pudge_meat_hook";
  private static final int CASTER_ID = 0;
  private static final int ALLY_ID = 2;
  private static final int ENEMY_ID = 10;
  private static final int STRANGER_ID = 99;
  private static final double CAST = 100;

  private final List<Hero> heroes =
      List.of(
          hero(CASTER_ID, "npc_dota_hero_pudge", 2),
          hero(ALLY_ID, "npc_dota_hero_axe", 2),
          hero(ENEMY_ID, "npc_dota_hero_lion", 3));
  private final SnapshotView view = new SnapshotView(context());

  private static Hero hero(int id, String name, int team) {
    return new Hero(id, id, name, team, null, new HeroHealth(name, List.of(), null));
  }

  private SkillContext context() {
    Match match =
        new Match(
            heroes, List.of(), List.of(), name -> Optional.<AbilityConstant>empty(), List::of);
    SkillCatalogEntry entry = new SkillCatalogEntry("hook", List.of(SKILL), List.of(), Map.of());
    return new SkillContext(
        match,
        entry,
        heroes.getFirst(),
        SkillCasts.of("npc_dota_hero_pudge", List.of(), List.of(), List.of(SKILL)));
  }

  private static HeroPoint at(int id) {
    return new HeroPoint(id, 0, 0, true);
  }

  private static CastSnapshot frame(
      String ability, double castSecond, double second, int casterId, HeroPoint... points) {
    return new CastSnapshot(ability, castSecond, second, casterId, 0, List.of(points));
  }

  @Test
  void teamIsKnownForARosterHeroAndZeroOtherwise() {
    assertThat(view.teamOf(at(ENEMY_ID))).isEqualTo(3);
    assertThat(view.teamOf(at(STRANGER_ID))).isZero();
    assertThat(view.nameOf(at(ALLY_ID))).isEqualTo("npc_dota_hero_axe");
    assertThat(view.nameOf(at(STRANGER_ID))).isEmpty();
  }

  @Test
  void onlyAKnownOtherTeamHeroIsAnEnemy() {
    assertThat(view.isEnemy(at(ENEMY_ID))).isTrue();
    assertThat(view.isEnemy(at(ALLY_ID))).isFalse();
    assertThat(view.isEnemy(at(STRANGER_ID))).isFalse();
  }

  @Test
  void aHeroMissingFromAFrameHasNoPoint() {
    CastSnapshot frame = frame(SKILL, CAST, CAST, CASTER_ID, at(CASTER_ID));

    assertThat(view.point(frame, CASTER_ID)).isPresent();
    assertThat(view.point(frame, ENEMY_ID)).isEmpty();
    assertThat(view.hero(ENEMY_ID)).isPresent();
    assertThat(view.hero(STRANGER_ID)).isEmpty();
  }

  @Test
  void framesAreFilteredByAbilityCasterAndCastSecondAndSorted() {
    CastSnapshot late = frame(SKILL, CAST, CAST + 2, CASTER_ID, at(CASTER_ID));
    CastSnapshot first = frame(SKILL, CAST + 0.04, CAST, CASTER_ID, at(CASTER_ID));
    CastSnapshot otherAbility = frame("tusk_ice_shards", CAST, CAST, CASTER_ID);
    CastSnapshot otherCaster = frame(SKILL, CAST, CAST, ALLY_ID);
    CastSnapshot otherCast = frame(SKILL, CAST + 0.06, CAST, CASTER_ID);

    List<CastSnapshot> frames =
        view.framesOf(
            List.of(late, otherAbility, otherCaster, otherCast, first),
            new Cast(SKILL, CAST, "", 1));

    assertThat(frames).containsExactly(first, late);
  }
}
