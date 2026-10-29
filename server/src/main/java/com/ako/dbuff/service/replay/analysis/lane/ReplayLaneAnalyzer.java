package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.CreepKillRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.LanePlayerStatRepo;
import com.ako.dbuff.dao.repo.LaneReasonRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.RunePickupRepo;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.analysis.creep.CreepValues;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The laning stage of each lane: which heroes laned where and until when, what each did in that
 * window, who won each lane and the reasons. It reads the activity, creep kills, runes, roles,
 * deaths and building kills the earlier analyzers stored, plus the combat log for hero damage, and
 * writes {@code lane_assignment}, {@code lane_player_stat}, {@code lane_result} and {@code
 * lane_reason}. Not to be mixed up with the OpenDota based lane report of the match report.
 */
@Slf4j
@Order(47)
@Component
public class ReplayLaneAnalyzer implements ReplayAnalyzer {
  private final LaneAssembler assembler;
  private final StoredActivity activity;
  private final ReplayPlayerRepo playerRepo;
  private final ReplayPlayerTrackRepo trackRepo;
  private final CreepKillRepo creepRepo;
  private final BuildingKillContextRepo buildingRepo;
  private final DeathAnalysisRepo deathRepo;
  private final RunePickupRepo runeRepo;
  private final PlayerRoleRepo roleRepo;
  private final PlayerTimelineRepo timelineRepo;
  private final PlayerGameStatisticRepo statisticRepo;
  private final CombatLogReader combatLog;
  private final ReportHeroNames heroNames;
  private final HeroKits kits;
  private final LaneAssignmentRepo assignmentRepo;
  private final LanePlayerStatRepo statRepo;
  private final LaneResultRepo resultRepo;
  private final LaneReasonRepo reasonRepo;

  public ReplayLaneAnalyzer(
      LaneProperties properties,
      CreepValues values,
      List<LaneReasonContributor> contributors,
      StoredActivity activity,
      ReplayPlayerRepo playerRepo,
      ReplayPlayerTrackRepo trackRepo,
      CreepKillRepo creepRepo,
      BuildingKillContextRepo buildingRepo,
      DeathAnalysisRepo deathRepo,
      RunePickupRepo runeRepo,
      PlayerRoleRepo roleRepo,
      PlayerTimelineRepo timelineRepo,
      PlayerGameStatisticRepo statisticRepo,
      CombatLogReader combatLog,
      ReportHeroNames heroNames,
      HeroKits kits,
      LaneAssignmentRepo assignmentRepo,
      LanePlayerStatRepo statRepo,
      LaneResultRepo resultRepo,
      LaneReasonRepo reasonRepo) {
    this.assembler = new LaneAssembler(properties, values, contributors);
    this.activity = activity;
    this.playerRepo = playerRepo;
    this.trackRepo = trackRepo;
    this.creepRepo = creepRepo;
    this.buildingRepo = buildingRepo;
    this.deathRepo = deathRepo;
    this.runeRepo = runeRepo;
    this.roleRepo = roleRepo;
    this.timelineRepo = timelineRepo;
    this.statisticRepo = statisticRepo;
    this.combatLog = combatLog;
    this.heroNames = heroNames;
    this.kits = kits;
    this.assignmentRepo = assignmentRepo;
    this.statRepo = statRepo;
    this.resultRepo = resultRepo;
    this.reasonRepo = reasonRepo;
  }

  @Override
  public String name() {
    return "lanes";
  }

  @Override
  public int version() {
    return 3;
  }

  @Override
  public Set<String> requires() {
    return Set.of(
        "players",
        "tracks",
        "activity",
        "combat-log",
        "creep-kills",
        "runes",
        "roles",
        "deaths",
        "building-kills");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    var segments = activity.bySlot(matchId);
    if (segments.isEmpty()) {
      log.info("Match {} has no stored activity, no lanes stored", matchId);
      return;
    }
    LaneRows rows = assembler.assemble(inputs(matchId, segments), version());
    assignmentRepo.saveAll(rows.assignments());
    statRepo.saveAll(rows.playerStats());
    resultRepo.saveAll(rows.results());
    reasonRepo.saveAll(rows.reasons());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    assignmentRepo.deleteByMatchId(matchId);
    statRepo.deleteByMatchId(matchId);
    resultRepo.deleteByMatchId(matchId);
    reasonRepo.deleteByMatchId(matchId);
  }

  private LaneInputs inputs(long matchId, Map<Long, List<ActivitySegment>> segments) {
    List<CombatEvent> events = combatLog.events(matchId);
    Set<String> combatNames = CombatHeroes.names(events);
    Map<Long, TeamSide> teams = new HashMap<>();
    Map<String, Long> slotByHero = new HashMap<>();
    for (ReplayPlayerDomain player : playerRepo.findByMatchId(matchId)) {
      if (player.getPlayerSlot() == null) {
        continue;
      }
      teams.put(player.getPlayerSlot(), TeamSide.ofSlot(player.getPlayerSlot()));
      String hero = CombatHeroes.nameOf(player, combatNames);
      if (hero != null) {
        slotByHero.putIfAbsent(hero, player.getPlayerSlot());
      }
    }
    Map<Long, ReplayPlayerTrackDomain> tracks =
        trackRepo.findByMatchId(matchId).stream()
            .filter(t -> t.getPlayerSlot() != null)
            .collect(
                Collectors.toMap(
                    ReplayPlayerTrackDomain::getPlayerSlot, Function.identity(), (a, b) -> a));
    List<PlayerTimelineDomain> timeline = timelineRepo.findByMatchId(matchId);
    return new LaneInputs(
        matchId,
        teams,
        segments,
        buildingRepo.findByMatchId(matchId),
        creepRepo.findByMatchId(matchId),
        tracks,
        TimelineSeries.bySlot(timeline, PlayerTimelineDomain::getXp),
        TimelineSeries.bySlot(timeline, PlayerTimelineDomain::getGold),
        TimelineSeries.bySlot(timeline, PlayerTimelineDomain::getCampsStacked),
        events,
        slotByHero,
        deathRepo.findByMatchId(matchId),
        runeRepo.findByMatchId(matchId),
        heroNames.bySlot(matchId),
        roleRepo.findByMatchId(matchId).stream()
            .filter(r -> r.getPlayerSlot() != null && r.getRole() != null)
            .collect(
                Collectors.toMap(
                    PlayerRoleDomain::getPlayerSlot, PlayerRoleDomain::getRole, (a, b) -> a)),
        heroKits(matchId));
  }

  private Map<Long, HeroConstant> heroKits(long matchId) {
    Map<Long, HeroConstant> byHero = new HashMap<>();
    for (PlayerMatchStatisticDomain s : statisticRepo.findAllByMatchId(matchId)) {
      if (s.getPlayerSlot() != null && s.getHeroId() != null) {
        kits.hero(s.getHeroId()).ifPresent(h -> byHero.put(s.getPlayerSlot(), h));
      }
    }
    return byHero;
  }
}
