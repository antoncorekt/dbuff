package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.analysis.rune.RuneType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The runes a hero took while he held a bottle and how many of them went into it: figures only, no
 * gain. A rune goes into the bottle when it is a power rune with an effect modifier and none began
 * on the hero at the pickup. Runes with no modifier (regeneration, illusion) cannot be told apart,
 * so they are not counted as bottled.
 */
@Component
public class BottleRunesAspect implements ItemAspect {
  public static final String NAME = "bottle-runes";

  static final String RUNES_BOTTLED_ESTIMATED = "RUNES_BOTTLED_ESTIMATED";

  private static final String RUNE = "RUNE";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Set<String> parameterNames() {
    return Set.of();
  }

  @Override
  public AspectPart measure(AspectContext context) {
    HeroInputs hero = context.hero();
    List<RunePickupDomain> taken =
        hero.runes().stream()
            .filter(r -> r.getSeconds() != null && hero.holds(context.names(), r.getSeconds()))
            .toList();
    List<Detail> rows = new ArrayList<>();
    int bottled = 0;
    for (RunePickupDomain rune : taken) {
      RuneType type = typeOf(rune);
      boolean inBottle = isBottled(rune, type);
      bottled += inBottle ? 1 : 0;
      Map<String, Double> figures = new LinkedHashMap<>();
      figures.put("bottled", inBottle ? 1.0 : 0.0);
      rows.add(new Detail(RUNE, rune.getSeconds(), type.words(), figures));
    }
    Map<String, Double> totals = new LinkedHashMap<>();
    totals.put("runes", (double) taken.size());
    totals.put("bottled", (double) bottled);
    return new AspectPart(
        0,
        true,
        totals,
        taken.isEmpty() ? List.of() : List.of(RUNES_BOTTLED_ESTIMATED),
        rows,
        hero.uses(context.names()).size(),
        null,
        null);
  }

  private static boolean isBottled(RunePickupDomain rune, RuneType type) {
    return type.modifier() != null && rune.getEffectEndSeconds() == null;
  }

  private static RuneType typeOf(RunePickupDomain rune) {
    for (RuneType type : RuneType.values()) {
      if (type.name().equals(rune.getRuneType())) {
        return type;
      }
    }
    return RuneType.UNKNOWN;
  }
}
