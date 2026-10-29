package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the role (1 carry to 5 hard support) of each player, resolved per game mode by a {@link
 * RoleResolver}, and the function profile behind each role stage the resolver produces. The final
 * role goes to {@code player_role}; every stage goes to {@code player_role_profile}. It reads
 * OpenDota's statistics and net worth and the activity stored before it, so it runs after the
 * activity and before the analyzers that will use roles. A mode without a resolver gets no roles.
 */
@Slf4j
@Order(15)
@Component
@RequiredArgsConstructor
public class PlayerRoleAnalyzer implements ReplayAnalyzer {
  private static final int TEAM_SIZE = 5;

  private final List<RoleResolver> resolvers;
  private final StoredMatchModes modes;
  private final PlayerGameStatisticRepo statisticRepo;
  private final PlayerTimelineRepo timelineRepo;
  private final StoredActivity storedActivity;
  private final PlayerRoleRepo roleRepo;
  private final PlayerRoleProfileRepo profileRepo;
  private final AbilityDraftPickRepo draftPickRepo;
  private final StageProfilers profilers;
  private final RoleFitter fitter;

  @Override
  public String name() {
    return "roles";
  }

  @Override
  public int version() {
    return 6;
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return resolvers.stream().anyMatch(r -> r.supports(mode));
  }

  @Override
  public Set<String> requires() {
    return Set.of("activity");
  }

  /** The extractors of the resolver of that mode only, so another mode's inputs do not count. */
  @Override
  public Set<String> requires(GameModeProfile mode) {
    Set<String> required = new HashSet<>(requires());
    resolverFor(mode).ifPresent(r -> required.addAll(r.requires()));
    return required;
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    GameModeProfile mode = modes.activeFor(matchId).orElse(null);
    RoleResolver resolver = mode == null ? null : resolverFor(mode).orElse(null);
    if (resolver == null) {
      return;
    }
    Map<Long, List<ActivitySegment>> activity = storedActivity.bySlot(matchId);
    List<PlayerMatchStatisticDomain> stats = statisticRepo.findAllByMatchId(matchId);
    Map<Long, double[]> series = netWorthSeries(matchId, stats);
    Map<Long, List<DraftedAbility>> drafted =
        resolver.stages().contains(RoleStage.INTENT) ? draftedAbilities(matchId) : Map.of();
    List<RolePlayer> players =
        stats.stream()
            .sorted(Comparator.comparing(PlayerMatchStatisticDomain::getPlayerSlot))
            .map(
                s ->
                    new RolePlayer(
                        s.getPlayerSlot(),
                        s.getLaneRole(),
                        s.getNetWorth(),
                        series.get(s.getPlayerSlot()),
                        activity.getOrDefault(s.getPlayerSlot(), List.of()),
                        s.getHeroId(),
                        drafted.getOrDefault(s.getPlayerSlot(), List.of()),
                        PlayerOutput.from(s)))
            .toList();
    List<PlayerRoleDomain> rows = new ArrayList<>();
    List<PlayerRoleProfileDomain> profiles = new ArrayList<>();
    for (boolean dire : new boolean[] {false, true}) {
      List<RolePlayer> team =
          players.stream().filter(p -> (p.slot() >= TeamSide.FIRST_DIRE_SLOT) == dire).toList();
      if (!isTeam(team, dire)) {
        log.info(
            "Match {} has stored slots {} on the {} team, no roles for it",
            matchId,
            team.stream().map(RolePlayer::slot).toList(),
            dire ? "dire" : "radiant");
        continue;
      }
      for (RoleStage stage : resolver.stages().stream().sorted().toList()) {
        RoleContext context = RoleContext.profiledBy(stage, team, profilers.of(stage));
        List<RoleAssignment> assignments;
        try {
          assignments = resolver.resolve(context);
        } catch (RuntimeException e) {
          log.warn("Match {}: the {} roles failed, the other stages are kept", matchId, stage, e);
          continue;
        }
        if (assignments.isEmpty()) {
          continue;
        }
        if (stage == RoleStage.FINAL) {
          assignments.forEach(a -> rows.add(row(matchId, a)));
        }
        try {
          profiles.addAll(profileRows(matchId, stage, team, assignments, context, resolver));
        } catch (RuntimeException e) {
          log.warn("Match {}: no {} profiles, the roles are kept", matchId, stage, e);
        }
      }
    }
    roleRepo.saveAll(rows);
    profileRepo.saveAll(profiles);
  }

  private Optional<RoleResolver> resolverFor(GameModeProfile mode) {
    return resolvers.stream().filter(r -> r.supports(mode)).findFirst();
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    roleRepo.deleteByMatchId(matchId);
    profileRepo.deleteByMatchId(matchId);
  }

