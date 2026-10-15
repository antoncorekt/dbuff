package com.ako.dbuff.service.replay.analysis.mid;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.MidActivityDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.MidActivityRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one {@code mid_activity} row for the final role 2 hero of each team: when he reached level
 * 6, what he did next, and in the mid game (from the end of the lane stage on) his kills, fights,
 * rotations and income against the enemy mid and his own team. A team without a role 2 hero gets no
 * row. Rotations, activity and the lane stage are soft inputs: without them he is taken to have
 * stayed mid and the window starts at the fallback minute.
 */
@Slf4j
@Order(97)
@Component
@RequiredArgsConstructor
public class MidActivityAnalyzer implements ReplayAnalyzer {
  private static final int MID_ROLE = 2;

  private final PlayerRoleRepo roleRepo;
  private final ReplayPlayerRepo playerRepo;
  private final CombatLogReader combatLog;
  private final MidInputsLoader inputsLoader;
  private final MidActivityProperties properties;
  private final MidActivityRepo midRepo;

  @Override
  public String name() {
    return "mid-activity";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("roles", "players", "tracks", "combat-log", "fights");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    List<CombatEvent> events = combatLog.events(matchId);
    Map<String, Integer> playerOf =
        CombatHeroes.playerIndexByHero(events, playerRepo.findByMatchId(matchId));
    if (playerOf.isEmpty()) {
      log.info("Match {} has no combat log heroes, so no mid activity", matchId);
      return;
    }
    Map<TeamSide, Long> mids = midSlots(matchId);
    for (TeamSide team : TeamSide.values()) {
      if (!mids.containsKey(team)) {
        log.info("Match {} has no mid hero for {}, so no mid activity row for it", matchId, team);
      }
    }
    if (mids.isEmpty()) {
      return;
    }
    MidInputs inputs = inputsLoader.load(matchId, MidTally.of(events, playerOf));
    midRepo.saveAll(
        mids.entrySet().stream()
            .map(
                e ->
                    row(matchId, e.getKey(), e.getValue(), mids.get(e.getKey().opponent()), inputs))
            .toList());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    midRepo.deleteByMatchId(matchId);
  }

  private Map<TeamSide, Long> midSlots(long matchId) {
    return roleRepo.findByMatchId(matchId).stream()
        .filter(r -> r.getRole() != null && r.getRole() == MID_ROLE && r.getPlayerSlot() != null)
        .sorted(Comparator.comparing(PlayerRoleDomain::getPlayerSlot))
        .collect(
            Collectors.toMap(
                r -> TeamSide.ofSlot(r.getPlayerSlot()),
                PlayerRoleDomain::getPlayerSlot,
                (first, second) -> first));
  }

