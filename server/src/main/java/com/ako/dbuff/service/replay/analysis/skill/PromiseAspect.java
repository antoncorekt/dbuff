package com.ako.dbuff.service.replay.analysis.skill;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.skill.SkillCasts.Cast;
import com.ako.dbuff.service.replay.analysis.skill.SkillContext.Hero;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Judges a skill that delays the damage an ally takes (False Promise). A save is a dangerous
 * promise the ally survived; an impact is an ally who died after real damage, kills or assists
 * during it. A cast with no danger, or a death without impact, is not counted. A missed chance is
 * an allied hero dying within cast range while the skill was learned, ready and its owner alive.
 */
@Component
public class PromiseAspect implements SkillAspect {
  public static final String NAME = "promise";
  public static final String SAVE = "SAVE";
  public static final String IMPACT = "IMPACT";
  public static final String MISSED = "MISSED";
  public static final String NOT_COUNTED = "NOT_COUNTED";

  static final String TIMER_MODIFIER = "modifier_oracle_false_promise_timer";

  private static final String DANGER_SHARE = "dangerShare";
  private static final String DANGER_ABSOLUTE = "dangerAbsolute";
  private static final String IMPACT_DAMAGE = "impactDamage";
  private static final String END_GRACE = "endGraceSeconds";
  private static final String SURVIVE = "surviveSeconds";
  private static final String RANGE_BONUS = "rangeBonus";
  private static final String FALLBACK_DURATION = "fallbackDurationSeconds";
  private static final String FALLBACK_RANGE = "fallbackCastRange";
  private static final String DURATION = "duration";
  private static final String CAST_RANGE = "abilitycastrange";
  private static final double TIMER_SEARCH_SECONDS = 30;
  private static final double HP_LOOKBACK_SECONDS = 3;
  private static final double MAX_HP_WINDOW_SECONDS = 120;

