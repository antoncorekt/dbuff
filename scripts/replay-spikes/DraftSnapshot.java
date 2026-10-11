package spike;

import skadistats.clarity.event.Insert;
import skadistats.clarity.model.Entity;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.processor.stringtables.StringTables;
import skadistats.clarity.processor.stringtables.UsesStringTable;
import skadistats.clarity.source.MappedFileSource;

/** Emits, per drafted slot, "D player slot abilityId abilityName" at a mid-game tick. */
@UsesEntities
@UsesStringTable("EntityNames")
public class DraftSnapshot {
  @Insert Entities entities;
  @Insert StringTables stringTables;
  static boolean done;

  @OnTickStart
  public void tick(Context ctx, boolean synthetic) {
    if (done || ctx.getTick() < 20000) return;
    done = true;
    Entity rules = entities.stream().filter(Entities.byDtName("CDOTAGamerulesProxy")).findFirst().get();
    for (int i = 0; i < 48; i++) {
      String b = String.format("m_pGameRules.m_AbilityDraftAbilities.%04d.", i);
      int abilityId = rules.getProperty(b + "m_nAbilityID");
      int player = rules.getProperty(b + "m_unPlayerID");
      int slot = rules.getProperty(b + "m_unAbilityPlayerSlot");
      String name = "-";
      if (player != 20) {
        Entity ctrl = entities.stream().filter(Entities.byDtName("CDOTAPlayerController"))
            .filter(c -> ((Integer) c.getProperty("m_nPlayerID")) == player).findFirst().orElse(null);
        Entity hero = ctrl == null ? null : entities.getByHandle(ctrl.getProperty("m_hAssignedHero"));
        int heroSlot = slot == 3 ? 5 : slot;
        if (hero != null) {
          Entity ab = entities.getByHandle(hero.getProperty(String.format("m_vecAbilities.%04d", heroSlot)));
          if (ab != null) name = stringTables.forName("EntityNames").getNameByIndex(ab.getProperty("m_pEntity.m_nameStringTableIndex"));
        }
      }
      System.out.println("D\t" + i + "\t" + player + "\t" + slot + "\t" + abilityId + "\t" + name);
    }
    entities.stream().filter(Entities.byDtName("CDOTAPlayerController")).forEach(c ->
        System.out.println("P\t" + c.getProperty("m_nPlayerID") + "\t" + c.getProperty("m_steamID") + "\t" + c.getProperty("m_iTeamNum")));
    System.exit(0);
  }

  public static void main(String[] args) throws Exception {
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new DraftSnapshot());
  }
}
