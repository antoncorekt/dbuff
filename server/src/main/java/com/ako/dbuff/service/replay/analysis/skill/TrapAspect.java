package com.ako.dbuff.service.replay.analysis.skill;

import com.ako.dbuff.service.replay.CastSnapshot;
import com.ako.dbuff.service.replay.CastSnapshot.HeroPoint;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.skill.SkillCasts.Cast;
import com.ako.dbuff.service.replay.analysis.skill.SkillContext.Hero;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Judges a skill that raises a barrier of shards where it is aimed (Ice Shards). The replay has no
 * shard unit and the combat log has no cast location, so the centre is the mean position of the
 * enemies the shards hurt as they formed, else the caster's position at the cast plus his facing
 * times the cast range. An enemy is trapped when he stood in the ring as it formed and then stayed
 * near it for most of the observed time, or only ever moved back towards the caster. Shard damage
 * alone is never a hit. A hero missing from a frame is unknown, not absent.
 */
@Component
public class TrapAspect implements SkillAspect {
  public static final String NAME = "trap";
  public static final String TRAP = "TRAP";
  public static final String MISS = "MISS";
  public static final String NOT_COUNTED = "NOT_COUNTED";

  private static final String RANGE = "abilitycastrange";
  private static final String RADIUS = "shard_width";
  private static final String SPEED = "shard_speed";
  private static final String DURATION = "shard_duration";
  private static final String SEEN_RANGE = "seenRange";
  private static final String FALLBACK_RANGE = "fallbackShardRange";
  private static final String FALLBACK_RADIUS = "fallbackShardRadius";
  private static final String FALLBACK_SPEED = "fallbackShardSpeed";
  private static final String FALLBACK_SECONDS = "fallbackShardSeconds";
  private static final String ENTER_MARGIN = "trapEnterMargin";
  private static final String STAY_MARGIN = "trapStayMargin";
  private static final String STAY_SHARE = "trapStayShare";
  private static final String RETREAT_DEGREES = "trapRetreatDegrees";
  private static final String KILL_SECONDS = "trapKillSeconds";
  private static final String CENTRE_WINDOW = "trapCentreWindowSeconds";

  private static final Set<String> PARAMETERS =
      Set.of(
          SEEN_RANGE,
          FALLBACK_RANGE,
          FALLBACK_RADIUS,
          FALLBACK_SPEED,
          FALLBACK_SECONDS,
          ENTER_MARGIN,
          STAY_MARGIN,
          STAY_SHARE,
          RETREAT_DEGREES,
          KILL_SECONDS,
          CENTRE_WINDOW);

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Set<String> parameterNames() {
    return PARAMETERS;
  }

  @Override
  public List<String> outcomes() {
    return List.of(TRAP, MISS, NOT_COUNTED);
  }

  @Override
  public boolean needsSnapshots() {
    return true;
  }

  @Override
  public List<Outcome> judge(SkillContext context) {
    List<CastSnapshot> snapshots = context.match().snapshots().get();
    SnapshotView view = new SnapshotView(context);
    List<Outcome> out = new ArrayList<>();
    for (Cast cast : context.casts().casts()) {
      out.add(judgeCast(context, view, cast, view.framesOf(snapshots, cast)));
    }
    return out;
  }

  private record Ring(double x, double y, double radius, double formSecond, double endSecond) {}