  private static final Set<String> PARAMETERS =
      Set.of(
          DANGER_SHARE,
          DANGER_ABSOLUTE,
          IMPACT_DAMAGE,
          END_GRACE,
          SURVIVE,
          RANGE_BONUS,
          FALLBACK_DURATION,
          FALLBACK_RANGE);

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
    return List.of(SAVE, IMPACT, NOT_COUNTED, MISSED);
  }

  @Override
  public List<Outcome> judge(SkillContext context) {
    List<Outcome> out = new ArrayList<>();
    List<double[]> windows = new ArrayList<>();
    for (Cast cast : context.casts().casts()) {
      Window window = window(context, cast);
      windows.add(new double[] {cast.second(), window.end() + context.parameter(END_GRACE, 1.5)});
      out.add(judgeCast(context, cast, window));
    }
    out.addAll(missedChances(context, windows));
    return out;
  }

  private record Window(double end, boolean fromModifier) {}

  private Window window(SkillContext context, Cast cast) {
    double end =
        context.match().events().stream()
            .filter(
                e ->
                    e.kind() == CombatEventKind.MODIFIER_REMOVE
                        && TIMER_MODIFIER.equals(e.inflictor())
                        && cast.target().equals(e.target())
                        && e.gameSecond() >= cast.second()
                        && e.gameSecond() <= cast.second() + TIMER_SEARCH_SECONDS)
            .mapToDouble(CombatEvent::gameSecond)
            .min()
            .orElse(Double.NaN);
    if (!Double.isNaN(end)) {
      return new Window(end, true);
    }
    OptionalDouble duration = context.attribute(cast.ability(), DURATION, cast.level());
    return new Window(
        cast.second() + duration.orElse(context.parameter(FALLBACK_DURATION, 8.5)), false);
  }

  private Outcome judgeCast(SkillContext context, Cast cast, Window window) {
    Hero ally = context.match().hero(cast.target()).orElse(null);
    Map<String, Double> numbers = new LinkedHashMap<>();
    numbers.put("level", (double) cast.level());
    numbers.put("windowSeconds", window.end() - cast.second());
    if (ally == null) {
      return new Outcome(cast.second(), cast.target(), NOT_COUNTED, true, numbers);
    }
    double from = cast.second();
    double to = window.end() + context.parameter(END_GRACE, 1.5);
    double damage = ally.health().damage(from, to);
    numbers.put("damage", damage);
    OptionalInt maxHp = ally.health().maxHealth(from, MAX_HP_WINDOW_SECONDS);
    OptionalInt hp = ally.health().health(from, HP_LOOKBACK_SECONDS, MAX_HP_WINDOW_SECONDS);
    maxHp.ifPresent(v -> numbers.put("maxHp", (double) v));
    hp.ifPresent(v -> numbers.put("hpAtCast", (double) v));

    OptionalDouble death = ally.health().deathBetween(from, to + context.parameter(SURVIVE, 5));
    if (death.isPresent()) {
      return impactOf(context, cast, ally, Math.min(death.getAsDouble(), to), numbers);
    }
    boolean danger =
        hp.isPresent() && damage >= hp.getAsInt()
            || inFight(context, from, to) && damage >= heavy(context, maxHp);
    return new Outcome(cast.second(), ally.name(), danger ? SAVE : NOT_COUNTED, true, numbers);
  }

  private static double heavy(SkillContext context, OptionalInt maxHp) {
    return maxHp.isPresent()
        ? maxHp.getAsInt() * context.parameter(DANGER_SHARE, 0.3)
        : context.parameter(DANGER_ABSOLUTE, 300);
  }

  private static boolean inFight(SkillContext context, double from, double to) {
    return context.match().fights().stream().anyMatch(f -> overlaps(f, from, to));
  }

  private static boolean overlaps(FightDomain fight, double from, double to) {
    return fight.getStartSeconds() != null
        && fight.getEndSeconds() != null
        && fight.getStartSeconds() <= to
        && fight.getEndSeconds() >= from;
  }

  private Outcome impactOf(
      SkillContext context, Cast cast, Hero ally, double until, Map<String, Double> numbers) {
    double dealt = 0;
    int kills = 0;
    int assists = 0;
    for (CombatEvent e : context.match().events()) {
      if (e.gameSecond() <= cast.second() || e.gameSecond() > until || !e.targetIsRealHero()) {
        continue;
      }
      boolean enemy =
          context.match().hero(e.target()).filter(t -> t.team() != ally.team()).isPresent();
      if (!enemy) {
        continue;
      }
      if (e.kind() == CombatEventKind.DAMAGE && ally.name().equals(e.attacker())) {
        dealt += e.value();
      } else if (e.kind() == CombatEventKind.DEATH) {
        if (ally.name().equals(e.attacker())) {
          kills++;
        } else if ((e.assistMask() & (1 << ally.playerIndex())) != 0) {
          assists++;
        }
      }
    }
    numbers.put("damageDealt", dealt);
    numbers.put("kills", (double) kills);
    numbers.put("assists", (double) assists);
    numbers.put("died", 1.0);
    boolean impact = dealt >= context.parameter(IMPACT_DAMAGE, 500) || kills + assists > 0;
    return new Outcome(cast.second(), ally.name(), impact ? IMPACT : NOT_COUNTED, true, numbers);
  }

  private List<Outcome> missedChances(SkillContext context, List<double[]> promised) {
    Hero owner = context.caster();
    List<Outcome> out = new ArrayList<>();
    String ability = context.entry().abilities().getFirst();
    for (CombatEvent death : context.match().events()) {
      if (death.kind() != CombatEventKind.DEATH
          || !death.targetIsRealHero()
          || death.target().equals(owner.name())) {
        continue;
      }
      double second = death.gameSecond();
      Hero ally = context.match().hero(death.target()).orElse(null);
      int level = context.casts().levelAt(ability, second);
      if (ally == null
          || ally.team() != owner.team()
          || level < 1
          || context.casts().onCooldown(ability, second)
          || !owner.aliveAt(second)
          || promised.stream().anyMatch(w -> second >= w[0] && second <= w[1])) {
        continue;
      }
      double range =
          context
                  .attribute(ability, CAST_RANGE, level)
                  .orElse(context.parameter(FALLBACK_RANGE, 800))
              + context.parameter(RANGE_BONUS, 300);
      owner
          .positionAt(second)
          .flatMap(o -> ally.positionAt(second).map(a -> Math.hypot(o[0] - a[0], o[1] - a[1])))
          .filter(distance -> distance <= range)
          .ifPresent(
              distance ->
                  out.add(
                      new Outcome(
                          second,
                          ally.name(),
                          MISSED,
                          false,
                          Map.of("distance", (double) Math.round(distance), "range", range))));
    }
    return out;
  }
}