  private List<PlayerRoleProfileDomain> profileRows(
      long matchId,
      RoleStage stage,
      List<RolePlayer> team,
      List<RoleAssignment> assignments,
      RoleContext context,
      RoleResolver resolver) {
    StageProfiler profiler = profilers.of(stage);
    Map<Long, RoleAssignment> bySlot = new HashMap<>();
    assignments.forEach(a -> bySlot.put(a.slot(), a));
    List<FunctionProfile> profiles = context.profiles(profiler);
    List<Long> slots = team.stream().map(RolePlayer::slot).toList();
    List<Long> uncovered = slots.stream().filter(s -> !bySlot.containsKey(s)).toList();
    List<Long> foreign = bySlot.keySet().stream().filter(s -> !slots.contains(s)).sorted().toList();
    if (!uncovered.isEmpty() || !foreign.isEmpty()) {
      log.info(
          "Match {}, {}: the resolver gave no role for slots {} and roles for unknown slots {};"
              + " those have no profile row",
          matchId,
          stage,
          uncovered,
          foreign);
    }
    List<PlayerRoleProfileDomain> rows = new ArrayList<>();
    for (int i = 0; i < team.size(); i++) {
      RoleAssignment assignment = bySlot.get(team.get(i).slot());
      if (assignment == null) {
        continue;
      }
      FunctionProfile p = profiles.get(i);
      double[] scores = fitter.scores(p, resolver.scoredAs(stage));
      rows.add(
          PlayerRoleProfileDomain.builder()
              .matchId(matchId)
              .playerSlot(assignment.slot())
              .stage(stage.name())
              .role(assignment.role())
              .confidence(assignment.confidence())
              .lane(p.lane() == null ? null : p.lane().name())
              .farmPriority(p.farmPriority())
              .jungle(p.jungle())
              .roaming(p.roaming())
              .visionDuty(p.visionDuty())
              .initiation(p.initiation())
              .disable(p.disable())
              .saveHeal(p.saveHeal())
              .damage(p.damage())
              .durability(p.durability())
              .escape(p.escape())
              .fitRole(RoleFitter.bestRole(scores))
              .roleScores(scoreText(scores))
              .evidence(assignment.evidence())
              .analysisVersion(version())
              .build());
    }
    return rows;
  }

  private static String scoreText(double[] scores) {
    return Arrays.stream(scores)
        .mapToObj(s -> String.format(Locale.ROOT, "%.2f", s))
        .collect(Collectors.joining(","));
  }

  private Map<Long, List<DraftedAbility>> draftedAbilities(long matchId) {
    return draftPickRepo.findByMatchId(matchId).stream()
        .filter(p -> p.getAbilityName() != null && p.getPlayerSlot() != null)
        .sorted(Comparator.comparing(AbilityDraftPickDomain::getPickOrder))
        .collect(
            Collectors.groupingBy(
                AbilityDraftPickDomain::getPlayerSlot,
                Collectors.mapping(
                    p ->
                        new DraftedAbility(
                            p.getAbilityName(), Boolean.TRUE.equals(p.getUltimate())),
                    Collectors.toList())));
  }

  private PlayerRoleDomain row(long matchId, RoleAssignment assignment) {
    return PlayerRoleDomain.builder()
        .matchId(matchId)
        .playerSlot(assignment.slot())
        .role(assignment.role())
        .source(assignment.source().name())
        .confidence(assignment.confidence())
        .evidence(assignment.evidence())
        .analysisVersion(version())
        .build();
  }

  private static boolean isTeam(List<RolePlayer> team, boolean dire) {
    long first = dire ? TeamSide.FIRST_DIRE_SLOT : 0;
    return team.size() == TEAM_SIZE
        && team.stream()
            .map(RolePlayer::slot)
            .collect(Collectors.toSet())
            .equals(LongStream.range(first, first + TEAM_SIZE).boxed().collect(Collectors.toSet()));
  }

  /**
   * Net worth by minute for each slot with any stored; a minute without a value takes the latest
   * one before it, and NaN stands before the first. One line says what was missing or filled.
   */
  private Map<Long, double[]> netWorthSeries(long matchId, List<PlayerMatchStatisticDomain> stats) {
    Map<Long, Map<Integer, Long>> worth = new HashMap<>();
    Map<Long, Integer> lastMinute = new HashMap<>();
    for (PlayerTimelineDomain row : timelineRepo.findByMatchId(matchId)) {
      if (row.getGameMinute() == null || row.getPlayerSlot() == null) {
        continue;
      }
      lastMinute.merge(row.getPlayerSlot(), row.getGameMinute(), Math::max);
      if (row.getNetworth() != null) {
        worth
            .computeIfAbsent(row.getPlayerSlot(), s -> new HashMap<>())
            .put(row.getGameMinute(), row.getNetworth());
      }
    }
    Map<Long, double[]> series = new HashMap<>();
    int filled = 0;
    for (Map.Entry<Long, Map<Integer, Long>> e : worth.entrySet()) {
      double[] minutes = new double[lastMinute.get(e.getKey()) + 1];
      double latest = Double.NaN;
      boolean gap = false;
      for (int minute = 0; minute < minutes.length; minute++) {
        Long value = e.getValue().get(minute);
        if (value != null) {
          latest = value;
        } else {
          gap = true;
        }
        minutes[minute] = latest;
      }
      filled += gap ? 1 : 0;
      series.put(e.getKey(), minutes);
    }
    long without = stats.stream().filter(s -> !series.containsKey(s.getPlayerSlot())).count();
    if (without > 0 || filled > 0) {
      log.info(
          "Match {}: net worth series missing for {} players and filled for {}",
          matchId,
          without,
          filled);
    }
    return series;
  }
}
