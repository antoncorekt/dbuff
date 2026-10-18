package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the two damage aspects share: the estimate, its price and the value in the comparison. */
final class DamageAspects {
  static final String DAMAGE = "DAMAGE";
  static final Set<String> PARAMETERS =
      Set.of(
          "damagePrice",
          "damageWindowMinutes",
          "windowMinutes",
          "minBeforeSeconds",
          "minAfterSeconds");

  private DamageAspects() {}

  static double price(AspectContext context) {
    return context.entry().parameter("damagePrice", 100.0);
  }

  static DamageComponent estimate(AspectContext context, FarmWindows windows) {
    return DamageComponent.estimate(
        context.entry(), context.hero(), context.names(), windows.bought(), windows.ownedTo());
  }

  /** The part of the damage value; it counts in the gain only where the farm comparison holds. */
  static AspectPart part(
      FarmWindows windows,
      double value,
      Map<String, Double> figures,
      List<String> flags,
      Map<String, Double> row) {
    double counted = windows.comparable() ? value : 0;
    Double perMinute =
        windows.comparable()
            ? (windows.ownedMinutes() > 0 ? value / windows.ownedMinutes() : 0)
            : null;
    return new AspectPart(
        counted,
        true,
        figures,
        flags,
        List.of(new Detail(DAMAGE, windows.bought(), null, row)),
        0,
        null,
        perMinute);
  }
}
