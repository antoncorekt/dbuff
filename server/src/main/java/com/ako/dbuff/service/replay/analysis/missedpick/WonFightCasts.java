package com.ako.dbuff.service.replay.analysis.missedpick;

import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In how many important fights its team won a player's skill was cast. */
final class WonFightCasts {
  private static final String ABILITY = "ABILITY";

  private final Map<TeamSide, Set<Integer>> wonFights = new EnumMap<>(TeamSide.class);
  private final Map<String, Set<Integer>> castFights = new HashMap<>();

  WonFightCasts(List<FightImportanceDomain> importance, List<FightAbilityUseDomain> uses) {
    for (TeamSide side : TeamSide.values()) {
      wonFights.put(side, new HashSet<>());
    }
    for (FightImportanceDomain i : importance) {
      TeamSide winner = side(i.getWinningTeam());
      if (i.isImportant() && i.getFightIndex() != null && winner != null) {
        wonFights.get(winner).add(i.getFightIndex());
      }
    }
    for (FightAbilityUseDomain u : uses) {
      if (ABILITY.equals(u.getKind())
          && u.getCasts() != null
          && u.getCasts() > 0
          && u.getFightIndex() != null
          && u.getPlayerSlot() != null) {
        castFights
            .computeIfAbsent(key(u.getPlayerSlot(), u.getAbilityName()), k -> new HashSet<>())
            .add(u.getFightIndex());
      }
    }
  }

  private static TeamSide side(String name) {
    for (TeamSide side : TeamSide.values()) {
      if (side.name().equalsIgnoreCase(name == null ? null : name.trim())) {
        return side;
      }
    }
    return null;
  }

  int teamWon(TeamSide team) {
    return wonFights.get(team).size();
  }

  /** Important fights won by the player's team in which he cast the skill. */
  int castInWon(long playerSlot, String abilityName) {
    Set<Integer> cast = castFights.getOrDefault(key(playerSlot, abilityName), Set.of());
    Set<Integer> won = wonFights.get(TeamSide.ofSlot(playerSlot));
    return (int) cast.stream().filter(won::contains).count();
  }

  private static String key(long slot, String ability) {
    return slot + "/" + ability;
  }
}
