package spike;

import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import java.util.*;

public class CombatProbe {
  static final Map<String, Integer> counts = new TreeMap<>();
  static final Map<String, List<String>> samples = new HashMap<>();
  static final Map<String, Integer> modifiers = new HashMap<>();

  @OnCombatLogEntry
  public void onCle(CombatLogEntry c) {
    String t = c.getType().name();
    counts.merge(t, 1, Integer::sum);
    var list = samples.computeIfAbsent(t, k -> new ArrayList<>());
    if (list.size() < 2) {
      list.add(String.format("t=%.1f att=%s tgt=%s infl=%s val=%d valname=%s health=%d goldReason=%d xpReason=%d loc=(%.0f,%.0f) stun=%.1f mod=%.1f rune=%d camp=%d assists=%s nw=%d",
          c.getTimestamp(), c.getAttackerName(), c.getTargetName(), c.getInflictorName(), c.getValue(), c.getValueName(), c.getHealth(),
          c.getGoldReason(), c.getXpReason(), c.getLocationX(), c.getLocationY(), c.getStunDuration(), c.getModifierDuration(),
          c.getRuneType(), c.getNeutralCampType(), c.hasAssistPlayers() ? c.getAssistPlayers() : "-", c.getNetworth()));
    }
    if (t.equals("DOTA_COMBATLOG_MODIFIER_ADD")) modifiers.merge(c.getInflictorName(), 1, Integer::sum);
  }

  public static void main(String[] args) throws Exception {
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new CombatProbe());
    counts.forEach((k, v) -> { System.out.println(v + "\t" + k); samples.get(k).forEach(s -> System.out.println("      " + s)); });
    System.out.println("== modifiers (top by count, key ones)");
    modifiers.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(25).forEach(e -> System.out.println(e.getValue() + "\t" + e.getKey()));
    for (String k : new String[]{"modifier_black_king_bar_immune", "modifier_teleporting", "modifier_item_glimmer_cape_fade", "modifier_smoke_of_deceit", "modifier_rune_haste", "modifier_fountain_aura_buff", "modifier_stunned", "modifier_silence", "modifier_invisible"})
      System.out.println("  " + k + " = " + modifiers.getOrDefault(k, 0));
  }
}
