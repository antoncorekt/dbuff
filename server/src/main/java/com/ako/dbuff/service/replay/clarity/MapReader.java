package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.MapFacts;
import java.util.ArrayList;
import java.util.List;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.StringTable;
import skadistats.clarity.model.Vector;
import skadistats.clarity.processor.entities.Entities;

/** Reads the buildings and the jungle camps once the game has started. */
final class MapReader {
  private static final String[] BUILDINGS = {
    "CDOTA_BaseNPC_Tower",
    "CDOTA_BaseNPC_Barracks",
    "CDOTA_BaseNPC_Fort",
    "CDOTA_BaseNPC_Watch_Tower",
    "CDOTA_BaseNPC_LotusPool"
  };
  private static final int MAX_CAMPS = 64;
  private static final String CAMPS = "m_pGameRules.m_NeutralSpawnBoxes.";

  private MapReader() {}

  static MapFacts read(Entities entities, StringTable entityNames, Entity rules) {
    List<MapFacts.Building> buildings = new ArrayList<>();
    for (String dtName : BUILDINGS) {
      entities.stream()
          .filter(Entities.byDtName(dtName))
          .forEach(
              entity -> {
                String name = name(entityNames, entity);
                if (name != null && Positions.has(entity)) {
                  buildings.add(
                      new MapFacts.Building(
                          name, team(entity), Positions.x(entity), Positions.y(entity)));
                }
              });
    }
    return new MapFacts(buildings, camps(rules));
  }

  private static List<MapFacts.Camp> camps(Entity rules) {
    List<MapFacts.Camp> camps = new ArrayList<>();
    for (int i = 0; i < MAX_CAMPS; i++) {
      String prefix = CAMPS + String.format("%04d.", i);
      Object name = value(rules, prefix + "strCampName");
      Object type = value(rules, prefix + "nCampType");
      float[] origin = vector(rules, prefix + "vSpawnBoxOrigin");
      float[] min = vector(rules, prefix + "m_vMinBounds");
      float[] max = vector(rules, prefix + "m_vMaxBounds");
      if (name == null
          || name.toString().isBlank()
          || origin == null
          || min == null
          || max == null) {
        continue;
      }
      camps.add(
          new MapFacts.Camp(
              name.toString(),
              type instanceof Number n ? n.intValue() : -1,
              Math.round(min[0]),
              Math.round(min[1]),
              Math.round(max[0]),
              Math.round(max[1]),
              Math.round(origin[0]),
              Math.round(origin[1])));
    }
    return camps;
  }

  private static String name(StringTable entityNames, Entity entity) {
    Object index = value(entity, "m_pEntity.m_nameStringTableIndex");
    if (!(index instanceof Number n) || n.intValue() < 0) {
      return null;
    }
    return entityNames.getNameByIndex(n.intValue());
  }

  private static int team(Entity entity) {
    return value(entity, "m_iTeamNum") instanceof Number n ? n.intValue() : 0;
  }

  private static Object value(Entity entity, String property) {
    return entity.hasProperty(property) ? entity.getProperty(property) : null;
  }

  private static float[] vector(Entity entity, String property) {
    return value(entity, property) instanceof Vector v && v.getDimension() >= 2
        ? new float[] {v.getElement(0), v.getElement(1)}
        : null;
  }
}
