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
import java.util.*;

/** Prints compressed field name lists (with a sample value) for the classes given in args[1..]. */
@UsesEntities
public class Probe {
  @Insert Entities entities;
  static boolean done;
  static List<String> classes;

  @OnTickStart
  public void tick(Context ctx, boolean synthetic) {
    if (done || ctx.getTick() < 30000) return;
    done = true;
    for (String cls : classes) {
      Entity e = entities.stream().filter(Entities.byDtName(cls)).findFirst().orElse(null);
      System.out.println("== " + cls + (e == null ? " (none)" : ""));
      if (e == null) continue;
      LinkedHashMap<String, String> seen = new LinkedHashMap<>();
      var it = e.getState().fieldPathIterator();
      while (it.hasNext()) {
        FieldPath fp = it.next();
        String n = e.getNameForFieldPath(fp);
        String key = n.replaceAll("\\.\\d{4}", ".N");
        Object val = e.getPropertyForFieldPath(fp); seen.putIfAbsent(key, String.valueOf(val));
      }
      seen.forEach((k, v) -> System.out.println("  " + k + " = " + (v.length() > 40 ? v.substring(0, 40) : v)));
    }
    System.exit(0);
  }

  public static void main(String[] args) throws Exception {
    classes = Arrays.asList(args).subList(1, args.length);
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new Probe());
  }
}