  private Outcome judgeCast(
      SkillContext context, SnapshotView view, Cast cast, List<CastSnapshot> frames) {
    Hero caster = context.caster();
    Map<String, Double> numbers = new LinkedHashMap<>();
    numbers.put("level", (double) cast.level());
    Optional<HeroPoint> from =
        frames.isEmpty() ? Optional.empty() : view.point(frames.getFirst(), caster.playerId());
    int facing = frames.isEmpty() ? CastSnapshot.NO_FACING : frames.getFirst().facing();
    if (from.isEmpty() || facing == CastSnapshot.NO_FACING) {
      numbers.put("noSnapshot", 1.0);
      return new Outcome(cast.second(), "", NOT_COUNTED, true, numbers);
    }
    Ring ring = ring(context, cast, from.get(), facing);
    Optional<Ring> damaged = centreFromDamage(context, view, frames, cast, ring);
    if (damaged.isPresent()) {
      ring = damaged.get();
      numbers.put("centreFromDamage", 1.0);
    }
    numbers.put("radius", ring.radius());

    List<HeroPoint> trapped = trapped(context, view, frames, from.get(), ring);
    if (!trapped.isEmpty()) {
      List<String> names = trapped.stream().map(h -> view.nameOf(h)).toList();
      numbers.put("trapped", (double) trapped.size());
      numbers.put("killed", (double) kills(context, names, ring));
      return new Outcome(cast.second(), String.join(", ", names), TRAP, true, numbers);
    }
    Optional<Hero> near = seenNear(context, view, frames.getFirst(), from.get(), ring, numbers);
    return near.map(h -> new Outcome(cast.second(), h.name(), MISS, true, numbers))
        .orElseGet(() -> new Outcome(cast.second(), "", NOT_COUNTED, true, numbers));
  }

  private Ring ring(SkillContext context, Cast cast, HeroPoint from, int facing) {
    double range =
        context
            .attribute(cast.ability(), RANGE, cast.level())
            .orElse(context.parameter(FALLBACK_RANGE, 1200));
    double radius =
        context
            .attribute(cast.ability(), RADIUS, cast.level())
            .orElse(context.parameter(FALLBACK_RADIUS, 200));
    double speed = positive(context.attribute(cast.ability(), SPEED, cast.level()));
    speed = speed > 0 ? speed : context.parameter(FALLBACK_SPEED, 1200);
    double duration = positive(context.attribute(cast.ability(), DURATION, cast.level()));
    duration = duration > 0 ? duration : context.parameter(FALLBACK_SECONDS, 7);
    double form = cast.second() + range / speed;
    double yaw = Math.toRadians(facing);
    return new Ring(
        from.x() + Math.cos(yaw) * range,
        from.y() + Math.sin(yaw) * range,
        radius,
        form,
        form + duration);
  }

  private Optional<Ring> centreFromDamage(
      SkillContext context, SnapshotView view, List<CastSnapshot> frames, Cast cast, Ring ring) {
    double window = context.parameter(CENTRE_WINDOW, 0.5);
    String caster = context.caster().name();
    Set<String> hurt =
        context.match().events().stream()
            .filter(
                e ->
                    e.kind() == CombatEventKind.DAMAGE
                        && caster.equals(e.attacker())
                        && cast.ability().equals(e.inflictor())
                        && Math.abs(e.gameSecond() - ring.formSecond()) <= window)
            .map(CombatEvent::target)
            .collect(Collectors.toSet());
    if (hurt.isEmpty()) {
      return Optional.empty();
    }
    Optional<CastSnapshot> frame =
        frames.stream()
            .min(Comparator.comparingDouble(f -> Math.abs(f.second() - ring.formSecond())));
    List<HeroPoint> points =
        frame
            .map(f -> f.heroes().stream())
            .orElseGet(Stream::empty)
            .filter(h -> view.isEnemy(h) && hurt.contains(view.nameOf(h)))
            .toList();
    if (points.isEmpty()) {
      return Optional.empty();
    }
    double x = points.stream().mapToDouble(HeroPoint::x).average().orElseThrow();
    double y = points.stream().mapToDouble(HeroPoint::y).average().orElseThrow();
    return Optional.of(new Ring(x, y, ring.radius(), ring.formSecond(), ring.endSecond()));
  }

  private static double positive(OptionalDouble value) {
    return value.isPresent() && value.getAsDouble() > 0 ? value.getAsDouble() : 0;
  }

