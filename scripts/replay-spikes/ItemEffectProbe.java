package spike;

import java.util.*;
import java.util.regex.Pattern;
import skadistats.clarity.event.Insert;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;

@UsesEntities
public class ItemEffectProbe {
  static final Pattern MODIFIER =
      Pattern.compile(
          "item_|glimmer|force|dust|truesight|bottle|soul_ring|aeon|sphere|lotus|cyclone|eul");
  static final Map<String, Integer> heals = new TreeMap<>();
  static final Map<String, Integer> modifiers = new TreeMap<>();
  static final Map<String, Integer> radiance = new TreeMap<>();
  static final List<String> purchases = new ArrayList<>();
  static int purchaseCount;
  static final Map<String, Set<String>> goldFields = new TreeMap<>();
  static final Set<String> healthFields = new TreeSet<>();
  static boolean scanned;

  @Insert Entities entities;

  @OnCombatLogEntry
  public void onCle(CombatLogEntry c) {
    switch (c.getType().name()) {
      case "DOTA_COMBATLOG_HEAL" -> heals.merge(c.getInflictorName(), 1, Integer::sum);
      case "DOTA_COMBATLOG_MODIFIER_ADD" -> {
        String n = c.getInflictorName();
        if (n != null && MODIFIER.matcher(n).find()) modifiers.merge(n, 1, Integer::sum);
      }
      case "DOTA_COMBATLOG_DAMAGE" -> {
        String n = c.getInflictorName();
        if (n != null && n.contains("radiance")) radiance.merge(n, 1, Integer::sum);
      }
      case "DOTA_COMBATLOG_PURCHASE" -> {
        purchaseCount++;
        if (purchases.size() < 5)
          purchases.add(
              String.format(
                  "t=%.1f att=%s tgt=%s infl=%s val=%d valname=%s",
                  c.getTimestamp(),
                  c.getAttackerName(),
                  c.getTargetName(),
                  c.getInflictorName(),
                  c.getValue(),
                  c.getValueName()));
      }
      default -> {}
    }
  }

  @OnTickStart
  public void tick(Context ctx, boolean synthetic) {
    if (scanned || ctx.getTick() < 40000) return;
    scanned = true;
    var it = entities.stream().iterator();
    Set<String> seenClasses = new HashSet<>();
    while (it.hasNext()) {
      Entity e = it.next();
      String cls = e.getDtClass().getDtName();
      boolean hero = cls.startsWith("CDOTA_Unit_Hero_");
      boolean data =
          cls.contains("PlayerResource")
              || cls.contains("PlayerController")
              || cls.contains("Data") && cls.startsWith("CDOTA_");
      if (!hero && !data) continue;
      if (!seenClasses.add(hero ? "hero" : cls)) continue;
      Iterator<FieldPath> fi = e.getState().fieldPathIterator();
      while (fi.hasNext()) {
        String name = e.getNameForFieldPath(fi.next());
        if (data && name.contains("Gold"))
          goldFields
              .computeIfAbsent(cls, k -> new TreeSet<>())
              .add(name.replaceAll("\\d{4}", "NNNN"));
        if (hero && name.contains("MaxHealth")) healthFields.add(name);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new ItemEffectProbe());
    System.out.println("== (a) HEAL by inflictor");
    heals.entrySet().stream()
        .sorted((a, b) -> b.getValue() - a.getValue())
        .forEach(e -> System.out.println(e.getValue() + "\t" + e.getKey()));
    System.out.println("== (b) MODIFIER_ADD names");
    modifiers.forEach((k, v) -> System.out.println(v + "\t" + k));
    System.out.println("== (c) DAMAGE radiance inflictors");
    radiance.forEach((k, v) -> System.out.println(v + "\t" + k));
    System.out.println("== (d) PURCHASE count=" + purchaseCount);
    purchases.forEach(p -> System.out.println("  " + p));
    System.out.println("== (e) Gold fields by data entity");
    goldFields.forEach((k, v) -> v.forEach(f -> System.out.println("  " + k + " : " + f)));
    System.out.println("== (e) hero MaxHealth fields");
    healthFields.forEach(f -> System.out.println("  " + f));
  }
}
