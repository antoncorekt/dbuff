package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.LanePlayerStatDomain;
import com.ako.dbuff.dao.model.LaneReasonDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.creep.CreepValues;
import com.ako.dbuff.service.replay.analysis.role.LanePosition;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Turns what the earlier analyzers stored into the lane rows: who laned where, for how long and why
 * it ended, what each hero did in his window, who won each lane, and why. It reads nothing but its
 * inputs, so the rules run again on stored data.
 */
final class LaneAssembler {
  private static final String COMMON = "COMMON";
  private static final String TOWER = "TOWER";

  private final LaneProperties properties;
  private final LaneAssigner assigner;
  private final LaningWindows windows;
  private final CreepTally tally;
  private final LaneVerdict verdict;
  private final List<LaneReasonContributor> contributors;

  LaneAssembler(
      LaneProperties properties, CreepValues values, List<LaneReasonContributor> contributors) {
    this.properties = properties;
    this.assigner = new LaneAssigner(properties);
    this.windows = new LaningWindows(properties);
    this.tally = new CreepTally(values, properties);
    this.verdict = new LaneVerdict(properties);
    this.contributors = contributors;
  }

  LaneRows assemble(LaneInputs in, int version) {
    Map<Long, Zone.Lane> lanes = assigner.assign(rosterOnly(in));
    Map<Zone.Lane, Tower> towers = firstTowers(in.buildings());
    List<LaneHero> heroes = new ArrayList<>();
    for (Map.Entry<Long, Zone.Lane> e : new TreeMap<>(lanes).entrySet()) {
      List<ActivitySegment> segments = in.segments().get(e.getKey());
      Tower tower = towers.get(e.getValue());
      windows
          .find(
              segments,
              e.getValue(),
              tower == null ? null : tower.second(),
              segments.get(segments.size() - 1).endSeconds())
          .ifPresent(
              w ->
                  heroes.add(
                      new LaneHero(e.getKey(), in.teams().get(e.getKey()), e.getValue(), w)));
    }
    heroes.removeIf(LaneAssembler::arrivedTooLate);
    clipToContest(heroes);
    heroes.removeIf(LaneAssembler::arrivedTooLate);
    Map<Long, LaneHero> laneHeroes =
        heroes.stream().collect(Collectors.toMap(LaneHero::slot, h -> h));
    Set<Long> withoutLane = new TreeSet<>(in.teams().keySet());
    withoutLane.removeAll(laneHeroes.keySet());
    Map<Long, LaningWindow> windowBySlot = new HashMap<>();
    heroes.forEach(h -> windowBySlot.put(h.slot(), h.window()));
    DamageLedger damage = new DamageLedger(in.events(), in.slotByHero(), windowBySlot);
    List<LaneDeath> deaths = deaths(in);
    Map<Long, HeroLaneStats> stats = new HashMap<>();
    for (LaneHero h : heroes) {
      stats.put(h.slot(), statsOf(in, h, damage, deaths));
    }
    LaneFacts facts =
        new LaneFacts(
            properties,
            in.heroNames(),
            in.roles(),
            in.kits(),
            laneHeroes,
            withoutLane,
            stats,
            damage,
            deaths,
            in.runes());
    List<LanePair> pairs = pairs(heroes);
    List<LaneResultDomain> results = new ArrayList<>();
    List<LaneReasonDomain> reasons = new ArrayList<>();
    List<LanePlayerStatDomain> playerStats = new ArrayList<>();
    for (LanePair pair : pairs) {
      Tower tower = towers.get(pair.lane());
      for (TeamSide team : TeamSide.values()) {
        List<LaneHero> side = pair.side(team);
        if (side.isEmpty()) {
          continue;
        }
        SideTotals own = totals(pair, team, stats, tower);
        SideTotals enemy = totals(pair, LanePair.other(team), stats, tower);
        LaneVerdict.Verdict decided = verdict.decide(own, enemy);
        results.add(resultRow(in.matchId(), pair, team, own, enemy, decided, version));
        playerStats.addAll(playerRows(in.matchId(), pair, side, own, stats, version));
        reasons.addAll(
            reasonRows(
                in.matchId(), pair, team, new LaneContext(facts, pair, team, own, enemy), version));
      }
    }
    return new LaneRows(assignmentRows(in, laneHeroes, version), playerStats, results, reasons);
  }

