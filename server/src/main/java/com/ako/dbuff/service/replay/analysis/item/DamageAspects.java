package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** What the two damage aspects share: the estimate, its value by kill share and the part. */
final class DamageAspects {
  static final String DAMAGE = "DAMAGE";
  static final String GAIN = "gain.";
  static final Set<String> PARAMETERS = parameters();

  private DamageAspects() {}

  private static Set<String> parameters() {
    return ParameterNames.withPrices(
        Set.of(
            "damageWindowMinutes",
            "windowMinutes",
            "minBeforeSeconds",
            "minAfterSeconds",
            "statShareSeconds"));
  }

  static DamageComponent estimate(AspectContext context, FarmWindows windows) {
    return DamageComponent.estimate(
        context.entry(),
        context.hero(),
        context.names(),
        windows.bought(),
        windows.ownedTo(),
        (int) WorthPrices.of(context).trendMinutes());
  }

  /** The hero's damage to enemy heroes in the ownership, optionally only by the item's names. */
  static Predicate<CombatEvent> during(FarmWindows windows, List<String> inflictors) {
    return e ->
        e.gameSecond() > windows.bought()
            && e.gameSecond() <= windows.ownedTo()
            && (inflictors == null || inflictors.contains(e.inflictor()));
  }

  static AspectPart part(
      String aspect,
      FarmWindows windows,
      double value,
      Map<String, Double> figures,
      List<String> flags,
      Map<String, Double> row) {
    figures.put(GAIN + aspect, value);
    Double perMinute = windows.ownedMinutes() > 0 ? value / windows.ownedMinutes() : 0;
    return new AspectPart(
        value,
        true,
        figures,
        flags,
        List.of(new Detail(DAMAGE, windows.bought(), null, row)),
        0,
        null,
        perMinute);
  }
}
