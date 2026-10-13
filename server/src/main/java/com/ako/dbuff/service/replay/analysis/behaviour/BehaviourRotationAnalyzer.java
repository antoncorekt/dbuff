package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.CreepKillRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.analysis.building.BuildingName;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.fight.PlayerTraces;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.analysis.lane.TimelineSeries;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one {@code team_rotation} row for each trip of a hero away from his team's group that
 * ended in another lane and came to something (a fight, a kill, a death, or a lane lost to the
 * enemy's creeps). Reads tracks, activity, lanes, fights, deaths, buybacks and creep kills.
 */
@Slf4j
@Order(90)
@Component
@RequiredArgsConstructor
public class BehaviourRotationAnalyzer implements ReplayAnalyzer {
  private static final String ZONE_LANE = Zone.Kind.LANE.name();
  private static final String TOWER_KIND = "TOWER";

  private final MatchMapLayout matchMapLayout;
  private final PlayerTraces playerTraces;
  private final StoredActivity activity;
  private final ReplayPlayerRepo playerRepo;
  private final CombatLogReader combatLog;
  private final DeathAnalysisRepo deathRepo;
  private final BuybackEventRepo buybackRepo;
  private final LaneAssignmentRepo laneRepo;
  private final CreepKillRepo creepRepo;
  private final PlayerTimelineRepo timelineRepo;
  private final BuildingKillContextRepo buildingRepo;
  private final FightRepo fightRepo;
  private final FightPlayerRepo fightPlayerRepo;
  private final BehaviourProperties properties;
  private final TeamRotationRepo rotationRepo;

  @Override
  public String name() {
    return "rotations";
  }

  @Override
  public int version() {
    return 2;
  }

  @Override
  public Set<String> requires() {
    return Set.of(
        "players",
        "tracks",
        "activity",
        "combat-log",
        "creep-kills",
        "deaths",
        "buybacks",
        "fights",
        "lanes",
        "important-fights",
        "building-kills");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    MapLayout layout = matchMapLayout.of(matchId).orElse(null);
    if (layout == null) {
      log.info("Match {} was not played on the frozen map layout, no rotations stored", matchId);
      return;
    }
    Map<Long, Trace> traces = new HashMap<>();
    playerTraces
        .load(matchId)
        .forEach((index, trace) -> traces.put(PlayerSlots.slot(index), trace));
    if (traces.isEmpty()) {
      return;
    }
    RotationFinder finder = new RotationFinder(properties, layout::zone);
    List<Rotation> rotations = finder.find(inputs(matchId, traces));
    rotationRepo.saveAll(rotations.stream().map(r -> row(matchId, r)).toList());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    rotationRepo.deleteByMatchId(matchId);
  }

  private RotationFinder.Inputs inputs(long matchId, Map<Long, Trace> traces) {
    List<CombatEvent> events = combatLog.events(matchId);
    Map<String, Long> slotByHero = new HashMap<>();
    CombatHeroes.playerIndexByHero(events, playerRepo.findByMatchId(matchId))
        .forEach((hero, index) -> slotByHero.put(hero, PlayerSlots.slot(index)));
    return new RotationFinder.Inputs(
        traces,
        activity.bySlot(matchId),
        deaths(matchId),
        buybacks(matchId),
        lanes(matchId),
        new TripOutcomes.Events(kills(events, slotByHero), creeps(matchId), fights(matchId)),
        midGame(matchId, events, slotByHero));
  }

  private MidGameFacts midGame(
      long matchId, List<CombatEvent> events, Map<String, Long> slotByHero) {
    List<PlayerTimelineDomain> timeline = timelineRepo.findByMatchId(matchId);
    Map<TeamSide, List<MidGameFacts.TowerLoss>> towerLosses = new HashMap<>();
    for (BuildingKillContextDomain b : buildingRepo.findByMatchId(matchId)) {
      TeamSide owner = Enums.find(TeamSide.class, b.getOwnerTeam());
      if (TOWER_KIND.equals(b.getKind()) && b.getSeconds() != null && owner != null) {
        Zone.Lane lane = b.getLane() == null ? null : Enums.find(Zone.Lane.class, b.getLane());
        towerLosses
            .computeIfAbsent(owner, t -> new ArrayList<>())
            .add(new MidGameFacts.TowerLoss(b.getSeconds(), lane));
      }
    }
    return new MidGameFacts(
        TimelineSeries.bySlot(timeline, PlayerTimelineDomain::getGold),
        TimelineSeries.bySlot(timeline, PlayerTimelineDomain::getXp),
        hits(events, slotByHero),
        towerLosses);
  }

  /** Hits on enemy heroes and enemy towers; damage to a hero's own team does not count. */
  static List<DamageHit> hits(List<CombatEvent> events, Map<String, Long> slotByHero) {
    List<DamageHit> hits = new ArrayList<>();
    for (CombatEvent e : events) {
      Long slot = slotByHero.get(e.attacker());
      if (e.kind() != CombatEventKind.DAMAGE || slot == null || !e.attackerIsRealHero()) {
        continue;
      }
      TeamSide own = TeamSide.ofSlot(slot);
      Long victim = e.targetIsRealHero() ? slotByHero.get(e.target()) : null;
      if (victim != null) {
        if (TeamSide.ofSlot(victim) != own) {
          hits.add(new DamageHit(slot, e.gameSecond(), false));
        }
        continue;
      }
      BuildingName.parse(e.target())
          .filter(b -> TOWER_KIND.equals(b.kind()) && !own.name().equals(b.team()))
          .ifPresent(b -> hits.add(new DamageHit(slot, e.gameSecond(), true)));
    }
    return hits;
  }

  private Map<Long, List<Double>> deaths(long matchId) {
    return timesBySlot(
        deathRepo.findByMatchId(matchId).stream()
            .filter(d -> !Boolean.TRUE.equals(d.getReincarnated()))
            .map(d -> new SlotTime(d.getPlayerSlot(), d.getDeathSeconds())));
  }

  private Map<Long, List<Double>> buybacks(long matchId) {
    return timesBySlot(
        buybackRepo.findByMatchId(matchId).stream()
            .map(b -> new SlotTime(b.getPlayerSlot(), b.getSeconds())));
  }

  private record SlotTime(Long slot, Double seconds) {}

  private static Map<Long, List<Double>> timesBySlot(Stream<SlotTime> times) {
    Map<Long, List<Double>> bySlot = new HashMap<>();
    times
        .filter(t -> t.slot() != null && t.seconds() != null)
        .forEach(t -> bySlot.computeIfAbsent(t.slot(), s -> new ArrayList<>()).add(t.seconds()));
    bySlot.values().forEach(list -> list.sort(Double::compare));
    return bySlot;
  }

  private List<LaneSlot> lanes(long matchId) {
    List<LaneSlot> lanes = new ArrayList<>();
    for (LaneAssignmentDomain a : laneRepo.findByMatchId(matchId)) {
      Zone.Lane lane = a.getLane() == null ? null : Enums.find(Zone.Lane.class, a.getLane());
      if (lane != null
          && a.getPlayerSlot() != null
          && a.getStartSeconds() != null
          && a.getEndSeconds() != null) {
        lanes.add(
            new LaneSlot(
                a.getPlayerSlot(),
                TeamSide.ofSlot(a.getPlayerSlot()),
                lane,
                a.getStartSeconds(),
                a.getEndSeconds()));
      }
    }
    return lanes;
  }

  private static Map<Long, List<Double>> kills(
      List<CombatEvent> events, Map<String, Long> slotByHero) {
    Map<Long, List<Double>> kills = new HashMap<>();
    for (CombatEvent e : events) {
      Long victim = slotByHero.get(e.target());
      if (e.kind() != CombatEventKind.DEATH || !e.targetIsRealHero() || victim == null) {
        continue;
      }
      Set<Long> takers = new HashSet<>();
      Long killer = slotByHero.get(e.attacker());
      if (killer != null && e.attackerIsRealHero()) {
        takers.add(killer);
      }
      for (int index = 0; index < 10; index++) {
        if ((e.assistMask() & (1 << index)) != 0) {
          takers.add(PlayerSlots.slot(index));
        }
      }
      for (Long taker : takers) {
        if (TeamSide.ofSlot(taker) != TeamSide.ofSlot(victim)) {
          kills.computeIfAbsent(taker, s -> new ArrayList<>()).add(e.gameSecond());
        }
      }
    }
    return kills;
  }

  private List<CreepHit> creeps(long matchId) {
    List<CreepHit> hits = new ArrayList<>();
    for (CreepKillDomain c : creepRepo.findByMatchId(matchId)) {
      if (c.getPlayerSlot() == null || c.getSeconds() == null) {
        continue;
      }
      Zone.Lane lane =
          ZONE_LANE.equals(c.getZone()) && c.getLane() != null
              ? Enums.find(Zone.Lane.class, c.getLane())
              : null;
      hits.add(
          new CreepHit(
              c.getPlayerSlot(), c.getSeconds(), lane, Boolean.TRUE.equals(c.getDenied())));
    }
    return hits;
  }

  private List<FightFact> fights(long matchId) {
    Map<Integer, Set<Long>> present = new HashMap<>();
    for (FightPlayerDomain p : fightPlayerRepo.findByMatchId(matchId)) {
      Presence presence = Enums.find(Presence.class, p.getPresence());
      if (p.getFightIndex() != null
          && p.getPlayerSlot() != null
          && (presence == Presence.ACTIVE || presence == Presence.PRESENT_PASSIVE)) {
        present.computeIfAbsent(p.getFightIndex(), i -> new HashSet<>()).add(p.getPlayerSlot());
      }
    }
    return fightRepo.findByMatchId(matchId).stream()
        .filter(
            f ->
                f.getFightIndex() != null
                    && f.getStartSeconds() != null
                    && f.getEndSeconds() != null)
        .map(
            f ->
                new FightFact(
                    f.getFightIndex(),
                    f.getStartSeconds(),
                    f.getEndSeconds(),
                    f.getWinner(),
                    present.getOrDefault(f.getFightIndex(), Set.of()),
                    ZONE_LANE.equals(f.getZone()) && f.getLane() != null
                        ? Enums.find(Zone.Lane.class, f.getLane())
                        : null))
        .toList();
  }

  private TeamRotationDomain row(long matchId, Rotation r) {
    AwayTrip trip = r.trip();
    return TeamRotationDomain.builder()
        .matchId(matchId)
        .team(r.team().name())
        .playerSlot(trip.slot())
        .startSeconds(trip.startSeconds())
        .endSeconds(trip.endSeconds())
        .endReason(trip.end().name())
        .fromZone(trip.from().kind().name())
        .fromLane(laneName(trip.from()))
        .toZone(trip.to().kind().name())
        .toLane(laneName(trip.to()))
        .toSide(side(trip))
        .groupZone(trip.groupZone().kind().name())
        .groupLane(laneName(trip.groupZone()))
        .teleported(trip.teleported())
        .laneLeft(r.laneLeft() == null ? null : r.laneLeft().name())
        .kills(r.outcome().kills())
        .creepKills(r.outcome().creepKills())
        .fights(r.outcome().fights())
        .fightsWon(r.outcome().fightsWon())
        .fightIndex(r.outcome().fightIndex())
        .proxyOwnKills(r.proxy().map(LaneProxy.Result::own).orElse(null))
        .proxyEnemyKills(r.proxy().map(LaneProxy.Result::enemy).orElse(null))
        .reasons(r.reasons().stream().map(Enum::name).collect(Collectors.joining(",")))
        .incomeBefore(rounded(r, Income::goldBefore))
        .incomeDuring(rounded(r, Income::goldDuring))
        .xpBefore(rounded(r, Income::xpBefore))
        .xpDuring(rounded(r, Income::xpDuring))
        .verdict(r.verdict().name())
        .analysisVersion(version())
        .build();
  }

  private static Integer rounded(Rotation r, ToDoubleFunction<Income> value) {
    return r.income().map(i -> (int) Math.round(value.applyAsDouble(i))).orElse(null);
  }

  private static String laneName(Zone zone) {
    return zone.lane() == null ? null : zone.lane().name();
  }

  private static String side(AwayTrip trip) {
    Zone.Half own =
        TeamSide.ofSlot(trip.slot()) == TeamSide.RADIANT ? Zone.Half.RADIANT : Zone.Half.DIRE;
    return trip.to().half() == own ? "OWN" : "ENEMY";
  }
}