  private static Map<Long, List<ActivitySegment>> rosterOnly(LaneInputs in) {
    Map<Long, List<ActivitySegment>> segments = new HashMap<>();
    in.segments()
        .forEach(
            (slot, list) -> {
              if (in.teams().containsKey(slot) && !list.isEmpty()) {
                segments.put(slot, list);
              }
            });
    return segments;
  }

  private record Tower(int second, TeamSide owner) {}

  /** The first tower of the configured tier to fall in each lane. */
  private Map<Zone.Lane, Tower> firstTowers(List<BuildingKillContextDomain> buildings) {
    Map<Zone.Lane, Tower> towers = new EnumMap<>(Zone.Lane.class);
    for (BuildingKillContextDomain b : buildings) {
      if (!TOWER.equals(b.getKind())
          || b.getTier() == null
          || b.getTier() != properties.getTowerTier()
          || b.getLane() == null
          || b.getSeconds() == null) {
        continue;
      }
      Zone.Lane lane = Zone.Lane.valueOf(b.getLane());
      Tower fell = new Tower((int) Math.floor(b.getSeconds()), TeamSide.valueOf(b.getOwnerTeam()));
      towers.merge(lane, fell, (a, c) -> a.second() <= c.second() ? a : c);
    }
    return towers;
  }

  /**
   * A hero whose window has no time (he came after the tower fell or the contest ended) was not
   * laning; he is listed as a hero with no lane.
   */
  private static boolean arrivedTooLate(LaneHero hero) {
    return hero.window().length() <= 0;
  }

  /** Ends the window of the side that stayed when the other side had left the lane. */
  private void clipToContest(List<LaneHero> heroes) {
    if (!properties.isEndWhenOpponentsLeft()) {
      return;
    }
    Map<Zone.Lane, Map<TeamSide, Integer>> lastLeft = new EnumMap<>(Zone.Lane.class);
    for (LaneHero h : heroes) {
      lastLeft
          .computeIfAbsent(h.lane(), k -> new EnumMap<>(TeamSide.class))
          .merge(h.team(), h.window().endSeconds(), Math::max);
    }
    heroes.replaceAll(
        h -> {
          Map<TeamSide, Integer> ends = lastLeft.get(h.lane());
          if (ends.size() < 2) {
            return h;
          }
          int contestEnd = Math.min(ends.get(TeamSide.RADIANT), ends.get(TeamSide.DIRE));
          LaningWindow w = h.window();
          if (w.endSeconds() <= contestEnd) {
            return h;
          }
          return new LaneHero(
              h.slot(),
              h.team(),
              h.lane(),
              new LaningWindow(
                  Math.min(w.startSeconds(), contestEnd), contestEnd, LaneEnd.OPPONENTS_LEFT));
        });
  }

  private List<LanePair> pairs(List<LaneHero> heroes) {
    Map<Zone.Lane, List<LaneHero>> byLane = new EnumMap<>(Zone.Lane.class);
    heroes.forEach(h -> byLane.computeIfAbsent(h.lane(), k -> new ArrayList<>()).add(h));
    List<LanePair> pairs = new ArrayList<>();
    byLane.forEach(
        (lane, members) -> {
          int start = members.stream().mapToInt(h -> h.window().startSeconds()).min().orElse(0);
          LaneHero last = lastToLeave(members);
          pairs.add(
              new LanePair(
                  lane,
                  new LaningWindow(start, last.window().endSeconds(), last.window().end()),
                  members));
        });
    return pairs;
  }

  /** The hero whose window ends last; one cut short by the other side leaving ranks after. */
  private static LaneHero lastToLeave(List<LaneHero> heroes) {
    return heroes.stream()
        .max(
            Comparator.<LaneHero>comparingInt(h -> h.window().endSeconds())
                .thenComparing(h -> h.window().end() != LaneEnd.OPPONENTS_LEFT))
        .orElseThrow();
  }

