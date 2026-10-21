package com.ako.dbuff.service.replay.analysis.missedpick;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MissedPickRepo;
import com.ako.dbuff.dao.repo.SkillCountRepository;
import com.ako.dbuff.dao.repo.SkillCountRepository.DamageAverage;
import com.ako.dbuff.service.replay.analysis.role.SkillFunctions;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
@Import({MissedPickAnalyzer.class, MissedPickProperties.class, AbilityDraftProfile.class})
class MissedPickAnalyzerTest {
  private static final long MATCH = 88L;

  @Autowired private MissedPickAnalyzer analyzer;
  @Autowired private AbilityDraftPickRepo pickRepo;
  @Autowired private AbilityRepo abilityRepo;
  @Autowired private FightImportanceRepo importanceRepo;
  @Autowired private FightAbilityUseRepo useRepo;
  @Autowired private MissedPickRepo missedPickRepo;
  @MockBean private SkillFunctions skillFunctions;
  @MockBean private MatchRepo matchRepo;
  @MockBean private SkillCountRepository countRepository;

  private void pick(int order, Integer turn, long slot, long id) {
    pickRepo.save(
        AbilityDraftPickDomain.builder()
            .matchId(MATCH)
            .pickOrder(order)
            .turnNumber(turn)
            .playerSlot(slot)
            .abilityId(id)
            .abilityName("skill_" + id)
            .ultimate(false)
            .poolRemainingBeforePick(48 - (order - 1))
            .build());
  }

  private void draft(Integer secondTurn) {
    pick(1, 1, 0, 10);
    pick(2, secondTurn, 128, 20);
    abilityRepo.save(
        AbilityDomain.builder()
            .matchId(MATCH)
            .playerSlot(0L)
            .abilityId(10L)
            .name("skill_10")
            .damageDealt(1000L)
            .build());
    abilityRepo.save(
        AbilityDomain.builder()
            .matchId(MATCH)
            .playerSlot(128L)
            .abilityId(20L)
            .name("skill_20")
            .damageDealt(1500L)
            .build());
    importanceRepo.save(
        FightImportanceDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .winningTeam("DIRE")
            .important(true)
            .build());
    useRepo.save(
        FightAbilityUseDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .playerSlot(128L)
            .abilityName("skill_20")
            .kind("ABILITY")
            .casts(1)
            .build());
  }

