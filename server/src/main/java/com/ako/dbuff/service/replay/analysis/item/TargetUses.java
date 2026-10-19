package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The uses of an item cast on a hero (Glimmer Cape, Force Staff, Lotus Orb, Eul's, Solar Crest)
 * with what each did to its target: a save when the target, himself or an ally, was low under heavy
 * damage and lived, valued by the death it prevented; a set-up when the target was an enemy who
 * died within the kill window.
 */
record TargetUses(List<Cast> casts) {
  /** The modifier a cast needs on its target within a second, or the cast had no effect. */
  static final Map<String, String> REQUIRED_MODIFIERS =
      Map.of("item_glimmer_cape", "modifier_item_glimmer_cape_fade");

  static final Set<String> EFFECT_MODIFIERS = Set.copyOf(REQUIRED_MODIFIERS.values());

  static final Set<String> PARAMETERS =
      Stream.concat(SaveJudge.PARAMETERS.stream(), WorthPrices.NAMES.stream())
          .collect(Collectors.toUnmodifiableSet());

  static final String DEATH_COST_ESTIMATED = SaveAspect.DEATH_COST_ESTIMATED;

  private static final String CAST = "CAST";
  private static final double EFFECT_SECONDS = 1.0;
  private static final double EPSILON_SECONDS = 0.1;

  enum Who {
    SELF,
    ALLY,
    ENEMY,
    UNKNOWN
  }

  /**
   * One cast.
   *
   * @param target the target's hero name; null when the caster himself
   * @param effective false when the item needed a modifier on the target and it never showed
   * @param verdict the target's health judgment for a self or ally cast, else null
   * @param saveValue the cost of the death the cast prevented; 0 when it was no save
   * @param setUp the share of the kill a cast on a dying enemy earns
   */
  record Cast(
      double second,
      String target,
      Who who,
      boolean effective,
      SaveJudge.Verdict verdict,
      double saveValue,
      boolean estimated,
      double setUp) {
    boolean saved() {
      return effective && verdict != null && verdict.saved();
    }
  }

  static boolean applies(AspectContext context) {
    return context.entry().aspects().contains(TargetUseAspect.NAME);
  }

  static TargetUses of(AspectContext context) {
    HeroInputs hero = context.hero();
    WorthPrices prices = WorthPrices.of(context);
    KillShare killShare = new KillShare(hero, prices);
    DeathCost ownCost = new DeathCost(hero, prices);
    List<Cast> casts = new ArrayList<>();
    Set<KillShare.Death> credited = new HashSet<>();
    for (CombatEvent use : hero.uses(context.names())) {
      String target = use.target() == null || use.target().isBlank() ? hero.hero() : use.target();
      Who who = who(hero, target);
      boolean effective = who != Who.UNKNOWN && effective(hero, use, target);
      String shown = target.equals(hero.hero()) ? null : target;
      if (effective && (who == Who.SELF || who == Who.ALLY)) {
        HeroInputs patient = who == Who.SELF ? hero : ally(hero, target);
        SaveJudge.Verdict verdict = new SaveJudge(patient, context).judge(use.gameSecond());
        DeathCost.Cost cost =
            verdict.saved()
                ? (who == Who.SELF ? ownCost : new DeathCost(patient, prices))
                    .avoided(use.gameSecond())
                : null;
        casts.add(
            new Cast(
                use.gameSecond(),
                shown,
                who,
                true,
                verdict,
                cost == null ? 0 : cost.value(),
                cost != null && cost.estimated(),
                0));
      } else {
        double setUp = 0;
        if (effective && who == Who.ENEMY) {
          setUp = firstCastValue(killShare, prices, credited, target, use.gameSecond());
        }
        casts.add(new Cast(use.gameSecond(), shown, who, effective, null, 0, false, setUp));
      }
    }
    return new TargetUses(List.copyOf(casts));
  }

  /** The share of the kill, for the first cast before each death only. */
  private static double firstCastValue(
      KillShare killShare,
      WorthPrices prices,
      Set<KillShare.Death> credited,
      String victim,
      double second) {
    return killShare.deaths().stream()
        .filter(d -> d.victim().equals(victim))
        .filter(d -> d.second() >= second && d.second() - second <= prices.killWindowSeconds())
        .findFirst()
        .filter(credited::add)
        .map(d -> killShare.disableValue(victim, second))
        .orElse(0.0);
  }

  AspectPart main() {
    Map<String, Double> totals = new LinkedHashMap<>();
    totals.put("effective", casts.stream().filter(Cast::effective).count() * 1.0);
    List<Detail> rows = new ArrayList<>();
    for (Cast cast : casts) {
      rows.add(row(cast, Map.of("effective", cast.effective() ? 1.0 : 0.0)));
    }
    return new AspectPart(0, true, totals, List.of(), rows, casts.size(), null, null);
  }

  AspectPart save() {
    Map<String, Double> totals = new LinkedHashMap<>();
    List<Detail> rows = new ArrayList<>();
    double gain = 0;
    boolean estimated = false;
    for (Cast cast : casts) {
      if (cast.verdict() == null) {
        continue;
      }
      gain += cast.saveValue();
      estimated |= cast.estimated();
      Map<String, Double> figures = new LinkedHashMap<>();
      figures.put("saved", cast.saved() ? 1.0 : 0.0);
      cast.verdict().hpNow().ifPresent(v -> figures.put("hpBefore", (double) v));
      cast.verdict().maxHp().ifPresent(v -> figures.put("maxHp", (double) v));
      figures.put("damageAround", cast.verdict().damageAround());
      figures.put("saveGain", cast.saveValue());
      rows.add(row(cast, figures));
    }
    totals.put("saves", casts.stream().filter(Cast::saved).count() * 1.0);
    totals.put(
        "selfSaves", casts.stream().filter(c -> c.saved() && c.who() == Who.SELF).count() * 1.0);
    totals.put(
        "allySaves", casts.stream().filter(c -> c.saved() && c.who() == Who.ALLY).count() * 1.0);
    totals.put("saveGain", gain);
    return new AspectPart(
        gain,
        true,
        totals,
        estimated ? List.of(DEATH_COST_ESTIMATED) : List.of(),
        rows,
        casts.size(),
        null,
        null);
  }

  AspectPart control() {
    List<Detail> rows = new ArrayList<>();
    double gain = 0;
    for (Cast cast : casts) {
      if (cast.who() == Who.ENEMY) {
        gain += cast.setUp();
        rows.add(
            row(cast, Map.of("setUp", cast.setUp() > 0 ? 1.0 : 0.0, "controlGain", cast.setUp())));
      }
    }
    Map<String, Double> totals = new LinkedHashMap<>();
    totals.put("setUps", casts.stream().filter(c -> c.setUp() > 0).count() * 1.0);
    totals.put("controlGain", gain);
    return new AspectPart(gain, true, totals, List.of(), rows, casts.size(), null, null);
  }

  private static Detail row(Cast cast, Map<String, Double> figures) {
    return new Detail(CAST, cast.second(), cast.target(), new LinkedHashMap<>(figures));
  }

  private static Who who(HeroInputs hero, String target) {
    if (target.equals(hero.hero())) {
      return Who.SELF;
    }
    if (!hero.match().slots().containsKey(target)) {
      return Who.UNKNOWN;
    }
    if (hero.match().enemies(hero.hero(), target)) {
      return Who.ENEMY;
    }
    return hero.match().roster().get().stream().anyMatch(o -> target.equals(o.hero()))
        ? Who.ALLY
        : Who.UNKNOWN;
  }

  private static boolean effective(HeroInputs hero, CombatEvent use, String target) {
    String modifier = REQUIRED_MODIFIERS.get(use.inflictor());
    return modifier == null
        || hero.match().events().stream()
            .anyMatch(
                e ->
                    e.kind() == CombatEventKind.MODIFIER_ADD
                        && modifier.equals(e.inflictor())
                        && target.equals(e.target())
                        && (e.attacker().isEmpty() || e.attacker().equals(hero.hero()))
                        && e.gameSecond() >= use.gameSecond() - EPSILON_SECONDS
                        && e.gameSecond() <= use.gameSecond() + EFFECT_SECONDS);
  }

  /** An ally as the judges read a hero: the roster's inputs of that hero. */
  private static HeroInputs ally(HeroInputs hero, String name) {
    return hero.match().roster().get().stream()
        .filter(other -> name.equals(other.hero()))
        .findFirst()
        .orElseThrow();
  }
}
