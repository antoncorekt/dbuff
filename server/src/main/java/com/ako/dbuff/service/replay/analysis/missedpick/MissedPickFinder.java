package com.ako.dbuff.service.replay.analysis.missedpick;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongBiFunction;

/**
 * Pairs every skill a player took with the later enemy picks of the same slot kind that did more.
 * Damage is the skill's total in the match and the taken skill must have done some; a pair where
 * neither did damage is compared on the won important fights each was cast in, and the candidate
 * must have more of them. A taken skill with no damage against a damaging one is not comparable.
 */
final class MissedPickFinder {
  static final String DAMAGE = "DAMAGE";
  static final String FIGHTS = "FIGHTS";

  private final double minRatio;

  MissedPickFinder(double minRatio) {
    this.minRatio = minRatio;
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
   * For each taken pick the one best candidate, by ratio and then by won fights. The damage
   * function takes the player slot and the ability id, and gives 0 when unknown.
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
    return rows;
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
    boolean noDamage = takenDamage == 0 && candidateDamage == 0;
    boolean comparable = takenDamage > 0 || noDamage;
    boolean flagged =
        comparable
            && candidateWon >= 1
            && (noDamage ? candidateWon > takenWon : candidateDamage > takenDamage * minRatio);
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
        .basis(noDamage ? FIGHTS : DAMAGE)
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
