package com.ako.dbuff.service.replay.analysis.missedpick;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.ako.dbuff.service.replay.analysis.role.SkillFunctions;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToLongBiFunction;

/**
 * Pairs every skill a player took with the later enemy picks of the same slot kind that did more,
 * like with like. Two skills that both did damage are compared on the damage; two that did none
 * only when both are mainly the same function, on the won important fights each was cast in, and
 * the candidate must have more of them. Any other pair, and any pair with a passive skill, is not
 * comparable. An enemy skill is flagged once, on the last of the taken picks it beat.
 */
final class MissedPickFinder {
  static final String DAMAGE = "DAMAGE";
  static final String FIGHTS = "FIGHTS";
  static final String FIGHTS_SEPARATOR = ":";

  private final double minRatio;
  private final SkillFunctions skills;

  MissedPickFinder(double minRatio, SkillFunctions skills) {
    this.minRatio = minRatio;
    this.skills = skills;
  }

  /** Whether the pick has every field the rule reads; a pick without one is left out. */
  static boolean complete(AbilityDraftPickDomain pick) {
    return pick.getPickOrder() != null
        && pick.getPoolRemainingBeforePick() != null
        && pick.getUltimate() != null
        && pick.getPlayerSlot() != null
        && pick.getTurnNumber() != null
        && pick.getAbilityId() != null
        && pick.getAbilityName() != null;
  }

  /**
   * For each taken pick the one best candidate, by ratio and then by won fights, then each
   * candidate on its last taken pick only. The damage function takes the player slot and the
   * ability id, and gives 0 when unknown.
   */
  List<MissedPickDomain> find(
      long matchId,
      List<AbilityDraftPickDomain> allPicks,
      ToLongBiFunction<Long, Long> damage,
      WonFightCasts casts,
      int version) {
    List<AbilityDraftPickDomain> picks =
        allPicks.stream().filter(MissedPickFinder::complete).toList();
    Comparator<MissedPickDomain> best =
        Comparator.comparing((MissedPickDomain r) -> r.getRatio() == null ? 0.0 : r.getRatio())
            .thenComparing(MissedPickDomain::getCandidateWonFights);
    List<MissedPickDomain> rows = new ArrayList<>();
    for (AbilityDraftPickDomain taken : picks) {
      picks.stream()
          .filter(candidate -> couldHaveTaken(taken, candidate))
          .map(candidate -> compare(matchId, taken, candidate, damage, casts, version))
          .filter(Objects::nonNull)
          .max(best)
          .ifPresent(rows::add);
    }
    return lastPasserOnly(rows);
  }

  private static List<MissedPickDomain> lastPasserOnly(List<MissedPickDomain> rows) {
    Map<String, MissedPickDomain> kept = new LinkedHashMap<>();
    for (MissedPickDomain row : rows) {
      kept.merge(
          TeamSide.ofSlot(row.getCandidateOwnerSlot()) + "/" + row.getCandidateAbility(),
          row,
          (a, b) -> b.getTakenTurn() > a.getTakenTurn() ? b : a);
    }
    return List.copyOf(kept.values());
  }

  /** The enemy took it later (by turn, pick order and pool size) and it fits the same slot kind. */
  private static boolean couldHaveTaken(
      AbilityDraftPickDomain taken, AbilityDraftPickDomain candidate) {
    return TeamSide.ofSlot(candidate.getPlayerSlot()) != TeamSide.ofSlot(taken.getPlayerSlot())
        && candidate.getTurnNumber() > taken.getTurnNumber()
        && candidate.getPickOrder() > taken.getPickOrder()
        && candidate.getPoolRemainingBeforePick() < taken.getPoolRemainingBeforePick()
        && candidate.getUltimate().equals(taken.getUltimate());
  }

  private MissedPickDomain compare(
      long matchId,
      AbilityDraftPickDomain taken,
      AbilityDraftPickDomain candidate,
      ToLongBiFunction<Long, Long> damage,
      WonFightCasts casts,
      int version) {
    long takenDamage = damage.applyAsLong(taken.getPlayerSlot(), taken.getAbilityId());
    long candidateDamage = damage.applyAsLong(candidate.getPlayerSlot(), candidate.getAbilityId());
    int candidateWon = casts.castInWon(candidate.getPlayerSlot(), candidate.getAbilityName());
    int takenWon = casts.castInWon(taken.getPlayerSlot(), taken.getAbilityName());
    if (skills.passive(taken.getAbilityName()) || skills.passive(candidate.getAbilityName())) {
      return null;
    }
    Optional<RoleFunction> main = skills.main(taken.getAbilityName());
    boolean sameMain = main.isPresent() && main.equals(skills.main(candidate.getAbilityName()));
    boolean bothDamage = takenDamage > 0 && candidateDamage > 0;
    boolean damageSkills = bothDamage && main.equals(Optional.of(RoleFunction.DAMAGE)) && sameMain;
    boolean noDamage = !damageSkills;
    String basis = DAMAGE;
    boolean flagged = false;
    if (damageSkills) {
      flagged = candidateWon >= 1 && candidateDamage > takenDamage * minRatio;
    } else if (takenDamage == 0 && candidateDamage == 0 || bothDamage) {
      flagged = sameMain && candidateWon >= 1 && candidateWon > takenWon;
      basis = main.map(f -> FIGHTS + FIGHTS_SEPARATOR + f.name()).orElse(FIGHTS);
    }
    if (!flagged) {
      return null;
    }
    return MissedPickDomain.builder()
        .matchId(matchId)
        .playerSlot(taken.getPlayerSlot())
        .takenAbility(taken.getAbilityName())
        .takenTurn(taken.getTurnNumber())
        .candidateAbility(candidate.getAbilityName())
        .candidateTurn(candidate.getTurnNumber())
        .candidateOwnerSlot(candidate.getPlayerSlot())
        .ultimate(taken.getUltimate())
        .basis(basis)
        .takenDamage(takenDamage)
        .candidateDamage(candidateDamage)
        .ratio(noDamage ? null : (double) candidateDamage / takenDamage)
        .candidateWonFights(candidateWon)
        .enemyWonFights(casts.teamWon(TeamSide.ofSlot(candidate.getPlayerSlot())))
        .takenWonFights(takenWon)
        .analysisVersion(version)
        .build();
  }
}
