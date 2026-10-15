package com.ako.dbuff.service.replay.analysis.missedpick;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.MissedPickRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

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
