package com.ako.dbuff.service.replay.clarity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import skadistats.clarity.model.Entity;
import skadistats.clarity.processor.entities.Entities;

/** The players the replay lists, read from their controllers. */
final class Controllers {
  private static final int RADIANT = 2;
  private static final int DIRE = 3;

  private Controllers() {}

  /** One playing controller; the hero is null until it exists. */
  record View(int playerId, int team, long steamId, Entity hero) {}

  /**
   * Radiant and Dire players only: spectators and casters also have controllers. A controller whose
   * fields have not arrived yet is skipped, not guessed.
   */
  static List<View> read(Entities entities) {
    List<View> views = new ArrayList<>();
    entities.stream()
        .filter(Entities.byDtName("CDOTAPlayerController"))
        .forEach(
            controller -> {
              Number playerId = number(controller, "m_nPlayerID");
              Number team = number(controller, "m_iTeamNum");
              if (playerId == null
                  || team == null
                  || playerId.intValue() < 0
                  || (team.intValue() != RADIANT && team.intValue() != DIRE)) {
                return;
              }
              Number steamId = number(controller, "m_steamID");
              Number heroHandle = number(controller, "m_hAssignedHero");
              Entity hero = heroHandle == null ? null : entities.getByHandle(heroHandle.intValue());
              views.add(
                  new View(
                      playerId.intValue(),
                      team.intValue(),
                      steamId == null ? 0 : steamId.longValue(),
                      hero));
            });
    views.sort(Comparator.comparingInt(View::playerId));
    return views;
  }

  private static Number number(Entity entity, String property) {
    return entity.hasProperty(property) && entity.getProperty(property) instanceof Number n
        ? n
        : null;
  }
}
