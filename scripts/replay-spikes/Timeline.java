package spike;

import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.OnEntityCreated;
import skadistats.clarity.processor.entities.OnEntityUpdated;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import java.util.regex.Pattern;

public class Timeline {
  static Pattern watch;

  @OnEntityCreated(classPattern = "CDOTAGamerulesProxy")
  public void created(Context ctx, Entity e) {
    dump(ctx, e, null, 0, "CREATE");
  }

  @OnEntityUpdated(classPattern = "CDOTAGamerulesProxy")
  public void updated(Context ctx, Entity e, FieldPath[] fps, int num) {
    dump(ctx, e, fps, num, "UPD");
  }

  void dump(Context ctx, Entity e, FieldPath[] fps, int num, String kind) {
    if (fps == null) {
      var it = e.getState().fieldPathIterator();
      while (it.hasNext()) line(ctx, e, it.next(), kind);
    } else {
      for (int i = 0; i < num; i++) line(ctx, e, fps[i], kind);
    }
  }

  void line(Context ctx, Entity e, FieldPath fp, String kind) {
    String name = e.getNameForFieldPath(fp);
    if (watch.matcher(name).find()) {
      Object gt = e.hasProperty("m_pGameRules.m_fGameTime") ? e.getProperty("m_pGameRules.m_fGameTime") : null;
      Object st = e.hasProperty("m_pGameRules.m_nGameState") ? e.getProperty("m_pGameRules.m_nGameState") : null;
      System.out.println(ctx.getTick() + "\t" + gt + "\t" + st + "\t" + kind + "\t" + name + "\t" + e.getPropertyForFieldPath(fp));
    }
  }

  public static void main(String[] args) throws Exception {
    watch = Pattern.compile(args[1]);
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new Timeline());
  }
}
