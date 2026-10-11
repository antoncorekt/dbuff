package spike;

import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;

public class Deaths {
  static final double START = 596.5334;

  @OnCombatLogEntry
  public void onCle(CombatLogEntry c) {
    String t = c.getType().name();
    if (t.equals("DOTA_COMBATLOG_DEATH") && c.isTargetHero() && !c.isTargetIllusion()) {
      System.out.printf("DEATH %.1f tgt=%s att=%s infl=%s attHero=%b assists=%s%n", c.getTimestamp() - START, c.getTargetName(), c.getAttackerName(), c.getInflictorName(), c.isAttackerHero(), c.hasAssistPlayers() ? c.getAssistPlayers() : "-");
    }
    if (t.equals("DOTA_COMBATLOG_TEAM_BUILDING_KILL")) {
      System.out.printf("BUILDING %.1f tgt=%s att=%s val=%d valname=%s team=%d%n", c.getTimestamp() - START, c.getTargetName(), c.getAttackerName(), c.getValue(), c.getValueName(), c.getAttackerTeam());
    }
  }

  public static void main(String[] args) throws Exception {
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new Deaths());
  }
}
