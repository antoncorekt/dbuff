package spike;

import skadistats.clarity.event.Insert;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import java.util.regex.Pattern;

@UsesEntities
public class Heroes {
  @Insert Entities entities;

  static Pattern pat;
  static boolean done;

  @OnTickStart
  public void tick(Context ctx, boolean synthetic) {
    if (done || ctx.getTick() < Integer.getInteger("snap", 20000)) return;
    done = true;
    dump(pat);
    System.exit(0);
  }

  void dump(Pattern p) {
    entities.stream().filter(Entities.byDtName("CDOTAPlayerController")).forEach(c -> {
      int hh = c.getProperty("m_hAssignedHero");
      Entity hero = entities.getByHandle(hh);
      System.out.println("player " + c.getProperty("m_nPlayerID") + " steam " + c.getProperty("m_steamID") + " team " + c.getProperty("m_iTeamNum")
          + " hero " + (hero == null ? "null" : hero.getDtClass().getDtName()));
      if (hero == null) return;
      var fi = hero.getState().fieldPathIterator();
      java.util.List<String> names = new java.util.ArrayList<>();
      while (fi.hasNext()) { String n = hero.getNameForFieldPath(fi.next()); if (n.toLowerCase().contains("abilit")) names.add(n); }
      if (names.size() > 0 && p.matcher("show").find()) System.out.println("   ability fields: " + names.subList(0, Math.min(6, names.size())));
      for (int i = 0; i < 12; i++) {
        String key = String.format("m_vecAbilities.%04d", i);
        if (!hero.hasProperty(key)) continue;
        int hnd = hero.getProperty(key);
        Entity ab = entities.getByHandle(hnd);
        System.out.println("    slot " + i + " -> " + (ab == null ? "null" : ab.getDtClass().getDtName() + " lvl=" + ab.getProperty("m_iLevel")));
      }
    });
  }

  static void show(Entity c, Pattern p, String label) {
    System.out.println("== " + label + " idx " + c.getIndex());
    var fi = c.getState().fieldPathIterator();
    while (fi.hasNext()) {
      FieldPath fp = fi.next();
      String n = c.getNameForFieldPath(fp);
      if (p.matcher(n).find()) System.out.println("  " + n + " = " + c.getPropertyForFieldPath(fp));
    }
  }

  public static void main(String[] args) throws Exception {
    pat = Pattern.compile(args[1], Pattern.CASE_INSENSITIVE);
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new Heroes());
  }
}
