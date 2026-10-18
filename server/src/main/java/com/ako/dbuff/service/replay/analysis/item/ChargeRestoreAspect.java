package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.analysis.item.StickUses.Use;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Magic Stick and its upgrade Magic Wand, which are one chain: what the charges restored, health
 * and mana capped by what was missing when that is known, at a price per point.
 */
@Component
public class ChargeRestoreAspect implements ItemAspect {
  public static final String NAME = "charge-restore";

  static final String HP_UNCAPPED = "HP_UNCAPPED";
  static final String MANA_UNCAPPED = "MANA_UNCAPPED";
  static final String NO_CHARGES_SEEN = "NO_CHARGES_SEEN";

  private static final String USE = "USE";
  private static final Set<String> PARAMETERS = parameters();

  private static Set<String> parameters() {
    Set<String> names = new HashSet<>(StickUses.PARAMETERS);
    names.addAll(Set.of("goldPerHp", "goldPerMana"));
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

  @Override
  public AspectPart measure(AspectContext context) {
    ItemEfficiencyEntry entry = context.entry();
    List<Use> uses = StickUses.of(context).uses();
    double hp = uses.stream().mapToDouble(Use::hp).sum();
    double mana = uses.stream().mapToDouble(Use::mana).sum();
    Map<String, Double> totals = new LinkedHashMap<>();
    totals.put("charges", uses.stream().mapToDouble(Use::charges).sum());
    totals.put("hp", hp);
    totals.put("mana", mana);
    totals.put("laningUses", uses.stream().filter(Use::laning).count() * 1.0);
    List<String> flags = new ArrayList<>();
    if (uses.stream().anyMatch(u -> !u.hpKnown())) {
      flags.add(HP_UNCAPPED);
    }
    if (uses.stream().anyMatch(u -> !u.manaKnown())) {
      flags.add(MANA_UNCAPPED);
    }
    if (uses.stream().anyMatch(u -> !u.chargesSeen())) {
      flags.add(NO_CHARGES_SEEN);
    }
    double gain =
        hp * entry.parameter("goldPerHp", 0.3) + mana * entry.parameter("goldPerMana", 0.3);
    return new AspectPart(
        gain,
        true,
        totals,
        flags,
        uses.stream().map(ChargeRestoreAspect::row).toList(),
        uses.size(),
        null,
        null);
  }

  private static Detail row(Use use) {
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("charges", (double) use.charges());
    figures.put("hp", use.hp());
    figures.put("mana", use.mana());
    figures.put("laning", use.laning() ? 1.0 : 0.0);
    return new Detail(USE, use.second(), null, figures);
  }
}
