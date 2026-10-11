package spike;

import skadistats.clarity.event.Insert;
import skadistats.clarity.model.Entity;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;

@UsesEntities
public class ItemProbe {
  @Insert Entities entities;
  static boolean done;

  @OnTickStart
  public void tick(Context ctx, boolean synthetic) {
    if (done || ctx.getTick() < 40000) return;
    done = true;
    Entity hero = entities.stream().filter(Entities.byDtName("CDOTA_Unit_Hero_Sniper")).findFirst().get();
    for (int i = 0; i < 20; i++) {
      for (String pre : new String[]{"m_vecItems", "m_hItems", "m_Inventory.m_hItems", "m_Inventory.m_vecItems"}) {
        String key = String.format(pre + ".%04d", i);
        if (!hero.hasProperty(key)) continue;
        int h = hero.getProperty(key);
        Entity it = entities.getByHandle(h);
        String extra = it == null ? "" : " cd=" + (it.hasProperty("m_fCooldown") ? it.getProperty("m_fCooldown") : "-") + " charges=" + (it.hasProperty("m_iCurrentCharges") ? it.getProperty("m_iCurrentCharges") : "-") + " name=" + it.getDtClass().getDtName();
        System.out.println(key + " -> " + (it == null ? "null" : extra));
      }
    }
    var fi = hero.getState().fieldPathIterator();
    while (fi.hasNext()) { String n = hero.getNameForFieldPath(fi.next()); if (n.matches(".*(Item|Inventory|Gold|Stash).*")) System.out.println("field " + n); }
    System.exit(0);
  }

  public static void main(String[] args) throws Exception {
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new ItemProbe());
  }
}
