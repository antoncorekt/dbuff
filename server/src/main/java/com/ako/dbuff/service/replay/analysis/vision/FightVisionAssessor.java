package com.ako.dbuff.service.replay.analysis.vision;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** The vision of both teams at one fight, from the facts of the match. */
final class FightVisionAssessor {
  static final String UNKNOWN_HERO = "?";

  /**
   * What the match tells.
   *
   * @param presentSlots the players of each fight, by fight index, who took part or stood close
   * @param abilitiesBySlot the abilities each player owned, with the second the level was first
   *     seen
   * @param slotByHero the combat log's hero name to the player slot
   * @param tracks the sampled hero tracks by player slot
   */
  record MatchFacts(
      long matchId,
      List<WardEventDomain> wards,
      InvisibilityLog invisibility,
      DetectionLog detection,
      Map<Integer, Set<Long>> presentSlots,
      Map<Long, Map<String, Double>> abilitiesBySlot,
      Map<String, Long> slotByHero,
      Map<Long, Trace> tracks) {}

  private final MatchFacts facts;
  private final VisionProperties properties;
  private final int version;

  FightVisionAssessor(MatchFacts facts, VisionProperties properties, int version) {
    this.facts = facts;
    this.properties = properties;
    this.version = version;
  }

  /** A row per team; none when the fight has no start or centre to fall back on. */
  List<FightVisionDomain> assess(FightDomain fight, String losingTeam) {
    if (fight.getCenterX() == null
        || fight.getCenterY() == null
        || fight.getStartSeconds() == null) {
      return List.of();
    }
    double start = fight.getStartSeconds();
    Map<Long, Set<String>> owned = owned(start);
    Set<Long> present = facts.presentSlots().getOrDefault(fight.getFightIndex(), Set.of());
    List<FightVisionDomain> rows = new ArrayList<>();
    for (TeamSide team : TeamSide.values()) {
      rows.add(assess(fight, team, losingTeam, present, owned));
    }
    return rows;
  }

  private FightVisionDomain assess(
      FightDomain fight,
      TeamSide team,
      String losingTeam,
      Set<Long> present,
      Map<Long, Set<String>> owned) {
    double start = fight.getStartSeconds();
    double end = fight.getEndSeconds() == null ? start : fight.getEndSeconds();
    double windowStart = start - properties.getWindowBeforeSeconds();
    double windowEnd = end + properties.getWindowAfterSeconds();
    Set<Long> own = side(present, team);
    Set<Long> enemy = side(present, other(team));
    TeamWards wards =
        TeamWards.at(facts.wards(), team, positions(fight, own, start), start, end, properties);
    List<WardEventDomain> destroyed =
        TeamWards.destroyedBy(facts.wards(), team, windowStart, windowEnd);
    Set<Long> invisible =
        facts
            .invisibility()
            .invisibleIn(enemy, start, end, properties.getUseLookbackSeconds(), owned);
    Set<String> held = facts.detection().held(own, start);
    boolean dustUsed =
        facts.detection().dustUsed(own, start - properties.getUseLookbackSeconds(), end);
    boolean available =
        !held.isEmpty() || dustUsed || wards.sentries() > 0 || wards.sentryPlacedAround();
    boolean used = dustUsed || wards.sentryPlacedAround();
    List<VisionVerdict> verdicts = VisionVerdict.of(wards, !invisible.isEmpty(), available);
    return FightVisionDomain.builder()
        .matchId(facts.matchId())
        .fightIndex(fight.getFightIndex())
        .team(team.name())
        .lost(team.name().equals(losingTeam))
        .windowStart(windowStart)
        .windowEnd(windowEnd)
        .activeObservers(wards.observers())
        .activeSentries(wards.sentries())
        .youngestObserverAge(wards.youngestAge())
        .oldestObserverAge(wards.oldestAge())
        .nearestWardDistance(wards.nearestWardDistance())
        .visionWeight(wards.weight())
        .lateWard(wards.late())
        .lateWardSeconds(wards.lateSeconds())
        .wardsDestroyed(destroyed.size())
        .destroyedBy(
            slots(destroyed.stream().map(w -> facts.slotByHero().get(w.getRemovedBy())).toList()))
        .invisibleHero(!invisible.isEmpty())
        .invisibleHeroes(slots(invisible))
        .detection(available)
        .detectionUsed(used)
        .verdict(verdicts.stream().map(VisionVerdict::name).collect(Collectors.joining(",")))
        .analysisVersion(version)
        .build();
  }

  /** Where the team's heroes in the fight stood at its start; the fight's centre when unknown. */
  private List<double[]> positions(FightDomain fight, Set<Long> slots, double start) {
    List<double[]> points = new ArrayList<>();
    for (long slot : slots) {
      Trace trace = facts.tracks().get(slot);
      if (trace != null) {
        int i = trace.indexAt(start);
        if (trace.level()[i] > 0) {
          points.add(new double[] {trace.x()[i], trace.y()[i]});
        }
      }
    }
    if (points.isEmpty()) {
      points.add(new double[] {fight.getCenterX(), fight.getCenterY()});
    }
    return points;
  }

  private Map<Long, Set<String>> owned(double at) {
    return facts.abilitiesBySlot().entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e ->
                    e.getValue().entrySet().stream()
                        .filter(a -> a.getValue() <= at)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet())));
  }

  private static Set<Long> side(Set<Long> slots, TeamSide team) {
    return slots.stream()
        .filter(s -> TeamSide.ofSlot(s) == team)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static TeamSide other(TeamSide team) {
    return team == TeamSide.RADIANT ? TeamSide.DIRE : TeamSide.RADIANT;
  }

  /** The slots as a list, {@code ?} for each that is not known, so no hero is dropped silently. */
  private static String slots(Collection<Long> slots) {
    return slots.stream()
        .map(s -> Objects.isNull(s) ? UNKNOWN_HERO : String.valueOf(s))
        .sorted()
        .collect(Collectors.joining(","));
  }
}
