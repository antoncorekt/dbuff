package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.HeroDraftPickDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.HeroDraftPickRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({HeroDraftExtractor.class, AbilityDraftProfile.class})
class HeroDraftExtractorTest {

  private static final int CLOCK_START = 17136;
  private static final MatchDomain MATCH = match();

  @Autowired private HeroDraftExtractor extractor;
  @Autowired private HeroDraftPickRepo repo;

  private List<ReplayPlayerFact> players;

  @BeforeEach
  void roster() {
    players = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      players.add(new ReplayPlayerFact(i * 2, i < 5 ? 2 : 3, 0L, "Hero" + i));
    }
  }

  @Test
  void extract_storesTwelveHeroesWithThePickedOnesStampedAndTheOthersBare() {
    extractor.extract(facts(fullHeroes(), null), MATCH);

    List<HeroDraftPickDomain> rows =
        repo.findAll().stream()
            .sorted(Comparator.comparing(HeroDraftPickDomain::getPoolIndex))
            .toList();
    assertThat(rows).hasSize(12);
    HeroDraftPickDomain first = rows.getFirst();
    assertThat(first.getHeroId()).isEqualTo(100L);
    assertThat(first.getPlayerSlot()).isZero();
    assertThat(first.getTeam()).isZero();
    assertThat(first.getPickTick()).isEqualTo(CLOCK_START - 300);
    assertThat(first.getClockSeconds()).isEqualTo(-10.0);
    assertThat(rows.get(5).getTeam()).isEqualTo(1);
    assertThat(rows.get(5).getPlayerSlot()).isEqualTo(130L);
    List<HeroDraftPickDomain> left = rows.stream().filter(r -> r.getPlayerSlot() == null).toList();
    assertThat(left).hasSize(2);
    assertThat(left)
        .allMatch(
            r ->
                r.getTeam() == null
                    && r.getTurnNumber() == null
                    && r.getPickTick() == null
                    && r.getClockSeconds() == null);
  }

  @Test
  void extract_numbersTheHeroTurnsAmongTheSkillTurns() {
    extractor.extract(facts(fullHeroes(), skillsBetweenTheHeroPicks()), MATCH);

    List<HeroDraftPickDomain> picked =
        repo.findAll().stream()
            .filter(r -> r.getPlayerSlot() != null)
            .sorted(Comparator.comparing(HeroDraftPickDomain::getPickTick))
            .toList();
    assertThat(picked).extracting(HeroDraftPickDomain::getTurnNumber).startsWith(2, 4, 6);
  }

  @Test
  void extract_leavesTheTurnNumberOutWhenThereIsNoSkillDraft() {
    extractor.extract(facts(fullHeroes(), null), MATCH);

    assertThat(repo.findAll()).allMatch(r -> r.getTurnNumber() == null);
  }

  @Test
  void extract_storesNothingWithoutAHeroDraftOrOfAnUnexpectedShape() {
    extractor.extract(facts(fullHeroes(), null), MATCH);

    extractor.extract(facts(null, null), MATCH);
    assertThat(repo.findAll()).isEmpty();

    HeroDraftFacts full = fullHeroes();
    extractor.extract(
        facts(new HeroDraftFacts(full.pool(), full.picks().subList(0, 9)), null), MATCH);
    assertThat(repo.findAll()).isEmpty();
  }

  @Test
  void extract_replacesAnEarlierRunInsteadOfDuplicating() {
    extractor.extract(facts(fullHeroes(), null), MATCH);
    extractor.extract(facts(fullHeroes(), null), MATCH);

    assertThat(repo.findAll()).hasSize(12);
  }

  @Test
  void supports_onlyAbilityDraft() {
    assertThat(extractor.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(extractor.supports(TestModes.ALL_PICK)).isFalse();
    assertThat(extractor.name()).isEqualTo("hero-draft");
  }

  private ReplayFacts facts(HeroDraftFacts heroes, AbilityDraftFacts skills) {
    return ReplayFacts.builder()
        .clockStartTick(CLOCK_START)
        .gameStartTime(596.5)
        .players(players)
        .draft(skills)
        .heroDraft(heroes)
        .build();
  }

  /** Hero i has id 100+i; the first 10 are picked, alternating sides, every 300 ticks. */
  private static HeroDraftFacts fullHeroes() {
    List<HeroDraftEntry> pool = new ArrayList<>();
    List<DraftPick> picks = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      boolean picked = i < 10;
      int playerIndex = (i % 2 == 0 ? 0 : 5) + (i / 2) % 5;
      pool.add(new HeroDraftEntry(i, 100 + i, picked ? playerIndex * 2 : -1));
      if (picked) {
        picks.add(new DraftPick(i + 1, i, playerIndex * 2, CLOCK_START - 300 + i * 300));
      }
    }
    return new HeroDraftFacts(pool, picks);
  }

  /** One skill pick 150 ticks before each hero pick, so heroes take turns 2, 4, 6 and so on. */
  private static AbilityDraftFacts skillsBetweenTheHeroPicks() {
    List<DraftPick> picks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      picks.add(new DraftPick(i + 1, i, 0, CLOCK_START - 450 + i * 300));
    }
    return new AbilityDraftFacts(List.of(), picks);
  }

  private static MatchDomain match() {
    MatchDomain match = new MatchDomain();
    match.setId(7L);
    return match;
  }
}
