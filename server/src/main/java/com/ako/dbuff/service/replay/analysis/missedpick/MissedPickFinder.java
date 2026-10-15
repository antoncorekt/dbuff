package com.ako.dbuff.service.replay.analysis.missedpick;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongBiFunction;

/**
 * Pairs every skill a player took with the later enemy picks of the same slot kind that did more.
 * Damage is the skill's total in the match; a pair where neither did damage is compared on the won
 * important fights each was cast in, and the candidate must have more of them.
 */
final class MissedPickFinder {
  static final String DAMAGE = "DAMAGE";
  static final String FIGHTS = "FIGHTS";

  private final double minRatio;

  MissedPickFinder(double minRatio) {
    this.minRatio = minRatio;
  }

  /** The damage function takes the player slot and the ability id, and gives 0 when unknown. */
  List<MissedPickDomain> find(
      long matchId,
      List<AbilityDraftPickDomain> picks,
      ToLongBiFunction<Long, Long> damage,
      WonFightCasts casts,
      int version) {
    List<MissedPickDomain> rows = new ArrayList<>();
    for (AbilityDraftPickDomain taken : picks) {
      for (AbilityDraftPickDomain candidate : picks) {
        if (!couldHaveTaken(taken, candidate)) {
          continue;
        }
        MissedPickDomain row = compare(matchId, taken, candidate, damage, casts, version);
        if (row != null) {
          rows.add(row);
        }
      }
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
    boolean flagged =
        candidateWon >= 1
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
        .ratio(takenDamage > 0 ? (double) candidateDamage / takenDamage : null)
        .candidateWonFights(candidateWon)
        .enemyWonFights(casts.teamWon(TeamSide.ofSlot(candidate.getPlayerSlot())))
        .takenWonFights(takenWon)
        .analysisVersion(version)
        .build();
  }
}
