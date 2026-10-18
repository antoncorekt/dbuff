package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.analysis.item.StickUses.Use;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Saves: uses at low health during heavy damage after which the hero lived, each worth a fixed
 * price.
 */
@Component
public class SaveAspect implements ItemAspect {
  public static final String NAME = "save";

  private static final String USE = "USE";
  private static final Set<String> PARAMETERS = parameters();

  private static Set<String> parameters() {
    Set<String> names = new HashSet<>(StickUses.PARAMETERS);
    names.add("saveGold");
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
    List<Use> uses = StickUses.of(context).uses();
    double saves = uses.stream().filter(Use::saved).count();
    Map<String, Double> totals = new LinkedHashMap<>();
    totals.put("saves", saves);
    totals.put("diedSoon", uses.stream().filter(u -> !u.survived()).count() * 1.0);
    return new AspectPart(
        saves * context.entry().parameter("saveGold", 300.0),
        true,
        totals,
        List.of(),
        uses.stream().map(SaveAspect::row).toList(),
        uses.size(),
        null,
        null);
  }

  private static Detail row(Use use) {
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("saved", use.saved() ? 1.0 : 0.0);
    figures.put("diedSoon", use.survived() ? 0.0 : 1.0);
    use.hpNow().ifPresent(v -> figures.put("hpBefore", (double) v));
    use.maxHp().ifPresent(v -> figures.put("maxHp", (double) v));
    figures.put("damageAround", use.damageAround());
    return new Detail(USE, use.second(), null, figures);
  }
}
