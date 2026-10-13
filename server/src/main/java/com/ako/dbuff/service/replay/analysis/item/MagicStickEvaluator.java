package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Magic Stick and its upgrade Magic Wand, which are one chain: what the charges restored (health
 * and mana, capped by what was missing when that is known) and the saves, uses at low health during
 * heavy damage after which the hero lived.
 */
@Component
public class MagicStickEvaluator implements ItemEfficiencyEvaluator {
  static final String KIND = "magic-stick";

  static final String HP_UNCAPPED = "HP_UNCAPPED";
  static final String MANA_UNCAPPED = "MANA_UNCAPPED";
  static final String NO_CHARGES_SEEN = "NO_CHARGES_SEEN";
  static final String COST_UNKNOWN = "COST_UNKNOWN";

  private static final String USE = "USE";
  private static final Set<String> PARAMETERS =
      Set.of(
          "restorePerCharge",
          "goldPerHp",
          "goldPerMana",
          "saveGold",
          "laningEndSeconds",
          "lowHpFraction",
          "lowHpAbsolute",
          "heavyDamageFraction",
          "heavyDamageAbsolute",
          "damageBeforeSeconds",
          "damageAfterSeconds",
          "surviveSeconds",
          "hpLookbackSeconds",
          "maxHpWindowSeconds");

  @Override
  public boolean supports(ItemEfficiencyEntry entry) {
    return KIND.equals(entry.kind());
  }

  @Override
  public Set<String> parameterNames() {
    return PARAMETERS;
  }

  @Override
  public List<ItemEfficiency> evaluate(EntryContext context) {
    List<String> names = context.replayNames();
    return context.heroes().stream()
        .filter(hero -> !hero.held(names).isEmpty())
        .map(hero -> evaluate(context, hero, names))
        .toList();
  }

  /** One use: its detail row and what could not be capped or seen. */
  private record Assessed(Detail row, boolean hpUncapped, boolean manaUncapped, boolean noCharges) {
    double figure(String name) {
      return row.figures().getOrDefault(name, 0.0);
    }
  }

  private ItemEfficiency evaluate(EntryContext context, HeroInputs hero, List<String> names) {
    ItemEfficiencyEntry entry = context.entry();
    HeroHealth health = new HeroHealth(hero.hero(), hero.events(), hero.track());
    List<Assessed> uses =
        hero.uses(names).stream().map(use -> assess(entry, hero, health, names, use)).toList();
    double hp = uses.stream().mapToDouble(u -> u.figure("hp")).sum();
    double mana = uses.stream().mapToDouble(u -> u.figure("mana")).sum();
    double saves = uses.stream().mapToDouble(u -> u.figure("saved")).sum();
    Map<String, Double> totals = new LinkedHashMap<>();
    totals.put("charges", uses.stream().mapToDouble(u -> u.figure("charges")).sum());
    totals.put("hp", hp);
    totals.put("mana", mana);
    totals.put("saves", saves);
    totals.put("diedSoon", uses.stream().mapToDouble(u -> u.figure("diedSoon")).sum());
    totals.put("laningUses", uses.stream().mapToDouble(u -> u.figure("laning")).sum());
    List<String> flags = new ArrayList<>();
    if (uses.stream().anyMatch(Assessed::hpUncapped)) {
      flags.add(HP_UNCAPPED);
    }
    if (uses.stream().anyMatch(Assessed::manaUncapped)) {
      flags.add(MANA_UNCAPPED);
    }
    if (uses.stream().anyMatch(Assessed::noCharges)) {
      flags.add(NO_CHARGES_SEEN);
    }
    double gain =
        hp * entry.parameter("goldPerHp", 0.3)
            + mana * entry.parameter("goldPerMana", 0.3)
            + saves * entry.parameter("saveGold", 300.0);
    Long cost = context.cost(hero.held(names));
    if (cost == null) {
      flags.add(COST_UNKNOWN);
    }
    return new ItemEfficiency(
        hero.slot(),
        entry.key(),
        HeroInputs.itemKey(hero.held(names).getFirst()),
        KIND,
        hero.firstAdded(names).orElse(0),
        uses.size(),
        gain,
        cost,
        cost == null ? null : gain / cost,
        null,
        totals,
        flags,
        uses.stream().map(Assessed::row).toList());
  }

  private static Assessed assess(
      ItemEfficiencyEntry entry,
      HeroInputs hero,
      HeroHealth health,
      List<String> names,
      CombatEvent use) {
    double t = use.gameSecond();
    OptionalInt seen = hero.chargesAt(names, t);
    int charges = seen.orElse(0);
    double restored = charges * entry.parameter("restorePerCharge", 15.0);
    double window = entry.parameter("maxHpWindowSeconds", 120.0);
    OptionalInt maxHp = health.maxHealth(t, window);
    OptionalInt hpNow = health.health(t, entry.parameter("hpLookbackSeconds", 3.0), window);
    boolean hpKnown = maxHp.isPresent() && hpNow.isPresent();
    double hp =
        hpKnown ? Math.min(restored, Math.max(0, maxHp.getAsInt() - hpNow.getAsInt())) : restored;
    HeroHealth.Mana manaNow = health.mana(t);
    double mana = manaNow == null ? restored : Math.min(restored, manaNow.missing());
    boolean survived = !health.diedBetween(t, t + entry.parameter("surviveSeconds", 10.0));
    boolean saved = survived && isSave(entry, health, t, maxHp, hpNow);
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("charges", (double) charges);
    figures.put("hp", hp);
    figures.put("mana", mana);
    figures.put("laning", t < entry.parameter("laningEndSeconds", 600.0) ? 1.0 : 0.0);
    figures.put("saved", saved ? 1.0 : 0.0);
    figures.put("diedSoon", survived ? 0.0 : 1.0);
    hpNow.ifPresent(v -> figures.put("hpBefore", (double) v));
    maxHp.ifPresent(v -> figures.put("maxHp", (double) v));
    figures.put(
        "damageAround",
        (double)
            health.damage(
                t - entry.parameter("damageBeforeSeconds", 3.0),
                t + entry.parameter("damageAfterSeconds", 3.0)));
    return new Assessed(
        new Detail(USE, t, null, figures), !hpKnown, manaNow == null, seen.isEmpty());
  }

  /** Low health at the use and heavy damage around it; the caller checks that he lived. */
  private static boolean isSave(
      ItemEfficiencyEntry entry,
      HeroHealth health,
      double second,
      OptionalInt maxHp,
      OptionalInt hpNow) {
    if (hpNow.isEmpty()) {
      return false;
    }
    double lowHp =
        maxHp.isPresent()
            ? maxHp.getAsInt() * entry.parameter("lowHpFraction", 0.35)
            : entry.parameter("lowHpAbsolute", 300.0);
    double heavy =
        maxHp.isPresent()
            ? maxHp.getAsInt() * entry.parameter("heavyDamageFraction", 0.3)
            : entry.parameter("heavyDamageAbsolute", 300.0);
    double damage =
        health.damage(
            second - entry.parameter("damageBeforeSeconds", 3.0),
            second + entry.parameter("damageAfterSeconds", 3.0));
    return hpNow.getAsInt() <= lowHp && damage >= heavy;
  }
}
