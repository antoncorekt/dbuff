package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The heroes the combat log names, tied to the players of the roster. */
public final class CombatHeroes {
  private CombatHeroes() {}

  /** Every name the log marks as a hero, illusions and clones included. */
  public static Set<String> names(List<CombatEvent> events) {
    Set<String> names = new HashSet<>();
    for (CombatEvent e : events) {
      if ((e.flags() & CombatEvent.ATTACKER_HERO) != 0) {
        names.add(e.attacker());
      }
      if ((e.flags() & CombatEvent.TARGET_HERO) != 0) {
        names.add(e.target());
      }
    }
    return names;
  }

  /** The combat-log hero name of a roster player, or null when the log never names them. */
  public static String nameOf(ReplayPlayerDomain player, Set<String> heroNames) {
    return heroNames.stream()
        .filter(name -> HeroNames.matches(player.getHeroEntityName(), name))
        .findFirst()
        .orElse(null);
  }

  /** Hero name to player index 0-9. */
  public static Map<String, Integer> playerIndexByHero(
      List<CombatEvent> events, List<ReplayPlayerDomain> roster) {
    Set<String> heroNames = names(events);
    Map<String, Integer> playerOf = new HashMap<>();
    for (ReplayPlayerDomain player : roster) {
      String name = nameOf(player, heroNames);
      if (name != null) {
        playerOf.put(name, player.getReplayPlayerId() / 2);
      }
    }
    return playerOf;
  }

  /** When each player's hero died, in time order, by player index; Aegis uses are included. */
  public static Map<Integer, List<Double>> deathTimes(
      List<CombatEvent> events, Map<String, Integer> playerOf) {
    Map<Integer, List<Double>> times = new HashMap<>();
    for (CombatEvent e : events) {
      if (e.kind() == CombatEventKind.DEATH
          && e.targetIsRealHero()
          && playerOf.containsKey(e.target())) {
        times.computeIfAbsent(playerOf.get(e.target()), k -> new ArrayList<>()).add(e.gameSecond());
      }
    }
    return times;
  }
}
