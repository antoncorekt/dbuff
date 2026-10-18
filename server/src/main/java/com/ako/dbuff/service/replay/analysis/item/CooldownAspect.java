package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Cooldown reduction, an investment: a cast made sooner after the previous cast of the same ability
 * than its base cooldown allows is an extra cast. The base is the cooldown the replay showed at the
 * cast divided by one minus the item's reduction. An extra cast is worth the share of the kills its
 * damage helped within a few seconds, or the gold of an extra Hand of Midas use.
 */
@Component
public class CooldownAspect implements ItemAspect {
  public static final String NAME = "cooldown";

  static final String REDUCTION_UNKNOWN = "REDUCTION_UNKNOWN";
  static final String COOLDOWN_NOT_SEEN = "COOLDOWN_NOT_SEEN";

  private static final String CAST = "CAST";
  private static final String REDUCTION = "bonus_cooldown";
  private static final String MIDAS = HeroInputs.replayName("hand_of_midas");
  private static final double PERCENT = 100;
  private static final double COOLDOWN_LOOKBACK_SECONDS = 1;
  private static final double COOLDOWN_LOOKAHEAD_SECONDS = 6;
  private static final Set<String> PARAMETERS = parameters();

  private static Set<String> parameters() {
    Set<String> names = new HashSet<>(Set.of("damageSeconds", "toleranceSeconds"));
    names.addAll(WorthPrices.NAMES);
    return Set.copyOf(names);
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Set<String> parameterNames() {
    return PARAMETERS;
  }

  private record Extra(CombatEvent cast, double gap, double base, double goldValue) {}

  @Override
  public AspectPart measure(AspectContext context) {
    ItemEfficiencyEntry entry = context.entry();
    HeroInputs hero = context.hero();
    FarmWindows windows = FarmWindows.of(context);
    double reduction =
        context.kits().attribute(entry.items().getFirst(), REDUCTION).orElse(0) / PERCENT;
    Set<String> flags = new HashSet<>();
    List<Extra> extras = new ArrayList<>();
    int casts = 0;
    if (reduction <= 0 || reduction >= 1) {
      flags.add(REDUCTION_UNKNOWN);
    } else {
      double tolerance = context.price("toleranceSeconds", 0.5);
      Map<String, Double> midasGold = midasGold(context);
      Map<String, Double> last = new LinkedHashMap<>();
      for (CombatEvent cast : hero.events()) {
        if (!isCast(cast, hero)) {
          continue;
        }
        Double previous = last.put(cast.inflictor(), cast.gameSecond());
        if (cast.gameSecond() <= windows.bought() || cast.gameSecond() > windows.ownedTo()) {
          continue;
        }
        OptionalDouble observed = observedCooldown(hero, cast);
        if (observed.isEmpty()) {
          flags.add(COOLDOWN_NOT_SEEN);
          continue;
        }
        casts++;
        double base = observed.getAsDouble() / (1 - reduction);
        if (previous != null && cast.gameSecond() - previous < base - tolerance) {
          extras.add(
              new Extra(
                  cast,
                  cast.gameSecond() - previous,
                  base,
                  midasGold.getOrDefault(key(cast), 0.0)));
        }
      }
    }
    return part(context, windows, extras, casts, flags);
  }

  private AspectPart part(
      AspectContext context,
      FarmWindows windows,
      List<Extra> extras,
      int casts,
      Set<String> flags) {
    double window = context.price("damageSeconds", 3);
    Predicate<CombatEvent> fromExtra = fromExtraCast(extras, window);
    KillShare share = new KillShare(context.hero(), WorthPrices.of(context));
    double damageValue = extras.isEmpty() ? 0 : share.value(fromExtra);
    double goldValue = extras.stream().mapToDouble(Extra::goldValue).sum();
    double value = damageValue + goldValue;
    List<Detail> rows = new ArrayList<>();
    for (Extra e : extras) {
      Map<String, Double> figures = new LinkedHashMap<>();
      figures.put("gap", e.gap());
      figures.put("baseCooldown", e.base());
      figures.put("goldValue", e.goldValue());
      rows.add(new Detail(CAST, e.cast().gameSecond(), e.cast().inflictor(), figures));
    }
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("casts", (double) casts);
    figures.put("extraCasts", (double) extras.size());
    figures.put("extraDamageValue", damageValue);
    figures.put("extraGold", goldValue);
    figures.put("killsHelped", (double) share.killsHelped(fromExtra));
    figures.put(DamageAspects.GAIN + NAME, value);
    double perMinute = windows.ownedMinutes() > 0 ? value / windows.ownedMinutes() : 0;
    return new AspectPart(
        value,
        true,
        figures,
        flags.stream().sorted().toList(),
        rows,
        extras.size(),
        null,
        perMinute);
  }

  private static Predicate<CombatEvent> fromExtraCast(List<Extra> extras, double window) {
    return e ->
        extras.stream()
            .anyMatch(
                x ->
                    e.inflictor().equals(x.cast().inflictor())
                        && e.gameSecond() >= x.cast().gameSecond()
                        && e.gameSecond() <= x.cast().gameSecond() + window);
  }

  private static boolean isCast(CombatEvent e, HeroInputs hero) {
    if (!hero.hero().equals(e.attacker())) {
      return false;
    }
    return e.kind() == CombatEventKind.ABILITY
        || (e.kind() == CombatEventKind.ITEM && MIDAS.equals(e.inflictor()));
  }

  private static String key(CombatEvent cast) {
    return cast.inflictor() + "@" + cast.gameSecond();
  }

  /** The gold each Midas use gave, by the use's key. */
  private static Map<String, Double> midasGold(AspectContext context) {
    Map<String, Double> gold = new LinkedHashMap<>();
    int bonus = MidasUsesAspect.bonusGold(context.kits(), "hand_of_midas");
    if (bonus <= 0) {
      return gold;
    }
    for (MidasUsesAspect.Use use :
        MidasUsesAspect.pairedUses(context.hero(), List.of(MIDAS), bonus)) {
      gold.put(key(use.event()), (double) use.gold());
    }
    return gold;
  }

  private static OptionalDouble observedCooldown(HeroInputs hero, CombatEvent cast) {
    return hero.changes().stream()
        .filter(c -> StateChangeKind.COOLDOWN.name().equals(c.getKind()))
        .filter(c -> cast.inflictor().equals(c.getName()) && c.getCooldownLength() != null)
        .filter(c -> c.getCooldownLength() > 0)
        .filter(
            c ->
                c.getGameSeconds() >= cast.gameSecond() - COOLDOWN_LOOKBACK_SECONDS
                    && c.getGameSeconds() <= cast.gameSecond() + COOLDOWN_LOOKAHEAD_SECONDS)
        .mapToDouble(ReplayStateChangeDomain::getCooldownLength)
        .findFirst();
  }
}
