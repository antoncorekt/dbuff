package com.ako.dbuff.service.replay.analysis.skill;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.service.replay.CastSnapshot;
import com.ako.dbuff.service.replay.CastSnapshot.HeroPoint;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.health.SaveRule;
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
import org.springframework.stereotype.Component;

/**
 * Judges a skill that pulls the first unit in a line (Meat Hook). The replay says no team sees a
 * hero, so an enemy counts as seen when he stood within {@code seenRange} of a living allied hero
 * other than the caster at the cast; a hit without a frame or without the target in it is not
 * counted. An enemy hit is a seen or a dark hit; a miss counts only when a seen enemy was aimed at;
 * an ally hit counts only as a save; creep and farming hooks are not counted.
 */
@Component
public class HookAspect implements SkillAspect {
  public static final String NAME = "hook";
  public static final String SEEN_HIT = "SEEN_HIT";
  public static final String DARK_HIT = "DARK_HIT";
  public static final String SEEN_MISS = "SEEN_MISS";
  public static final String ALLY_SAVE = "ALLY_SAVE";
  public static final String NOT_COUNTED = "NOT_COUNTED";
  public static final String ACCURACY = "accuracy";

  static final String HOOK_MODIFIER = "modifier_pudge_meat_hook";

  private static final String SEEN_RANGE = "seenRange";
  private static final String AIM_RANGE_BONUS = "aimRangeBonus";
  private static final String AIM_ANGLE = "aimAngleDegrees";
  private static final String EXTRA_SECONDS = "hitExtraSeconds";
  private static final String FALLBACK_SECONDS = "fallbackWindowSeconds";
  private static final String FALLBACK_RANGE = "fallbackHookRange";
  private static final String LOW_SHARE = "saveLowShare";
  private static final String HEAVY_SHARE = "saveHeavyShare";
  private static final String BEFORE_SECONDS = "saveBeforeSeconds";
  private static final String SURVIVE_SECONDS = "saveSurviveSeconds";
  private static final String SPEED = "hook_speed";
  private static final String RANGE = "abilitycastrange";
  private static final double HP_LOOKBACK_SECONDS = 3;
  private static final double MAX_HP_WINDOW_SECONDS = 120;
  private static final double ABSOLUTE_LOW = 300;
  private static final double ABSOLUTE_HEAVY = 300;
  private static final double ANYTHING_LOW = 1.0;

