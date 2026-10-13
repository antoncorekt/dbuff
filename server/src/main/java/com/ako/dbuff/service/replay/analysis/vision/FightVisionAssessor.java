package com.ako.dbuff.service.replay.analysis.vision;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** The vision of one team at one fight, from the facts of the match. */
final class FightVisionAssessor {
  /**
   * What the match tells.
   *
   * @param presentSlots the players of each fight, by fight index, who took part or stood close
   * @param abilitiesBySlot the abilities each player owned, with the second the level was first
   *     seen
   * @param slotByHero the combat log's hero name to the player slot
   */
  record MatchFacts(
      long matchId,
      List<WardEventDomain> wards,
      InvisibilityLog invisibility,
      DetectionLog detection,
      Map<Integer, Set<Long>> presentSlots,
      Map<Long, Map<String, Double>> abilitiesBySlot,
      Map<String, Long> slotByHero) {}

  private final MatchFacts facts;
  private final VisionProperties properties;
  private final int version;

  FightVisionAssessor(MatchFacts facts, VisionProperties properties, int version) {
    this.facts = facts;
    this.properties = properties;
    this.version = version;
  }

  /** Null when the fight has no centre to measure the wards from. */
  FightVisionDomain assess(FightDomain fight, TeamSide team) {
    if (fight.getCenterX() == null
        || fight.getCenterY() == null
        || fight.getStartSeconds() == null) {
      return null;
    }
    double start = fight.getStartSeconds();
    double end = fight.getEndSeconds() == null ? start : fight.getEndSeconds();
    double windowStart = start - properties.getWindowBeforeSeconds();
    double windowEnd = end + properties.getWindowAfterSeconds();
    TeamWards wards =
        TeamWards.at(
            facts.wards(), team, fight.getCenterX(), fight.getCenterY(), start, end, properties);
    List<WardEventDomain> destroyed =
        TeamWards.destroyedBy(facts.wards(), team, windowStart, windowEnd);
    Set<Long> present = facts.presentSlots().getOrDefault(fight.getFightIndex(), Set.of());
    Set<Long> own = side(present, team);
    Set<Long> enemy = side(present, other(team));
    Set<Long> invisible =
        facts
            .invisibility()
            .invisibleIn(enemy, start, end, properties.getUseLookbackSeconds(), owned(start));
    Set<String> held = facts.detection().held(own, start);
    boolean dustUsed =
        facts.detection().dustUsed(own, start - properties.getUseLookbackSeconds(), end);
    boolean available =
        !held.isEmpty() || dustUsed || wards.sentries() > 0 || wards.sentryPlacedAround();
    boolean used = dustUsed || wards.sentryPlacedAround();
    List<VisionVerdict> verdicts =
        VisionVerdict.of(wards, !invisible.isEmpty(), available, properties);
    return FightVisionDomain.builder()
        .matchId(facts.matchId())
        .fightIndex(fight.getFightIndex())
        .team(team.name())
        .windowStart(windowStart)
        .windowEnd(windowEnd)
        .activeObservers(wards.observers())
        .activeSentries(wards.sentries())
        .youngestObserverAge(wards.youngestAge())
        .oldestObserverAge(wards.oldestAge())
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

  private static String slots(java.util.Collection<Long> slots) {
    return slots.stream()
        .filter(java.util.Objects::nonNull)
        .distinct()
        .sorted()
        .map(String::valueOf)
        .collect(Collectors.joining(","));
  }
}