  private List<LaneDeath> deaths(LaneInputs in) {
    return in.deaths().stream()
        .filter(d -> d.getDeathSeconds() != null && d.getPlayerSlot() != null)
        .map(
            d ->
                new LaneDeath(
                    d.getPlayerSlot(),
                    "HERO".equals(d.getKillerKind())
                        ? in.slotByHero().get(d.getKillerName())
                        : null,
                    d.getDeathSeconds()))
        .toList();
  }

  private HeroLaneStats statsOf(
      LaneInputs in, LaneHero hero, DamageLedger damage, List<LaneDeath> deaths) {
    LaningWindow w = hero.window();
    var creeps =
        tally.tally(
            in.creepKills().stream()
                .filter(k -> k.getPlayerSlot() != null && hero.slot() == k.getPlayerSlot())
                .toList(),
            w);
    MinuteSeries xp = in.xp().get(hero.slot());
    MinuteSeries gold = in.gold().get(hero.slot());
    MinuteSeries stacks = in.stacks().get(hero.slot());
    Double stacked = stacks == null ? null : stacks.gain(w.startSeconds(), w.endSeconds());
    return new HeroLaneStats(
        hero.slot(),
        creeps.lastHits(),
        creeps.meleeLastHits(),
        creeps.rangedLastHits(),
        creeps.siegeLastHits(),
        creeps.otherLastHits(),
        creeps.denies(),
        creeps.score(),
        xp == null ? null : xp.gain(w.startSeconds(), w.endSeconds()),
        gold == null ? null : gold.gain(w.startSeconds(), w.endSeconds()),
        levelAt(in.tracks().get(hero.slot()), w.endSeconds()),
        (int)
            deaths.stream()
                .filter(d -> d.killerSlot() != null && d.killerSlot() == hero.slot())
                .filter(d -> w.contains(d.seconds()))
                .count(),
        (int)
            deaths.stream()
                .filter(d -> d.victimSlot() == hero.slot() && w.contains(d.seconds()))
                .count(),
        damage.dealt(hero.slot()),
        damage.taken(hero.slot()),
        damage.towerDamage(hero.slot()),
        stacked == null ? 0 : (int) Math.round(stacked));
  }

  private static int levelAt(ReplayPlayerTrackDomain track, int second) {
    if (track == null
        || track.getLevel() == null
        || track.getLevel().length == 0
        || track.getStartSeconds() == null
        || track.getStepSeconds() == null
        || track.getStepSeconds() <= 0) {
      return 0;
    }
    int index = (second - track.getStartSeconds()) / track.getStepSeconds();
    return track.getLevel()[Math.max(0, Math.min(track.getLevel().length - 1, index))];
  }

  private SideTotals totals(
      LanePair pair, TeamSide team, Map<Long, HeroLaneStats> stats, Tower tower) {
    List<HeroLaneStats> side = pair.side(team).stream().map(h -> stats.get(h.slot())).toList();
    boolean fell = tower != null && tower.second() <= pair.window().endSeconds();
    return SideTotals.of(team, side, fell && tower.owner() == team, fell && tower.owner() != team);
  }

  private List<LaneAssignmentDomain> assignmentRows(
      LaneInputs in, Map<Long, LaneHero> laneHeroes, int version) {
    List<LaneAssignmentDomain> rows = new ArrayList<>();
    for (Map.Entry<Long, TeamSide> e : new TreeMap<>(in.teams()).entrySet()) {
      LaneHero hero = laneHeroes.get(e.getKey());
      LaneAssignmentDomain.LaneAssignmentDomainBuilder row =
          LaneAssignmentDomain.builder()
              .matchId(in.matchId())
              .playerSlot(e.getKey())
              .team(e.getValue().name())
              .analysisVersion(version);
      if (hero != null) {
        row.lane(hero.lane().name())
            .pairId(hero.lane().ordinal() + 1)
            .position(LanePosition.of(hero.team(), hero.lane()).name())
            .startSeconds(hero.window().startSeconds())
            .endSeconds(hero.window().endSeconds())
            .endReason(hero.window().end().name());
      }
      rows.add(row.build());
    }
    return rows;
  }

