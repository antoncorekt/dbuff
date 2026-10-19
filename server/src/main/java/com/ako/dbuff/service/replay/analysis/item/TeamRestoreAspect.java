package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.analysis.item.TeamUses.Use;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Arcane Boots, Mekansm, Guardian Greaves and Holy Locket: the health and mana a use gave the
 * allies around the hero, at a price per point. The saves it made are {@link SaveAspect}'s.
 */
@Component
public class TeamRestoreAspect implements ItemAspect {
  public static final String NAME = "team-restore";

  static final String MANA_ESTIMATED = "MANA_ESTIMATED";
  static final String RADIUS_ASSUMED = "RADIUS_ASSUMED";

  private static final String USE = "USE";
  private static final Set<String> PARAMETERS = parameters();

  private static Set<String> parameters() {
    Set<String> names = new HashSet<>(TeamUses.PARAMETERS);
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
    List<Use> uses = TeamUses.of(context).uses();
    double hp = uses.stream().mapToDouble(Use::hp).sum();
    double mana = uses.stream().mapToDouble(Use::mana).sum();
    Map<String, Double> totals = new LinkedHashMap<>();
    totals.put("hp", hp);
    totals.put("mana", mana);
    totals.put("allyReaches", uses.stream().mapToDouble(u -> u.reached().size()).sum());
    totals.put(
        "allies",
        (double)
            uses.stream()
                .flatMap(u -> u.reached().stream())
                .map(r -> r.ally().slot())
                .distinct()
                .count());
    double gain = hp * context.price("goldPerHp", 0.3) + mana * context.price("goldPerMana", 0.3);
    return new AspectPart(
        gain,
        true,
        totals,
        flags(mana > 0, uses.stream().anyMatch(Use::radiusAssumed)),
        uses.stream().map(TeamRestoreAspect::row).toList(),
        uses.size(),
        null,
        null);
  }

  private static Detail row(Use use) {
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("hp", use.hp());
    figures.put("mana", use.mana());
    figures.put("allies", (double) use.reached().size());
    return new Detail(USE, use.second(), null, figures);
  }

  private static List<String> flags(boolean manaEstimated, boolean radiusAssumed) {
    List<String> flags = new java.util.ArrayList<>();
    if (manaEstimated) {
      flags.add(MANA_ESTIMATED);
    }
    if (radiusAssumed) {
      flags.add(RADIUS_ASSUMED);
    }
    return flags;
  }
}