  private List<HeroPoint> trapped(
      SkillContext context,
      SnapshotView view,
      List<CastSnapshot> frames,
      HeroPoint caster,
      Ring ring) {
    List<CastSnapshot> window =
        frames.stream()
            .filter(f -> f.second() >= ring.formSecond() && f.second() <= ring.endSecond())
            .toList();
    if (window.isEmpty()) {
      return List.of();
    }
    double enter = ring.radius() + context.parameter(ENTER_MARGIN, 100);
    double stay = ring.radius() + context.parameter(STAY_MARGIN, 150);
    return window.getFirst().heroes().stream()
        .filter(h -> h.alive() && view.isEnemy(h))
        .filter(h -> distance(h, ring.x(), ring.y()) <= enter)
        .filter(h -> held(context, view, window, h, caster, ring, stay))
        .toList();
  }

  private boolean held(
      SkillContext context,
      SnapshotView view,
      List<CastSnapshot> window,
      HeroPoint start,
      HeroPoint caster,
      Ring ring,
      double stay) {
    int inside = 0;
    int known = 0;
    boolean onlyBack = true;
    for (CastSnapshot frame : window) {
      Optional<HeroPoint> now = view.point(frame, start.playerId()).filter(HeroPoint::alive);
      if (now.isEmpty()) {
        continue;
      }
      known++;
      if (distance(now.get(), ring.x(), ring.y()) <= stay) {
        inside++;
      } else if (!towardsCaster(context, start, now.get(), caster)) {
        onlyBack = false;
      }
    }
    return known > 0 && (onlyBack || inside >= context.parameter(STAY_SHARE, 0.6) * known);
  }

  private boolean towardsCaster(SkillContext context, HeroPoint start, HeroPoint now, HeroPoint c) {
    double awayX = start.x() - c.x();
    double awayY = start.y() - c.y();
    double moveX = now.x() - start.x();
    double moveY = now.y() - start.y();
    double lengths = Math.hypot(awayX, awayY) * Math.hypot(moveX, moveY);
    if (lengths == 0) {
      return false;
    }
    double cos = Math.max(-1, Math.min(1, (awayX * moveX + awayY * moveY) / lengths));
    return Math.toDegrees(Math.acos(cos)) > context.parameter(RETREAT_DEGREES, 120);
  }

  private int kills(SkillContext context, List<String> names, Ring ring) {
    double until = ring.formSecond() + context.parameter(KILL_SECONDS, 5);
    return (int)
        context.match().events().stream()
            .filter(
                e ->
                    e.kind() == CombatEventKind.DEATH
                        && e.gameSecond() >= ring.formSecond()
                        && e.gameSecond() <= until
                        && names.contains(e.target()))
            .map(CombatEvent::target)
            .distinct()
            .count();
  }

  private Optional<Hero> seenNear(
      SkillContext context,
      SnapshotView view,
      CastSnapshot frame,
      HeroPoint caster,
      Ring ring,
      Map<String, Double> numbers) {
    double seenRange = context.parameter(SEEN_RANGE, 1600);
    double reach =
        distance(caster, ring.x(), ring.y()) + ring.radius() + context.parameter(ENTER_MARGIN, 100);
    List<HeroPoint> witnesses = view.witnesses(frame);
    List<HeroPoint> seen =
        frame.heroes().stream()
            .filter(h -> h.alive() && view.isEnemy(h))
            .filter(h -> distance(h, caster.x(), caster.y()) <= reach)
            .filter(h -> view.seenBy(h, witnesses, seenRange))
            .toList();
    numbers.put("seenEnemies", (double) seen.size());
    return seen.stream()
        .min(Comparator.comparingDouble(h -> distance(h, caster.x(), caster.y())))
        .flatMap(h -> view.hero(h.playerId()));
  }

  private static double distance(HeroPoint h, double x, double y) {
    return Math.hypot(h.x() - x, h.y() - y);
  }
}
