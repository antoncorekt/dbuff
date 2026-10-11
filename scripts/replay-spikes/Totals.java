package spike;

import java.util.Map;
import java.util.TreeMap;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;

public class Totals {
  static final double START = 596.5334;
  static Map<String, Long> damage = new TreeMap<>(), taken = new TreeMap<>();
  static Map<String, Integer> abilities = new TreeMap<>(), items = new TreeMap<>(), stuns = new TreeMap<>();
  static int shown;

  @OnCombatLogEntry
  public void onCle(CombatLogEntry c) {
    String t = c.getType().name();
    boolean aHero = c.isAttackerHero() && !c.isAttackerIllusion();
    boolean tHero = c.isTargetHero() && !c.isTargetIllusion();
    if (t.equals("DOTA_COMBATLOG_DAMAGE") && aHero && tHero && c.getTimestamp() > START) {
      damage.merge(c.getAttackerName(), (long) c.getValue(), Long::sum);
      if (shown++ < 4) System.out.printf("DMG t=%.1f att=%s tgt=%s infl=%s val=%d health=%d%n", c.getTimestamp() - START, c.getAttackerName(), c.getTargetName(), c.getInflictorName(), c.getValue(), c.getHealth());
    }
    if (t.equals("DOTA_COMBATLOG_ABILITY") && aHero && c.getTimestamp() > START) abilities.merge(c.getAttackerName() + " " + c.getInflictorName(), 1, Integer::sum);
    if (t.equals("DOTA_COMBATLOG_ITEM") && aHero && c.getTimestamp() > START) items.merge(c.getAttackerName() + " " + c.getInflictorName(), 1, Integer::sum);
    if (t.equals("DOTA_COMBATLOG_MODIFIER_ADD") && tHero && c.getStunDuration() > 0 && c.getTimestamp() > START) stuns.merge(c.getInflictorName(), 1, Integer::sum);
  }

  public static void main(String[] args) throws Exception {
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new Totals());
    damage.forEach((k, v) -> System.out.println("TOTAL " + k + " " + v));
    abilities.forEach((k, v) -> System.out.println("ABIL " + k + " " + v));
    items.forEach((k, v) -> System.out.println("ITEM " + k + " " + v));
    System.out.println("STUNMODS " + stuns);
  }
}
