package com.ako.dbuff.service.replay.clarity;

import skadistats.clarity.model.Entity;

/** World coordinates of an entity, which the replay stores as a map cell plus an offset in it. */
final class Positions {
  private static final int CELL_SIZE = 128;
  private static final int CELL_ORIGIN = 128;

  private Positions() {}

  static int x(Entity entity) {
    return world(entity, "CBodyComponent.m_cellX", "CBodyComponent.m_vecX");
  }

  static int y(Entity entity) {
    return world(entity, "CBodyComponent.m_cellY", "CBodyComponent.m_vecY");
  }

  static boolean has(Entity entity) {
    for (String property :
        new String[] {
          "CBodyComponent.m_cellX",
          "CBodyComponent.m_cellY",
          "CBodyComponent.m_vecX",
          "CBodyComponent.m_vecY"
        }) {
      if (!entity.hasProperty(property) || !(entity.getProperty(property) instanceof Number)) {
        return false;
      }
    }
    return true;
  }

  private static int world(Entity entity, String cell, String offset) {
    return (int) ((number(entity, cell) - CELL_ORIGIN) * CELL_SIZE + number(entity, offset));
  }

  private static double number(Entity entity, String property) {
    return entity.hasProperty(property) && entity.getProperty(property) instanceof Number n
        ? n.doubleValue()
        : 0;
  }
}
