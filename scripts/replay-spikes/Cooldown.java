package spike;

import java.util.HashMap;
import java.util.Map;
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
public class Cooldown {
  @Insert Entities entities;
  @Insert StringTables stringTables;
  static int from, to, player;
  Map<String, String> last = new HashMap<>();

  Object p(Entity e, String k) { return e.hasProperty(k) ? e.getProperty(k) : null; }

  @OnTickStart
  public void tick(Context ctx, boolean synthetic) {
    int t = ctx.getTick();
    if (t < from || t > to || t % 30 != 0) return;
    StringTable names = stringTables.forName("EntityNames");
    entities.stream().filter(Entities.byDtName("CDOTAPlayerController")).forEach(c -> {
      int pid = c.getProperty("m_nPlayerID");
      if (pid != player) return;
      Entity hero = entities.getByHandle((Integer) c.getProperty("m_hAssignedHero"));
      if (hero == null) return;
      for (String list : new String[] {"m_vecAbilities", "m_hItems"}) {
        for (int i = 0; i < 40; i++) {
          String key = String.format("%s.%04d", list, i);
          if (!hero.hasProperty(key)) break;
          Object h = hero.getProperty(key);
          if (!(h instanceof Number) || ((Number) h).intValue() == 16777215 || ((Number) h).intValue() == -1) continue;
          Entity a = entities.getByHandle(((Number) h).intValue());
          if (a == null) continue;
          Object idx = p(a, "m_pEntity.m_nameStringTableIndex");
          String name = idx instanceof Number n && n.intValue() >= 0 ? names.getNameByIndex(n.intValue()) : a.getDtClass().getDtName();
          String state = "cd=" + p(a, "m_fCooldown") + " len=" + p(a, "m_flCooldownLength") + " lvl=" + p(a, "m_iLevel") + " ch=" + p(a, "m_iCurrentCharges") + " mana=" + p(a, "m_iManaCost") + " castStart=" + p(a, "m_flCastStartTime");
          String id = list + i + " " + name;
          String sig = state.replaceAll("cd=[0-9.]+", "cd=#");
          boolean cdOn = state.matches(".*cd=[0-9.]*[1-9].*") && !state.startsWith("cd=0.0 ");
          String prev = last.put(id, cdOn + "|" + state);
          if (prev == null || !prev.equals(cdOn + "|" + state)) {
            if (cdOn || (prev != null && prev.startsWith("true"))) System.out.println("COOLDOWN t=" + (t - from) / 30 + "s " + id + " " + state);
          }
        }
      }
    });
  }

  public static void main(String[] args) throws Exception {
    from = Integer.parseInt(args[1]);
    to = Integer.parseInt(args[2]);
    player = Integer.parseInt(args[3]);
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new Cooldown());
  }
}