  private static final Set<String> PARAMETERS =
      Set.of(
          SEEN_RANGE,
          AIM_RANGE_BONUS,
          AIM_ANGLE,
          EXTRA_SECONDS,
          FALLBACK_SECONDS,
          FALLBACK_RANGE,
          LOW_SHARE,
          HEAVY_SHARE,
          BEFORE_SECONDS,
          SURVIVE_SECONDS);

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
    return List.of(SEEN_HIT, DARK_HIT, SEEN_MISS, ALLY_SAVE, NOT_COUNTED);
  }

  @Override
  public boolean needsSnapshots() {
    return true;
  }

  @Override
  public Map<String, Integer> derivedFigures(Map<String, Integer> counts) {
    int hits = counts.getOrDefault(SEEN_HIT, 0);
    int misses = counts.getOrDefault(SEEN_MISS, 0);
    return hits + misses == 0
        ? Map.of()
        : Map.of(ACCURACY, (int) Math.round(100.0 * hits / (hits + misses)));
  }

  @Override
  public List<Outcome> judge(SkillContext context) {
    List<CastSnapshot> snapshots = context.match().snapshots().get();
    SnapshotView view = new SnapshotView(context);
    List<Outcome> out = new ArrayList<>();
    for (Cast cast : context.casts().casts()) {
      out.add(judgeCast(context, view, cast, snapshots));
    }
    return out;
  }

  private record Frame(CastSnapshot snapshot, Optional<HeroPoint> caster) {}

  private Outcome judgeCast(
      SkillContext context, SnapshotView view, Cast cast, List<CastSnapshot> snapshots) {
    Hero caster = context.caster();
    double range =
        context.attribute(cast.ability(), RANGE, cast.level()).orElse(fallbackRange(context));
    double window = window(context, cast, range);
    Map<String, Double> numbers = new LinkedHashMap<>();
    numbers.put("level", (double) cast.level());
    numbers.put("range", range);
    Optional<CombatEvent> hit = firstHit(context, cast, window);
    Optional<Frame> frame = frameAt(view, snapshots, caster, cast);
    Set<Integer> seen = frame.map(f -> seenEnemies(context, view, f.snapshot())).orElse(Set.of());
    numbers.put("seenEnemies", (double) seen.size());
    if (frame.isEmpty()) {
      numbers.put("noSnapshot", 1.0);
    }

    if (hit.isPresent()) {
      Hero target = context.match().hero(hit.get().target()).orElseThrow();
      numbers.put("hitAfter", hit.get().gameSecond() - cast.second());
      if (target.team() != caster.team()) {
        String verdict = enemyHitVerdict(view, frame, seen, target);
        return new Outcome(cast.second(), target.name(), verdict, true, numbers);
      }
      boolean save = isSave(context, target, hit.get().gameSecond(), numbers);
      return new Outcome(
          cast.second(), target.name(), save ? ALLY_SAVE : NOT_COUNTED, true, numbers);
    }
    Optional<Hero> aimed = frame.flatMap(f -> aimedAt(context, view, f, seen, range, numbers));
    return aimed
        .map(h -> new Outcome(cast.second(), h.name(), SEEN_MISS, true, numbers))
        .orElseGet(() -> new Outcome(cast.second(), "", NOT_COUNTED, true, numbers));
  }

  private static String enemyHitVerdict(
      SnapshotView view, Optional<Frame> frame, Set<Integer> seen, Hero target) {
    if (frame.isEmpty() || view.point(frame.get().snapshot(), target.playerId()).isEmpty()) {
      return NOT_COUNTED;
    }
    return seen.contains(target.playerId()) ? SEEN_HIT : DARK_HIT;
  }

  private static double fallbackRange(SkillContext context) {
    return context.parameter(FALLBACK_RANGE, 1300);
  }

  private static double window(SkillContext context, Cast cast, double range) {
    OptionalDouble speed = context.attribute(cast.ability(), SPEED, cast.level());
    return speed.isPresent() && speed.getAsDouble() > 0
        ? range / speed.getAsDouble() + context.parameter(EXTRA_SECONDS, 0.3)
        : context.parameter(FALLBACK_SECONDS, 1.5);
  }

  private static Optional<CombatEvent> firstHit(SkillContext context, Cast cast, double window) {
    String caster = context.caster().name();
    return context.match().events().stream()
        .filter(
            e ->
                e.gameSecond() >= cast.second()
                    && e.gameSecond() <= cast.second() + window
                    && caster.equals(e.attacker())
                    && e.targetIsRealHero()
                    && context.match().hero(e.target()).isPresent()
                    && isHookRow(e, cast))
        .min(Comparator.comparingDouble(CombatEvent::gameSecond));
  }

  private static boolean isHookRow(CombatEvent e, Cast cast) {
    return e.kind() == CombatEventKind.MODIFIER_ADD && HOOK_MODIFIER.equals(e.inflictor())
        || e.kind() == CombatEventKind.DAMAGE && cast.ability().equals(e.inflictor());
  }

  private static Optional<Frame> frameAt(
      SnapshotView view, List<CastSnapshot> snapshots, Hero caster, Cast cast) {
    return view.framesOf(snapshots, cast).stream()
        .findFirst()
        .map(s -> new Frame(s, view.point(s, caster.playerId())));
  }

  private static Set<Integer> seenEnemies(
      SkillContext context, SnapshotView view, CastSnapshot frame) {
    List<HeroPoint> witnesses = view.witnesses(frame);
    double reach = context.parameter(SEEN_RANGE, 1600);
    return frame.heroes().stream()
        .filter(h -> h.alive() && view.isEnemy(h))
        .filter(h -> view.seenBy(h, witnesses, reach))
        .map(HeroPoint::playerId)
        .collect(java.util.stream.Collectors.toSet());
  }

  private Optional<Hero> aimedAt(
      SkillContext context,
      SnapshotView view,
      Frame frame,
      Set<Integer> seen,
      double range,
      Map<String, Double> numbers) {
    if (frame.caster().isEmpty() || frame.snapshot().facing() == CastSnapshot.NO_FACING) {
      return Optional.empty();
    }
    HeroPoint from = frame.caster().get();
    double reach = range + context.parameter(AIM_RANGE_BONUS, 150);
    double maxAngle = context.parameter(AIM_ANGLE, 15);
    return frame.snapshot().heroes().stream()
        .filter(h -> seen.contains(h.playerId()))
        .map(h -> Map.entry(h, Math.hypot(h.x() - from.x(), h.y() - from.y())))
        .filter(e -> e.getValue() <= reach)
        .map(e -> Map.entry(e.getKey(), angleBetween(frame.snapshot().facing(), from, e.getKey())))
        .filter(e -> e.getValue() <= maxAngle)
        .min(Map.Entry.comparingByValue())
        .flatMap(
            e -> {
              numbers.put("aimAngle", (double) Math.round(e.getValue()));
              return view.hero(e.getKey().playerId());
            });
  }

  private static double angleBetween(int facing, HeroPoint from, HeroPoint to) {
    double bearing = Math.toDegrees(Math.atan2(to.y() - from.y(), to.x() - from.x()));
    double diff = Math.abs(((bearing - facing) % 360 + 540) % 360 - 180);
    return diff;
  }

  private boolean isSave(
      SkillContext context, Hero ally, double second, Map<String, Double> numbers) {
    boolean inFight = context.match().fights().stream().anyMatch(f -> contains(f, second));
    SaveRule.Parameters defaults = SaveRule.Parameters.defaults();
    SaveRule rule =
        new SaveRule(
            ally.health(),
            new SaveRule.Parameters(
                inFight ? ANYTHING_LOW : context.parameter(LOW_SHARE, defaults.lowHpFraction()),
                inFight ? Double.MAX_VALUE : ABSOLUTE_LOW,
                context.parameter(HEAVY_SHARE, defaults.heavyDamageFraction()),
                ABSOLUTE_HEAVY,
                context.parameter(BEFORE_SECONDS, defaults.damageBeforeSeconds()),
                0,
                context.parameter(SURVIVE_SECONDS, defaults.surviveSeconds()),
                HP_LOOKBACK_SECONDS,
                MAX_HP_WINDOW_SECONDS));
    SaveRule.Verdict verdict = rule.judge(second);
    numbers.put("damageBefore", verdict.damageAround());
    verdict.maxHp().ifPresent(v -> numbers.put("maxHp", (double) v));
    verdict.hpNow().ifPresent(v -> numbers.put("hpAtHit", (double) v));
    numbers.put("inFight", inFight ? 1.0 : 0.0);
    return verdict.saved();
  }

  private static boolean contains(FightDomain fight, double second) {
    return fight.getStartSeconds() != null
        && fight.getEndSeconds() != null
        && fight.getStartSeconds() <= second
        && fight.getEndSeconds() >= second;
  }
}
