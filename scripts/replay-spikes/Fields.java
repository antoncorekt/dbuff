package spike;

import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.OnEntityCreated;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import java.util.*;
import java.util.regex.Pattern;

public class Fields {
  static final Set<String> CLASSES = Set.of("CDOTAGamerulesProxy", "CDOTAGameManagerProxy", "CDOTAPlayerController", "CDOTATeam", "CDOTAPlayerPawn");
  static final Map<String, Entity> first = new TreeMap<>();

  @OnEntityCreated
  public void created(Entity e) {
    String n = e.getDtClass().getDtName();
    if (CLASSES.contains(n)) first.putIfAbsent(n, e);
  }

  public static void main(String[] args) throws Exception {
    Pattern p = Pattern.compile(args[1], Pattern.CASE_INSENSITIVE);
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new Fields());
    for (var en : first.entrySet()) {
      Entity e = en.getValue();
      System.out.println("== " + en.getKey());
      Iterator<FieldPath> it = e.getState().fieldPathIterator();
      int total = 0;
      while (it.hasNext()) {
        FieldPath fp = it.next();
        total++;
        String name = e.getNameForFieldPath(fp);
        if (p.matcher(name).find()) {
          Object v = e.getPropertyForFieldPath(fp);
          System.out.println("  " + name + " = " + v);
        }
      }
      System.out.println("  (total fields " + total + ")");
    }
  }
}
