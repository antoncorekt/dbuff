package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.HeroDraftPickDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.dao.repo.HeroDraftPickRepo;
import com.ako.dbuff.service.replay.AbilityDraftExtractor;
import com.ako.dbuff.service.replay.HeroDraftExtractor;
import com.ako.dbuff.service.replay.HeroDraftFacts;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Hero draft of real Ability Draft replays; runs only through {@code :server:replayTest}. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class HeroDraftReplayTest {
  private static final long MELEE_MEEPO = 82;
  private static final long CLOCKWERK = 51;

  private final ClarityReplayParser parser = Parsers.standard();
  private final AbilityDraftProfile profile = new AbilityDraftProfile();

  @Test
  void parse_readsTheTwelveHeroPoolWithTwoLeftOver() {
    HeroDraftFacts heroes = parse("9038725154").heroDraft();

    assertThat(heroes.pool()).hasSize(12);
    assertThat(heroes.picks()).hasSize(10);
    assertThat(heroes.pool().stream().filter(h -> !h.picked()))
        .extracting(h -> (long) h.heroId())
        .containsExactlyInAnyOrder(MELEE_MEEPO, CLOCKWERK);
    assertThat(heroes.picks()).extracting(p -> p.tick()).isSorted();
  }

  @ParameterizedTest
  @ValueSource(strings = {"9038725154", "9037038717"})
  void extract_storesHeroPicksThatEqualWhatOpenDotaSaysEachPlayerPlayed(String match)
      throws IOException {
    List<HeroDraftPickDomain> rows = heroRows(match, parse(match));

    Map<Long, Long> played = new HashMap<>();
    for (JsonNode p : new ObjectMapper().readTree(json(match).toFile()).get("players")) {
      played.put(p.get("player_slot").asLong(), p.get("hero_id").asLong());
    }
    List<HeroDraftPickDomain> picked =
        rows.stream().filter(r -> r.getPlayerSlot() != null).toList();
    assertThat(rows).hasSize(12);
    assertThat(picked).hasSize(10);
    picked.forEach(r -> assertThat(r.getHeroId()).isEqualTo(played.get(r.getPlayerSlot())));
    assertThat(picked).allMatch(r -> r.getClockSeconds() < 0);
  }

  @Test
  void extract_mergesHeroAndSkillTurnsIntoOneOrderFromOneToFifty() {
    ReplayFacts facts = parse("9038725154");
    List<HeroDraftPickDomain> heroes = heroRows("9038725154", facts);
    List<AbilityDraftPickDomain> skills = skillRows(facts);

    assertThat(skills).hasSize(40);
    List<Integer> turns = new ArrayList<>();
    heroes.stream()
        .map(HeroDraftPickDomain::getTurnNumber)
        .filter(t -> t != null)
        .forEach(turns::add);
    skills.stream().map(AbilityDraftPickDomain::getTurnNumber).forEach(turns::add);
    assertThat(turns).hasSize(50).doesNotHaveDuplicates();
    assertThat(turns.stream().sorted().toList())
        .isEqualTo(Stream.iterate(1, i -> i + 1).limit(50).toList());
    assertThat(skills.getFirst().getTurnNumber()).isEqualTo(1);
    assertThat(skills.getFirst().getClockSeconds()).isBetween(-507.0, -505.0);
    HeroDraftPickDomain first =
        heroes.stream()
            .filter(h -> h.getTurnNumber() != null)
            .min((a, b) -> a.getTurnNumber() - b.getTurnNumber())
            .orElseThrow();
    assertThat(first.getTurnNumber()).isEqualTo(15);
    assertThat(first.getPlayerSlot()).isEqualTo(130L);
  }

  private List<HeroDraftPickDomain> heroRows(String match, ReplayFacts facts) {
    HeroDraftPickRepo repo = mock(HeroDraftPickRepo.class);
    new HeroDraftExtractor(repo, profile).extract(facts, matchDomain(match));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<HeroDraftPickDomain>> rows = ArgumentCaptor.forClass(List.class);
    verify(repo).saveAll(rows.capture());
    return rows.getValue();
  }

  private List<AbilityDraftPickDomain> skillRows(ReplayFacts facts) {
    AbilityDraftPickRepo repo = mock(AbilityDraftPickRepo.class);
    new AbilityDraftExtractor(
            repo, mock(AbilityDraftPoolRepo.class), id -> Optional.empty(), profile)
        .extract(facts, matchDomain("9038725154"));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<AbilityDraftPickDomain>> rows = ArgumentCaptor.forClass(List.class);
    verify(repo).saveAll(rows.capture());
    return rows.getValue();
  }

  private ReplayFacts parse(String match) {
    return parser.parse(dir().resolve(match + ".dem"), TestModes.ABILITY_DRAFT, () -> false);
  }

  private static MatchDomain matchDomain(String match) {
    MatchDomain domain = new MatchDomain();
    domain.setId(Long.parseLong(match));
    return domain;
  }

  private static Path json(String match) {
    return dir().resolve(match + ".json");
  }

  private static Path dir() {
    return Path.of(System.getenv("REPLAY_FIXTURE_DIR"));
  }
}
