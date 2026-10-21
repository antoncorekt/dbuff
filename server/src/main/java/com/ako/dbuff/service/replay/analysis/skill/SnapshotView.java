package com.ako.dbuff.service.replay.analysis.skill;

import com.ako.dbuff.service.replay.CastSnapshot;
import com.ako.dbuff.service.replay.CastSnapshot.HeroPoint;
import com.ako.dbuff.service.replay.analysis.skill.SkillCasts.Cast;
import com.ako.dbuff.service.replay.analysis.skill.SkillContext.Hero;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads cast snapshots through the match roster; a hero missing from the roster has team 0. */
final class SnapshotView {
  static final double SNAPSHOT_MATCH_SECONDS = 0.05;

  private final SkillContext context;
  private final Map<Integer, Hero> roster = new HashMap<>();

  SnapshotView(SkillContext context) {
    this.context = context;
    context.match().heroes().forEach(h -> roster.putIfAbsent(h.playerId(), h));
  }

  Optional<HeroPoint> point(CastSnapshot frame, int playerId) {
    return frame.heroes().stream().filter(h -> h.playerId() == playerId).findFirst();
  }

  int teamOf(HeroPoint point) {
    return hero(point.playerId()).map(Hero::team).orElse(0);
  }

  boolean isEnemy(HeroPoint point) {
    int team = teamOf(point);
    return team != 0 && team != context.caster().team();
  }

  List<HeroPoint> witnesses(CastSnapshot frame) {
    int casterId = context.caster().playerId();
    int team = context.caster().team();
    return frame.heroes().stream()
        .filter(h -> h.alive() && h.playerId() != casterId && teamOf(h) == team)
        .toList();
  }

  boolean seenBy(HeroPoint enemy, List<HeroPoint> witnesses, double seenRange) {
    return witnesses.stream()
        .anyMatch(w -> Math.hypot(w.x() - enemy.x(), w.y() - enemy.y()) <= seenRange);
  }

  Optional<Hero> hero(int playerId) {
    return Optional.ofNullable(roster.get(playerId));
  }

  String nameOf(HeroPoint point) {
    return hero(point.playerId()).map(Hero::name).orElse("");
  }

  List<CastSnapshot> framesOf(List<CastSnapshot> snapshots, Cast cast) {
    int casterId = context.caster().playerId();
    return snapshots.stream()
        .filter(
            s ->
                s.ability().equals(cast.ability())
                    && s.casterId() == casterId
                    && Math.abs(s.castSecond() - cast.second()) <= SNAPSHOT_MATCH_SECONDS)
        .sorted(Comparator.comparingDouble(CastSnapshot::second))
        .toList();
  }
}
