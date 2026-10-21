package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.analysis.item.StickUses.Use;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Saves: uses at low health during heavy damage after which the hero lived, each worth the cost of
 * the death it prevented ({@link DeathCost}).
 */
@Component
public class SaveAspect implements ItemAspect {
  public static final String NAME = "save";

  static final String DEATH_COST_ESTIMATED = "DEATH_COST_ESTIMATED";

  private static final String USE = "USE";
  private static final Set<String> PARAMETERS = parameters();

  private static Set<String> parameters() {
    return ParameterNames.withPrices(
        StickUses.PARAMETERS, TeamUses.PARAMETERS, TargetUses.PARAMETERS);
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Set<String> parameterNames() {
    return PARAMETERS;
  }

  @Override
  public AspectPart measure(AspectContext context) {
    if (TargetUses.applies(context)) {
      return TargetUses.of(context).save();
    }
    return context.entry().aspects().contains(TeamRestoreAspect.NAME)
        ? measureAllies(context)
        : measureSelf(context);
  }

  private AspectPart measureSelf(AspectContext context) {
    List<Use> uses = StickUses.of(context).uses();
    DeathCost deathCost = new DeathCost(context.hero(), WorthPrices.of(context));
    double gain = 0;
    boolean estimated = false;
    List<Detail> rows = new ArrayList<>();
    for (Use use : uses) {
      double value = 0;
      if (use.saved()) {
        DeathCost.Cost cost = deathCost.avoided(use.second());
        value = cost.value();
        estimated |= cost.estimated();
      }
      gain += value;
      rows.add(row(use, value));
    }
    Map<String, Double> totals = new LinkedHashMap<>();
    totals.put("saves", uses.stream().filter(Use::saved).count() * 1.0);
    totals.put("diedSoon", uses.stream().filter(u -> !u.survived()).count() * 1.0);
    totals.put("saveGain", gain);
    return new AspectPart(
        gain,
        true,
        totals,
        estimated ? List.of(DEATH_COST_ESTIMATED) : List.of(),
        rows,
        uses.size(),
        null,
        null);
  }

  /** The uses of an item that restores allies save the allies it reached. */
  private AspectPart measureAllies(AspectContext context) {
    List<TeamUses.Use> uses = TeamUses.of(context).uses();
    WorthPrices prices = WorthPrices.of(context);
    Map<Long, DeathCost> costs = new HashMap<>();
    double gain = 0;
    double saves = 0;
    double diedSoon = 0;
    boolean estimated = false;
    List<Detail> rows = new ArrayList<>();
    for (TeamUses.Use use : uses) {
      double value = 0;
      for (TeamUses.Reach reach : use.reached()) {
        if (reach.saved()) {
          DeathCost.Cost cost =
              costs
                  .computeIfAbsent(reach.ally().slot(), s -> new DeathCost(reach.ally(), prices))
                  .avoided(use.second());
          value += cost.value();
          estimated |= cost.estimated();
        }
      }
      gain += value;
      saves += use.saves();
      diedSoon += use.diedSoon();
      Map<String, Double> figures = new LinkedHashMap<>();
      figures.put("saved", (double) use.saves());
      figures.put("diedSoon", (double) use.diedSoon());
      figures.put("saveGain", value);
      rows.add(new Detail(USE, use.second(), null, figures));
    }
    Map<String, Double> totals = new LinkedHashMap<>();
    totals.put("saves", saves);
    totals.put("diedSoon", diedSoon);
    totals.put("saveGain", gain);
    return new AspectPart(
        gain,
        true,
        totals,
        estimated ? List.of(DEATH_COST_ESTIMATED) : List.of(),
        rows,
        uses.size(),
        null,
        null);
  }

  private static Detail row(Use use, double value) {
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("saved", use.saved() ? 1.0 : 0.0);
    figures.put("diedSoon", use.survived() ? 0.0 : 1.0);
    use.hpNow().ifPresent(v -> figures.put("hpBefore", (double) v));
    use.maxHp().ifPresent(v -> figures.put("maxHp", (double) v));
    figures.put("damageAround", use.damageAround());
    figures.put("saveGain", value);
    return new Detail(USE, use.second(), null, figures);
  }
}