  private LaneResultDomain resultRow(
      long matchId,
      LanePair pair,
      TeamSide team,
      SideTotals own,
      SideTotals enemy,
      LaneVerdict.Verdict decided,
      int version) {
    List<LaneHero> side = pair.side(team);
    LaneHero last = lastToLeave(side);
    return LaneResultDomain.builder()
        .matchId(matchId)
        .pairId(pair.id())
        .lane(pair.lane().name())
        .team(team.name())
        .position(LanePosition.of(team, pair.lane()).name())
        .heroes(own.heroes())
        .enemyHeroes(enemy.heroes())
        .startSeconds(pair.window().startSeconds())
        .endSeconds(pair.window().endSeconds())
        .endReason(pair.window().end().name())
        .sideEndSeconds(last.window().endSeconds())
        .sideEndReason(last.window().end().name())
        .creepScore(own.creepScore())
        .lastHits(own.lastHits())
        .siegeLastHits(own.siegeLastHits())
        .denies(own.denies())
        .xp(own.xp())
        .gold(own.gold())
        .level(own.averageLevel() == 0 ? null : own.averageLevel())
        .kills(own.kills())
        .deaths(own.deaths())
        .heroDamageDealt(own.damageDealt())
        .heroDamageTaken(own.damageTaken())
        .towerDamage(own.towerDamage())
        .ownTowerFell(own.ownTowerFell())
        .enemyTowerFell(own.enemyTowerFell())
        .result(decided.result().name())
        .margin(decided.margin())
        .analysisVersion(version)
        .build();
  }

  private List<LanePlayerStatDomain> playerRows(
      long matchId,
      LanePair pair,
      List<LaneHero> side,
      SideTotals own,
      Map<Long, HeroLaneStats> stats,
      int version) {
    return side.stream()
        .map(h -> stats.get(h.slot()))
        .map(
            s ->
                LanePlayerStatDomain.builder()
                    .matchId(matchId)
                    .playerSlot(s.slot())
                    .pairId(pair.id())
                    .lastHits(s.lastHits())
                    .meleeLastHits(s.meleeLastHits())
                    .rangedLastHits(s.rangedLastHits())
                    .siegeLastHits(s.siegeLastHits())
                    .otherLastHits(s.otherLastHits())
                    .denies(s.denies())
                    .creepScore(s.creepScore())
                    .sideShare(
                        own.creepScore() > 0
                            ? s.creepScore() / own.creepScore()
                            : 1.0 / side.size())
                    .xp(s.xp())
                    .gold(s.gold())
                    .level(s.level() == 0 ? null : s.level())
                    .kills(s.kills())
                    .deaths(s.deaths())
                    .heroDamageDealt(s.damageDealt())
                    .heroDamageTaken(s.damageTaken())
                    .towerDamage(s.towerDamage())
                    .stacks(s.stacks())
                    .analysisVersion(version)
                    .build())
        .toList();
  }

  /** Where two contributors give the same code for the same hero, the more severe one is kept. */
  private List<LaneReasonDomain> reasonRows(
      long matchId, LanePair pair, TeamSide team, LaneContext context, int version) {
    Map<String, LaneReason> byKey = new LinkedHashMap<>();
    for (LaneReasonContributor contributor : contributors) {
      for (LaneReason reason : contributor.contribute(context)) {
        byKey.merge(
            reason.code() + "/" + reason.subjectSlot(),
            reason,
            (a, b) -> a.severity() >= b.severity() ? a : b);
      }
    }
    List<LaneReason> ranked =
        byKey.values().stream()
            .filter(r -> r.severity() >= properties.getMinSeverity())
            .sorted(
                Comparator.comparingDouble(LaneReason::severity)
                    .reversed()
                    .thenComparing(LaneReason::code))
            .limit(properties.getReasonsPerSide())
            .toList();
    List<LaneReasonDomain> rows = new ArrayList<>();
    for (int i = 0; i < ranked.size(); i++) {
      LaneReason r = ranked.get(i);
      rows.add(
          LaneReasonDomain.builder()
              .matchId(matchId)
              .pairId(pair.id())
              .team(team.name())
              .rank(i + 1)
              .code(r.code())
              .direction(r.direction().name())
              .subjectSlot(r.subjectSlot())
              .severity(r.severity())
              .evidence(r.evidence())
              .source(COMMON)
              .analysisVersion(version)
              .build());
    }
    return rows;
  }
}
