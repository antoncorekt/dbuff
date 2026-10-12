package spike;

import skadistats.clarity.event.Insert;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.StringTable;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.processor.stringtables.StringTables;
import skadistats.clarity.processor.stringtables.UsesStringTable;
import skadistats.clarity.source.MappedFileSource;

@UsesEntities
@UsesStringTable("EntityNames")
public class MapProbe {
  @Insert Entities entities;
  @Insert StringTables stringTables;
  boolean done;

  double pos(Entity e, String c, String v) {
    return ((Number) e.getProperty(c)).doubleValue() * 0 + (((Number) e.getProperty(c)).intValue() - 128) * 128 + ((Number) e.getProperty(v)).doubleValue();
  }

  @OnTickStart
  public void tick(Context ctx, boolean synthetic) {
    if (done || ctx.getTick() < 20000) return;
    done = true;
    StringTable names = stringTables.forName("EntityNames");
    for (String dt : new String[] {"CDOTA_BaseNPC_Tower", "CDOTA_BaseNPC_Barracks", "CDOTA_BaseNPC_Fort", "CDOTA_RoshanSpawner", "CDOTA_BaseNPC_Watch_Tower", "CDOTA_NeutralSpawner", "CDOTA_BaseNPC_LotusPool", "CDOTA_Unit_Roshan"}) {
      entities.stream().filter(Entities.byDtName(dt)).forEach(e -> {
        String name = "?";
        try {
          int idx = e.getProperty("m_pEntity.m_nameStringTableIndex");
          name = names.getNameByIndex(idx);
        } catch (Exception ex) { name = "ERR " + ex.getMessage(); }
        Object teamObj = e.hasProperty("m_iTeamNum") ? e.getProperty("m_iTeamNum") : "-";
        String team = "" + teamObj;
        String xy = e.hasProperty("CBodyComponent.m_cellX") ? String.format("%.0f,%.0f", pos(e, "CBodyComponent.m_cellX", "CBodyComponent.m_vecX"), pos(e, "CBodyComponent.m_cellY", "CBodyComponent.m_vecY")) : "no-pos";
        System.out.println(dt + " team=" + team + " name=" + name + " pos=" + xy);
      });
    }
    entities.stream().filter(Entities.byDtName("CDOTAGamerulesProxy")).forEach(r -> {
      for (int i = 0; i < 40; i++) {
        String p = String.format("m_pGameRules.m_NeutralSpawnBoxes.%04d.", i);
        if (!r.hasProperty(p + "strCampName")) break;
        Object n = r.getProperty(p + "strCampName"), t = r.getProperty(p + "nCampType"), o = r.getProperty(p + "vSpawnBoxOrigin"), mn = r.getProperty(p + "m_vMinBounds"), mx = r.getProperty(p + "m_vMaxBounds");
        System.out.println("CAMP " + i + " " + n + " type=" + t + " origin=" + o + " min=" + mn + " max=" + mx);
      }
    });
  }

  public static void main(String[] args) throws Exception {
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new MapProbe());
  }
}
