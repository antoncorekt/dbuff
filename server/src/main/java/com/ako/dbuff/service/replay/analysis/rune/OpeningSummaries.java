package com.ako.dbuff.service.replay.analysis.rune;

import com.ako.dbuff.dao.model.OpeningSummaryDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.GoldReason;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The opening of a match: the window around the first rune spawn, in which the team with the higher
 * gold plus experience balance wins and kills break a tie.
 */
final class OpeningSummaries {
  private static final String DRAW = "DRAW";

  private final RuneProperties properties;
  private final Map<String, TeamSide> teamOfHero;

  /**
   * @param teamOfHero the team of each combat-log hero name
   */
  OpeningSummaries(RuneProperties properties, Map<String, TeamSide> teamOfHero) {
    this.properties = properties;
    this.teamOfHero = teamOfHero;
  }

  List<OpeningSummaryDomain> build(
      long matchId, List<RunePickupDomain> pickups, List<CombatEvent> events, int version) {
    int from = properties.openingStart();
    int to = properties.openingEnd();
    Map<TeamSide, Long> gold = sums(events, CombatEventKind.GOLD, from, to);
    Map<TeamSide, Long> xp = sums(events, CombatEventKind.XP, from, to);
    Map<TeamSide, Integer> kills = kills(events, from, to);
    Map<TeamSide, Long> bounty = bountyGold(events, from, to);
    Map<TeamSide, Long> bountyGame = bountyGold(events, Integer.MIN_VALUE, Integer.MAX_VALUE);
    Map<TeamSide, Long> balance = new EnumMap<>(TeamSide.class);
    for (TeamSide team : TeamSide.values()) {
      TeamSide other = other(team);
      balance.put(team, gold.get(team) + xp.get(team) - gold.get(other) - xp.get(other));
    }
    return Stream.of(TeamSide.values())
        .map(
            team ->
                summary(
                    matchId,
                    team,
                    pickups,
                    from,
                    to,
                    gold,
                    xp,
                    kills,
                    balance,
                    bounty,
                    bountyGame,
                    version))
        .toList();
  }

  private OpeningSummaryDomain summary(
      long matchId,
      TeamSide team,
      List<RunePickupDomain> pickups,
      int from,
      int to,
      Map<TeamSide, Long> gold,
      Map<TeamSide, Long> xp,
      Map<TeamSide, Integer> kills,
      Map<TeamSide, Long> balance,
      Map<TeamSide, Long> bounty,
      Map<TeamSide, Long> bountyGame,
      int version) {
    List<RunePickupDomain> own =
        pickups.stream().filter(p -> team.name().equals(p.getTeam())).toList();
    List<RunePickupDomain> inWindow =
        own.stream().filter(p -> p.getSeconds() >= from && p.getSeconds() <= to).toList();
    Optional<RunePickupDomain> first =
        inWindow.stream()
            .min(
                Comparator.comparing(RunePickupDomain::getSeconds)
                    .thenComparing(RunePickupDomain::getPlayerSlot));
    return OpeningSummaryDomain.builder()
        .matchId(matchId)
        .team(team.name())
        .windowStart(from)
        .windowEnd(to)
        .runesTaken(inWindow.size())
        .bountyGold(bounty.get(team).intValue())
        .bountyGoldGame(bountyGame.get(team).intValue())
        .firstTakerSlot(first.map(RunePickupDomain::getPlayerSlot).orElse(null))
        .firstTakerSeconds(first.map(RunePickupDomain::getSeconds).orElse(null))
        .kills(kills.get(team))
        .gold(gold.get(team))
        .xp(xp.get(team))
        .balance(balance.get(team))
        .result(result(team, balance, kills))
        .analysisVersion(version)
        .build();
  }

  private static String result(
      TeamSide team, Map<TeamSide, Long> balance, Map<TeamSide, Integer> kills) {
    TeamSide other = other(team);
    int byBalance = Long.compare(balance.get(team), balance.get(other));
    int decided = byBalance != 0 ? byBalance : Integer.compare(kills.get(team), kills.get(other));
    return decided > 0 ? "WON" : decided < 0 ? "LOST" : DRAW;
  }

  /** A bounty rune pays every hero of the taker's team, so the team's gold is every entry. */
  private Map<TeamSide, Long> bountyGold(List<CombatEvent> events, int from, int to) {
    return sums(events, CombatEventKind.GOLD, from, to, GoldReason.BOUNTY_RUNE);
  }

  private Map<TeamSide, Long> sums(
      List<CombatEvent> events, CombatEventKind kind, int from, int to) {
    return sums(events, kind, from, to, null);
  }

  private Map<TeamSide, Long> sums(
      List<CombatEvent> events, CombatEventKind kind, int from, int to, GoldReason only) {
    Map<TeamSide, Long> sums = new EnumMap<>(TeamSide.class);
    for (TeamSide team : TeamSide.values()) {
      sums.put(team, 0L);
    }
    for (CombatEvent e : events) {
      TeamSide team = teamOfHero.get(e.target());
      if (e.kind() == kind
          && team != null
          && (only == null || e.goldReason() == only)
          && inWindow(e, from, to)) {
        sums.merge(team, (long) e.value(), Long::sum);
      }
    }
    return sums;
  }

  private Map<TeamSide, Integer> kills(List<CombatEvent> events, int from, int to) {
    Map<TeamSide, Integer> kills = new EnumMap<>(TeamSide.class);
    for (TeamSide team : TeamSide.values()) {
      kills.put(team, 0);
    }
    for (CombatEvent e : events) {
      TeamSide died = teamOfHero.get(e.target());
      if (e.kind() == CombatEventKind.DEATH
          && e.targetIsRealHero()
          && died != null
          && inWindow(e, from, to)) {
        kills.merge(other(died), 1, Integer::sum);
      }
    }
    return kills;
  }

  private static boolean inWindow(CombatEvent e, int from, int to) {
    return e.gameSecond() >= from && e.gameSecond() <= to;
  }

  private static TeamSide other(TeamSide team) {
    return team == TeamSide.RADIANT ? TeamSide.DIRE : TeamSide.RADIANT;
  }
}