  private MidActivityDomain row(
      long matchId, TeamSide team, long slot, Long enemySlot, MidInputs in) {
    int index = PlayerSlots.index(slot);
    List<FightDomain> joinedFights = in.fightsJoinedBy(slot);
    Integer level6 = level6Seconds(in.traces().get(index));
    List<TeamRotationDomain> rotations =
        in.rotations().stream().filter(r -> Objects.equals(r.getPlayerSlot(), slot)).toList();
    MidMove move =
        level6 == null
            ? null
            : new MidMoveFinder(properties)
                .find(
                    level6,
                    rotations,
                    in.activity().getOrDefault(slot, List.of()),
                    joinedFights.stream().map(FightDomain::getStartSeconds).toList());
    Integer killsAfter =
        move == null || move.seconds() == null
            ? null
            : (int)
                MidTally.count(
                    in.tally().kills().get(index),
                    move.seconds(),
                    move.seconds() + properties.getKillWindowSeconds());
    int joined = joinedFights.size();
    double median = median(team, in);
    List<TeamRotationDomain> inWindow =
        rotations.stream()
            .filter(r -> r.getStartSeconds() != null)
            .filter(
                r ->
                    r.getStartSeconds() >= in.windowStart() && r.getStartSeconds() < in.windowEnd())
            .toList();
    Double goldPm = in.income().goldPerMinute(slot, in.windowStart(), in.windowEnd());
    Double xpPm = in.income().xpPerMinute(slot, in.windowStart(), in.windowEnd());
    Double enemyXpPm =
        enemySlot == null
            ? null
            : in.income().xpPerMinute(enemySlot, in.windowStart(), in.windowEnd());
    Double enemyGoldPm =
        enemySlot == null
            ? null
            : in.income().goldPerMinute(enemySlot, in.windowStart(), in.windowEnd());
    MidVerdict verdict =
        new MidVerdictRule(properties)
            .verdict(
                new MidVerdictRule.Facts(
                    move == null ? null : move.next(),
                    killsAfter,
                    joined,
                    median,
                    xpPm,
                    enemyXpPm));
    return MidActivityDomain.builder()
        .matchId(matchId)
        .playerSlot(slot)
        .team(team.name())
        .level6Seconds(level6)
        .nextAction(move == null ? null : move.next().name())
        .leaveSeconds(move == null ? null : move.seconds())
        .nextPlace(move == null ? null : move.place())
        .killsAfterLeaving(killsAfter)
        .windowStartSeconds(in.windowStart())
        .windowEndSeconds(in.windowEnd())
        .kills(
            (int) MidTally.count(in.tally().kills().get(index), in.windowStart(), in.windowEnd()))
        .assists(
            (int) MidTally.count(in.tally().assists().get(index), in.windowStart(), in.windowEnd()))
        .fightsJoined(joined)
        .fightsTotal(in.windowFights().size())
        .teamMedianFights(median)
        .rotations(inWindow.size())
        .rotationsGood((int) inWindow.stream().filter(r -> "GOOD".equals(r.getVerdict())).count())
        .goldPerMinute(rounded(goldPm))
        .xpPerMinute(rounded(xpPm))
        .enemyMidSlot(enemySlot)
        .enemyGoldPerMinute(rounded(enemyGoldPm))
        .enemyXpPerMinute(rounded(enemyXpPm))
        .teamGoldPerMinute(rounded(teamAverage(team, in, MidIncome::goldPerMinute)))
        .teamXpPerMinute(rounded(teamAverage(team, in, MidIncome::xpPerMinute)))
        .verdict(verdict.name())
        .analysisVersion(version())
        .build();
  }

  private static Integer level6Seconds(Trace trace) {
    if (trace == null) {
      return null;
    }
    for (int i = 0; i < trace.level().length; i++) {
      if (trace.level()[i] >= 6) {
        return trace.startSeconds() + i * trace.stepSeconds();
      }
    }
    return null;
  }

  private static double median(TeamSide team, MidInputs in) {
    List<Long> counts =
        in.teamOf().entrySet().stream()
            .filter(e -> e.getValue() == team)
            .map(e -> (long) in.fightsJoinedBy(e.getKey()).size())
            .sorted()
            .toList();
    int n = counts.size();
    if (n == 0) {
      return 0;
    }
    return n % 2 == 1 ? counts.get(n / 2) : (counts.get(n / 2 - 1) + counts.get(n / 2)) / 2.0;
  }

  @FunctionalInterface
  private interface Rate {
    Double of(MidIncome income, long slot, int from, int to);
  }

  private static Double teamAverage(TeamSide team, MidInputs in, Rate rate) {
    List<Double> rates =
        in.teamOf().entrySet().stream()
            .filter(e -> e.getValue() == team)
            .map(e -> rate.of(in.income(), e.getKey(), in.windowStart(), in.windowEnd()))
            .filter(Objects::nonNull)
            .toList();
    return rates.isEmpty()
        ? null
        : rates.stream().mapToDouble(Double::doubleValue).average().orElse(0);
  }

  private static Integer rounded(Double value) {
    return value == null ? null : (int) Math.round(value);
  }
}
