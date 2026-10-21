package com.ako.dbuff.service.replay.analysis.missedpick;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.ako.dbuff.service.replay.analysis.role.SkillFunctions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissedPickFinderTest {
  private static final long MINE = 0;
  private static final long ENEMY = 128;

  private final List<AbilityDraftPickDomain> picks = new ArrayList<>();
  private final List<FightImportanceDomain> fights = new ArrayList<>();
  private final List<FightAbilityUseDomain> uses = new ArrayList<>();
  private final Map<String, Long> damage = new HashMap<>();
  private final SkillFunctions skills = mock(SkillFunctions.class);

  private void main(long id, RoleFunction function) {
    when(skills.main("skill_" + id)).thenReturn(Optional.of(function));
  }

  private void passive(long id) {
    when(skills.passive("skill_" + id)).thenReturn(true);
  }

  private void pick(int order, int turn, long slot, long id, boolean ultimate) {
    picks.add(
        AbilityDraftPickDomain.builder()
            .pickOrder(order)
            .turnNumber(turn)
            .playerSlot(slot)
            .abilityId(id)
            .abilityName("skill_" + id)
            .ultimate(ultimate)
            .poolRemainingBeforePick(48 - (order - 1))
            .build());
  }

  private void dealt(long slot, long id, long total) {
    damage.put(slot + "/" + id, total);
  }

  private void fight(int index, String winner, boolean important) {
    fights.add(
        FightImportanceDomain.builder()
            .fightIndex(index)
            .winningTeam(winner)
            .important(important)
            .build());
  }

  private void cast(long slot, long id, int fightIndex) {
    uses.add(
        FightAbilityUseDomain.builder()
            .playerSlot(slot)
            .abilityName("skill_" + id)
            .kind("ABILITY")
            .fightIndex(fightIndex)
            .casts(2)
            .build());
  }

  private List<MissedPickDomain> find() {
    return new MissedPickFinder(1.10, skills)
        .find(
            5L,
            picks,
            (slot, id) -> damage.getOrDefault(slot + "/" + id, 0L),
            new WonFightCasts(fights, uses),
            1);
  }

  private void basicPair(boolean ultimate) {
    pick(1, 1, MINE, 10, ultimate);
    pick(2, 3, ENEMY, 20, ultimate);
    fight(0, "DIRE", true);
  }

  @Test
  void aLaterEnemyPickWithMoreDamageThatWonAnImportantFightIsFlagged() {
    basicPair(false);
    dealt(MINE, 10, 1000);
    dealt(ENEMY, 20, 1380);
    cast(ENEMY, 20, 0);

    List<MissedPickDomain> rows = find();

    assertThat(rows).hasSize(1);
    MissedPickDomain row = rows.getFirst();
    assertThat(row.getPlayerSlot()).isEqualTo(MINE);
    assertThat(row.getTakenTurn()).isEqualTo(1);
    assertThat(row.getCandidateTurn()).isEqualTo(3);
    assertThat(row.getCandidateOwnerSlot()).isEqualTo(ENEMY);
    assertThat(row.getBasis()).isEqualTo("DAMAGE");
    assertThat(row.getRatio()).isEqualTo(1.38);
    assertThat(row.getCandidateWonFights()).isEqualTo(1);
    assertThat(row.getEnemyWonFights()).isEqualTo(1);
  }

  @Test
  void aMarginOfExactlyTheRatioIsNotEnough() {
    basicPair(false);
    dealt(MINE, 10, 1000);
    dealt(ENEMY, 20, 1100);
    cast(ENEMY, 20, 0);

    assertThat(find()).isEmpty();
  }

  @Test
  void aCandidateNeverCastInAFightItsTeamWonIsNotFlagged() {
    basicPair(false);
    dealt(MINE, 10, 1000);
    dealt(ENEMY, 20, 5000);
    fight(1, "RADIANT", true);
    cast(ENEMY, 20, 1);

    assertThat(find()).isEmpty();
  }

  @Test
  void aWonFightThatWasNotImportantDoesNotCount() {
    pick(1, 1, MINE, 10, false);
    pick(2, 3, ENEMY, 20, false);
    fight(0, "DIRE", false);
    dealt(MINE, 10, 1000);
    dealt(ENEMY, 20, 5000);
    cast(ENEMY, 20, 0);

    assertThat(find()).isEmpty();
  }

  @Test
  void aRegularSkillCannotReplaceAnUltimateAndTheReverse() {
    pick(1, 1, MINE, 10, true);
    pick(2, 3, ENEMY, 20, false);
    fight(0, "DIRE", true);
    dealt(ENEMY, 20, 5000);
    cast(ENEMY, 20, 0);

    assertThat(find()).isEmpty();
  }

  @Test
  void onlyLaterPicksOfTheOtherTeamAreCandidates() {
    pick(1, 1, ENEMY, 20, false);
    pick(2, 3, MINE, 10, false);
    pick(3, 5, MINE + 1, 11, false);
    fight(0, "DIRE", true);
    dealt(ENEMY, 20, 5000);
    dealt(MINE + 1, 11, 9000);
    cast(ENEMY, 20, 0);
    cast(MINE + 1, 11, 0);

    assertThat(find()).isEmpty();
  }

  @Test
  void aTurnThatDisagreesWithThePickOrderIsNotTrusted() {
    pick(1, 5, MINE, 10, false);
    pick(2, 3, ENEMY, 20, false);
    fight(0, "DIRE", true);
    dealt(ENEMY, 20, 5000);
    cast(ENEMY, 20, 0);

    assertThat(find()).isEmpty();
  }

  @Test
  void aTakenSkillWithNoDamageIsNotComparedWithADamagingCandidate() {
    basicPair(false);
    dealt(ENEMY, 20, 700);
    cast(ENEMY, 20, 0);

    assertThat(find()).isEmpty();
  }

  @Test
  void onlyTheBestCandidatePerTakenPickIsKept() {
    pick(1, 1, MINE, 10, false);
    pick(2, 3, ENEMY, 20, false);
    pick(3, 5, ENEMY + 1, 21, false);
    fight(0, "DIRE", true);
    dealt(MINE, 10, 1000);
    dealt(ENEMY, 20, 1500);
    dealt(ENEMY + 1, 21, 3000);
    cast(ENEMY, 20, 0);
    cast(ENEMY + 1, 21, 0);

    List<MissedPickDomain> rows = find();

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getCandidateAbility()).isEqualTo("skill_21");
  }

  @Test
  void aPickMissingAFieldIsSkippedAndTheRestStillWork() {
    basicPair(false);
    pick(3, 5, ENEMY + 1, 21, false);
    picks.getLast().setPoolRemainingBeforePick(null);
    pick(4, 7, ENEMY + 2, 22, false);
    picks.getLast().setTurnNumber(null);
    pick(5, 9, ENEMY + 3, 23, false);
    picks.getLast().setUltimate(null);
    dealt(MINE, 10, 1000);
    dealt(ENEMY, 20, 2000);
    dealt(ENEMY + 1, 21, 9000);
    cast(ENEMY, 20, 0);
    cast(ENEMY + 1, 21, 0);

    List<MissedPickDomain> rows = find();

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getCandidateAbility()).isEqualTo("skill_20");
  }

  @Test
  void whenNeitherDidDamageTheWonFightsItWasCastInDecide() {
    basicPair(false);
    main(10, RoleFunction.DISABLE);
    main(20, RoleFunction.DISABLE);
    cast(ENEMY, 20, 0);

    MissedPickDomain row = find().getFirst();

    assertThat(row.getBasis()).isEqualTo("FIGHTS:DISABLE");
    assertThat(row.getCandidateWonFights()).isEqualTo(1);
    assertThat(row.getTakenWonFights()).isZero();
  }

  @Test
  void whenNeitherDidDamageAnEqualRecordIsNotFlagged() {
    basicPair(false);
    main(10, RoleFunction.DISABLE);
    main(20, RoleFunction.DISABLE);
    fight(1, "RADIANT", true);
    cast(ENEMY, 20, 0);
    cast(MINE, 10, 1);

    assertThat(find()).isEmpty();
  }

  @Test
  void aPassiveSkillOnEitherSideIsNotFlagged() {
    basicPair(false);
    dealt(MINE, 10, 1000);
    dealt(ENEMY, 20, 5000);
    cast(ENEMY, 20, 0);
    passive(20);

    assertThat(find()).isEmpty();

    when(skills.passive("skill_20")).thenReturn(false);
    passive(10);

    assertThat(find()).isEmpty();
  }

  @Test
  void aSkillWithNoDamageAgainstADamagingOneIsNotFlaggedEitherWay() {
    basicPair(false);
    main(10, RoleFunction.DISABLE);
    main(20, RoleFunction.DISABLE);
    cast(ENEMY, 20, 0);
    dealt(ENEMY, 20, 900);

    assertThat(find()).isEmpty();

    damage.clear();
    dealt(MINE, 10, 900);

    assertThat(find()).isEmpty();
  }

  @Test
  void twoSkillsWithoutDamageAreComparedOnlyWithTheSameMainFunction() {
    basicPair(false);
    main(10, RoleFunction.DISABLE);
    main(20, RoleFunction.SAVE_HEAL);
    cast(ENEMY, 20, 0);

    assertThat(find()).isEmpty();

    main(20, RoleFunction.DISABLE);

    assertThat(find()).hasSize(1);
  }

  @Test
  void twoSkillsWithoutDamageAndWithoutAKnownFunctionAreNotComparable() {
    basicPair(false);
    cast(ENEMY, 20, 0);

    assertThat(find()).isEmpty();
  }

  @Test
  void anEnemySkillIsFlaggedOnceOnTheLatestTakenPickItBeat() {
    pick(1, 1, MINE, 10, false);
    pick(2, 3, MINE + 1, 11, false);
    pick(3, 5, ENEMY, 20, false);
    fight(0, "DIRE", true);
    dealt(MINE, 10, 1000);
    dealt(MINE + 1, 11, 1000);
    dealt(ENEMY, 20, 3000);
    cast(ENEMY, 20, 0);

    List<MissedPickDomain> rows = find();

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getPlayerSlot()).isEqualTo(MINE + 1);
    assertThat(rows.getFirst().getTakenTurn()).isEqualTo(3);
  }
}
