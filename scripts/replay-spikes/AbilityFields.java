package spike;

import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.OnEntityCreated;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import java.util.*;

public class AbilityFields {
  static Entity sample;

  @OnEntityCreated(classPattern = "CDOTA_Ability_Furion_Teleportation")
  public void created(Entity e) { if (sample == null) sample = e; }

  public static void main(String[] args) throws Exception {
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new AbilityFields());
    Iterator<FieldPath> it = sample.getState().fieldPathIterator();
    while (it.hasNext()) {
      FieldPath fp = it.next();
      System.out.println(sample.getNameForFieldPath(fp) + " = " + sample.getPropertyForFieldPath(fp));
    }
  }
}