  @Test
  void storesTheFlaggedPairAndReplacesItOnARerun() {
    draft(3);

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<MissedPickDomain> rows = missedPickRepo.findByMatchId(MATCH);
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getRatio()).isEqualTo(1.5);
    assertThat(rows.getFirst().getAnalysisVersion()).isEqualTo(analyzer.version());
  }

  private void patch(Map<Long, DamageAverage> averages) {
    when(matchRepo.findById(MATCH))
        .thenReturn(
            Optional.of(MatchDomain.builder().id(MATCH).patch(60L).startTime(5000L).build()));
    when(countRepository.averageDamage(any(), anyLong(), any())).thenReturn(averages);
  }

  @Test
  void storesThePatchAveragesAndMarksACandidateFarAboveItsOwnAsCarried() {
    draft(3);
    patch(Map.of(10L, new DamageAverage(8, 900), 20L, new DamageAverage(23, 600)));

    analyzer.analyze(MATCH);

    MissedPickDomain row = missedPickRepo.findByMatchId(MATCH).getFirst();
    assertThat(row.getTakenPatchAverage()).isEqualTo(900);
    assertThat(row.getCandidatePatchAverage()).isEqualTo(600);
    assertThat(row.getCandidatePatchGames()).isEqualTo(23);
    assertThat(row.getCarried()).isTrue();
  }

  @Test
  void aCandidateNearItsPatchAverageIsNotCarried() {
    draft(3);
    patch(Map.of(20L, new DamageAverage(23, 1400)));

    analyzer.analyze(MATCH);

    MissedPickDomain row = missedPickRepo.findByMatchId(MATCH).getFirst();
    assertThat(row.getTakenPatchAverage()).isNull();
    assertThat(row.getCarried()).isFalse();
  }

  @Test
  void aSkillWithNoGamesOnThePatchHasZeroGamesAndIsNotCarried() {
    draft(3);
    patch(Map.of());

    analyzer.analyze(MATCH);

    MissedPickDomain row = missedPickRepo.findByMatchId(MATCH).getFirst();
    assertThat(row.getCandidatePatchGames()).isZero();
    assertThat(row.getCandidatePatchAverage()).isNull();
    assertThat(row.getCarried()).isFalse();
  }

  @Test
  void aMatchWithoutAPatchStoresNoAverage() {
    draft(3);

    analyzer.analyze(MATCH);

    MissedPickDomain row = missedPickRepo.findByMatchId(MATCH).getFirst();
    assertThat(row.getCandidatePatchGames()).isNull();
    assertThat(row.getCarried()).isNull();
  }

  @Test
  void anAdPickTakesTheDamageOfTheBaseRowInItsSlot() {
    draft(3);
    pickRepo.findByMatchId(MATCH).stream()
        .filter(p -> p.getAbilityId() == 20L)
        .forEach(
            p -> {
              p.setAbilityName("skill_20_ad");
              pickRepo.save(p);
            });
    abilityRepo.deleteAll(
        abilityRepo.findAllByMatchId(MATCH).stream().filter(a -> a.getAbilityId() == 20L).toList());
    useRepo.findAll().forEach(u -> u.setAbilityName("skill_20_ad"));
    abilityRepo.save(
        AbilityDomain.builder()
            .matchId(MATCH)
            .playerSlot(128L)
            .abilityId(99L)
            .name("skill_20")
            .damageDealt(1500L)
            .build());

    analyzer.analyze(MATCH);

    assertThat(missedPickRepo.findByMatchId(MATCH)).hasSize(1);
  }

  @Test
  void anAdPickIsAveragedUnderItsBaseSkillId() {
    draft(3);
    pickRepo.findByMatchId(MATCH).stream()
        .filter(p -> p.getAbilityId() == 20L)
        .forEach(
            p -> {
              p.setAbilityName("skill_20_ad");
              pickRepo.save(p);
            });
    abilityRepo.deleteAll(
        abilityRepo.findAllByMatchId(MATCH).stream().filter(a -> a.getAbilityId() == 20L).toList());
    useRepo.findAll().forEach(u -> u.setAbilityName("skill_20_ad"));
    abilityRepo.save(
        AbilityDomain.builder()
            .matchId(MATCH)
            .playerSlot(128L)
            .abilityId(99L)
            .name("skill_20")
            .damageDealt(1500L)
            .build());
    patch(Map.of(99L, new DamageAverage(12, 700)));

    analyzer.analyze(MATCH);

    MissedPickDomain row = missedPickRepo.findByMatchId(MATCH).getFirst();
    assertThat(row.getCandidatePatchGames()).isEqualTo(12);
    assertThat(row.getCandidatePatchAverage()).isEqualTo(700);
  }

  @Test
  void aPickWithoutATurnNumberIsSkippedOnItsOwn() {
    draft(3);
    pick(3, null, 128, 21);

    analyzer.analyze(MATCH);

    assertThat(missedPickRepo.findByMatchId(MATCH)).hasSize(1);
  }

  @Test
  void aDraftWhoseEveryPickLacksATurnStoresNothing() {
    draft(null);
    pickRepo.findByMatchId(MATCH).forEach(p -> p.setTurnNumber(null));

    analyzer.analyze(MATCH);

    assertThat(missedPickRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void aMatchWithNoAbilityDamageStoresNothing() {
    draft(3);
    abilityRepo.deleteAll();

    analyzer.analyze(MATCH);

    assertThat(missedPickRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void aMatchWithNoDraftStoresNothing() {
    analyzer.analyze(MATCH);

    assertThat(missedPickRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void onlyAbilityDraftIsSupported() {
    assertThat(analyzer.supports(new AbilityDraftProfile())).isTrue();
    assertThat(analyzer.supports(new AllPickProfile())).isFalse();
  }
}
