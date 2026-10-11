package spike;

import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.OnEntityCreated;
import skadistats.clarity.processor.entities.OnEntityUpdated;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import java.util.*;

public class Dump {
  static final Map<String,Integer> classes = new TreeMap<>();

  @OnEntityCreated
  public void created(Entity e) {
    classes.merge(e.getDtClass().getDtName(), 1, Integer::sum);
  }

  public static void main(String[] args) throws Exception {
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new Dump());
    classes.forEach((k, v) -> System.out.println(v + "\t" + k));
  }
}
