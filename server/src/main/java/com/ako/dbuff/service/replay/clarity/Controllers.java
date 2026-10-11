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

  /** Radiant and Dire players only: spectators and casters also have controllers. */
  static List<View> read(Entities entities) {
    List<View> views = new ArrayList<>();
    entities.stream()
        .filter(Entities.byDtName("CDOTAPlayerController"))
        .forEach(
            controller -> {
              int playerId = controller.getProperty("m_nPlayerID");
              int team = controller.getProperty("m_iTeamNum");
              if (playerId < 0 || (team != RADIANT && team != DIRE)) {
                return;
              }
              long steamId = ((Number) controller.getProperty("m_steamID")).longValue();
              int heroHandle = controller.getProperty("m_hAssignedHero");
              views.add(new View(playerId, team, steamId, entities.getByHandle(heroHandle)));
            });
    views.sort(Comparator.comparingInt(View::playerId));
    return views;
  }
}
