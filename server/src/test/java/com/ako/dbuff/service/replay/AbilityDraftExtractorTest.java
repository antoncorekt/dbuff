package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.AbilityDraftPoolDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  AbilityDraftExtractor.class,
  AbilityDraftProfile.class,
  AbilityDraftExtractorTest.Names.class
})
class AbilityDraftExtractorTest {

  private static final MatchDomain MATCH = match();

  @Autowired private AbilityDraftExtractor extractor;
  @Autowired private AbilityDraftPickRepo pickRepo;
  @Autowired private AbilityDraftPoolRepo poolRepo;

  private List<ReplayPlayerFact> players;

  @BeforeEach
  void roster() {
    players = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      players.add(new ReplayPlayerFact(i * 2, i < 5 ? 2 : 3, 0L, "Hero" + i));
    }
  }

  @Test
  void extract_storesFortyPicksWithTeamIndexTimingAndSlots() {
    extractor.extract(facts(fullDraft()), MATCH);

    List<AbilityDraftPickDomain> picks =
        pickRepo.findAll().stream()
            .sorted(Comparator.comparing(AbilityDraftPickDomain::getPickOrder))
            .toList();
    assertThat(picks).hasSize(40);
    AbilityDraftPickDomain first = picks.getFirst();
    assertThat(first.getPlayerSlot()).isEqualTo(0L);
    assertThat(first.getTeam()).isZero();
    assertThat(first.getTeamPickIndex()).isEqualTo(1);
    assertThat(first.getSecondsIntoDraft()).isZero();
    assertThat(first.getSecondsSincePrevPick()).isNull();
    assertThat(first.getPoolRemainingBeforePick()).isEqualTo(48);
    assertThat(first.getAbilityName()).isEqualTo("ability_100");
    AbilityDraftPickDomain second = picks.get(1);
    assertThat(second.getTeam()).isEqualTo(1);
    assertThat(second.getPlayerSlot()).isEqualTo(128L);
    assertThat(second.getSecondsSincePrevPick()).isEqualTo(7.0);
    assertThat(second.getSecondsIntoDraft()).isEqualTo(7.0);
    assertThat(picks.getLast().getPoolRemainingBeforePick()).isEqualTo(9);
  }

  @Test
  void extract_leavesTurnAndClockOutWithoutAHeroDraftButStillStoresThePicks() {
    extractor.extract(facts(fullDraft()), MATCH);

    List<AbilityDraftPickDomain> picks = pickRepo.findAll();
    assertThat(picks).hasSize(40);
    assertThat(picks).allMatch(p -> p.getTurnNumber() == null && p.getClockSeconds() == null);
  }

  @Test
  void extract_leavesTurnAndClockOutWhenTheHeroDraftHasTheWrongShape() {
    HeroDraftFacts shortHeroes =
        new HeroDraftFacts(List.of(), List.of(new DraftPick(1, 0, 0, 2105)));

    extractor.extract(withHeroes(shortHeroes), MATCH);

    assertThat(pickRepo.findAll()).hasSize(40);
    assertThat(pickRepo.findAll())
        .allMatch(p -> p.getTurnNumber() == null && p.getClockSeconds() == null);
  }

  @Test
  void extract_numbersEverySkillTurnAmongTheHeroTurnsAndStampsTheClock() {
    extractor.extract(withHeroes(heroesAfterTheFirstSkill()), MATCH);

    List<AbilityDraftPickDomain> picks =
        pickRepo.findAll().stream()
            .sorted(Comparator.comparing(AbilityDraftPickDomain::getPickOrder))
            .toList();
    assertThat(picks.get(0).getTurnNumber()).isEqualTo(1);
    assertThat(picks.get(1).getTurnNumber()).isEqualTo(3);
    assertThat(picks.getLast().getTurnNumber()).isEqualTo(41);
    assertThat(picks.getFirst().getClockSeconds()).isEqualTo((2000 - 17136) / 30.0);
  }

  private ReplayFacts withHeroes(HeroDraftFacts heroes) {
    return ReplayFacts.builder()
        .clockStartTick(17136)
        .players(players)
        .draft(fullDraft())
        .heroDraft(heroes)
        .build();
  }

  /** Ten heroes, the first just after the first skill pick and the rest long after the skills. */
  private static HeroDraftFacts heroesAfterTheFirstSkill() {
    List<HeroDraftEntry> pool = new ArrayList<>();
    List<DraftPick> picks = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      pool.add(new HeroDraftEntry(i, 1 + i, -1));
    }
    for (int i = 0; i < 10; i++) {
      picks.add(new DraftPick(i + 1, i, 0, i == 0 ? 2105 : 2000 + 40 * 210 + i));
    }
    return new HeroDraftFacts(pool, picks);
  }

  @Test
  void extract_storesThePoolWithSkippedSkillsHavingNoPicker() {
    extractor.extract(facts(fullDraft()), MATCH);

    List<AbilityDraftPoolDomain> pool = poolRepo.findAll();
    assertThat(pool).hasSize(48);
    assertThat(pool.stream().filter(p -> p.getPickedBySlot() == null)).hasSize(8);
    assertThat(pool.stream().filter(p -> p.getPickedBySlot() == null))
        .allMatch(p -> p.getPickOrder() == null && p.getUltimate() == null);
  }

  @Test
  void extract_storesNothingForAMatchWithoutADraftOrWithAnUnexpectedShape() {
    extractor.extract(facts(fullDraft()), MATCH);

    extractor.extract(facts(null), MATCH);
    assertThat(pickRepo.findAll()).isEmpty();
    assertThat(poolRepo.findAll()).isEmpty();

    AbilityDraftFacts shortDraft = fullDraft();
    extractor.extract(
        facts(new AbilityDraftFacts(shortDraft.pool(), shortDraft.picks().subList(0, 39))), MATCH);
    assertThat(pickRepo.findAll()).isEmpty();
    assertThat(poolRepo.findAll()).isEmpty();
  }

  @Test
  void extract_replacesAnEarlierRunInsteadOfDuplicating() {
    extractor.extract(facts(fullDraft()), MATCH);
    extractor.extract(facts(fullDraft()), MATCH);

    assertThat(pickRepo.findAll()).hasSize(40);
    assertThat(poolRepo.findAll()).hasSize(48);
  }

  private ReplayFacts facts(AbilityDraftFacts draft) {
    return ReplayFacts.builder()
        .clockStartTick(17136)
        .gameStartTime(596.5)
        .players(players)
        .draft(draft)
        .build();
  }

  /** Pool entry i is ability 100+i; the first 40 are picked, alternating Radiant and Dire. */
  private static AbilityDraftFacts fullDraft() {
    List<DraftPoolEntry> pool = new ArrayList<>();
    List<DraftPick> picks = new ArrayList<>();
    for (int i = 0; i < 48; i++) {
      boolean picked = i < 40;
      int playerIndex = (i % 2 == 0 ? 0 : 5) + (i / 2) % 5;
      pool.add(new DraftPoolEntry(i, 100 + i, picked ? playerIndex * 2 : -1, i % 4));
      if (picked) {
        picks.add(new DraftPick(i + 1, i, playerIndex * 2, 2000 + i * 210));
      }
    }
    return new AbilityDraftFacts(pool, picks);
  }

  private static MatchDomain match() {
    MatchDomain match = new MatchDomain();
    match.setId(7L);
    return match;
  }

  @TestConfiguration
  static class Names {
    @Bean
    AbilityNames abilityNames() {
      return id -> Optional.of("ability_" + id);
    }
  }
}
