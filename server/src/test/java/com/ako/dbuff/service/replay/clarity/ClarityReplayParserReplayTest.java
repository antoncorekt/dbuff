package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.AbilityDraftFacts;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.DraftPick;
import com.ako.dbuff.service.replay.MinuteStat;
import com.ako.dbuff.service.replay.ReplayException;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.ReplayPlayerFact;
import com.ako.dbuff.service.replay.SampleFacts;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Parses a real Ability Draft replay; runs only through {@code :server:replayTest}. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class ClarityReplayParserReplayTest {

  private static final String MATCH = "9038725154.dem";

  private final ClarityReplayParser parser = Parsers.standard();

  @Test
  void parse_readsTheClockStartAndTheRosterOfARealReplay() {
    ReplayFacts facts = parser.parse(replay(MATCH), TestModes.ABILITY_DRAFT, () -> false);

    assertThat(facts.clockStartTick()).isEqualTo(17136);
    assertThat(facts.gameStartTime()).isBetween(596.0, 597.0);
    List<ReplayPlayerFact> players = facts.players();
    assertThat(players).hasSize(10);
    assertThat(players)
        .extracting(ReplayPlayerFact::playerSlot)
        .containsExactly(0L, 1L, 2L, 3L, 4L, 128L, 129L, 130L, 131L, 132L);
    assertThat(players.subList(0, 5)).allMatch(p -> p.teamNum() == 2);
    assertThat(players.subList(5, 10)).allMatch(p -> p.teamNum() == 3);
    assertThat(players).allMatch(p -> p.heroEntityName() != null && !p.heroEntityName().isBlank());
  }

  @Test
  void parse_readsTheAbilityDraftOfARealReplay() {
    AbilityDraftFacts draft =
        parser.parse(replay(MATCH), TestModes.ABILITY_DRAFT, () -> false).draft();

    assertThat(draft).isNotNull();
    assertThat(draft.pool()).hasSize(48);
    assertThat(draft.picks()).hasSize(40);
    assertThat(draft.pool().stream().filter(entry -> !entry.picked())).hasSize(8);
    assertThat(draft.picks()).extracting(DraftPick::order).isSorted();
    assertThat(draft.picks()).extracting(DraftPick::tick).isSorted();
    assertThat(draft.picks().getFirst().replayPlayerId()).isEqualTo(0);
    assertThat(draft.picks().getFirst().poolIndex()).isEqualTo(26);
    assertThat(draft.picks().getFirst().tick()).isBetween(1900, 2000);
    assertThat(draft.pool()).allMatch(entry -> entry.abilityId() > 0 && entry.abilityId() < 10000);
  }

  @Test
  void parse_readsNoDraftPoolForAllPick_evenFromAnAbilityDraftReplayParsedAsAnotherMode() {
    assertThat(parser.parse(replay("9040892342.dem"), TestModes.ALL_PICK, () -> false).draft())
        .isNull();
    assertThat(parser.parse(replay(MATCH), TestModes.ALL_PICK, () -> false).draft()).isNull();
    assertThat(parser.parse(replay("9040892342.dem"), TestModes.ALL_PICK, () -> false).players())
        .hasSize(10);
  }

  @Test
  void parse_readsTheSameDraftShapeFromAnotherReplay() {
    AbilityDraftFacts draft =
        parser.parse(replay("9037038717.dem"), TestModes.ABILITY_DRAFT, () -> false).draft();

    assertThat(draft.pool()).hasSize(48);
    assertThat(draft.picks()).hasSize(40);
    assertThat(draft.pool().stream().filter(entry -> !entry.picked())).hasSize(8);
  }

  @Test
  void parse_samplesEveryPlayerThroughTheWholeGame() {
    SampleFacts samples =
        parser.parse(replay(MATCH), TestModes.ABILITY_DRAFT, () -> false).samples();

    assertThat(samples.startSeconds()).isEqualTo(-90);
    assertThat(samples.stepSeconds()).isEqualTo(5);
    assertThat(samples.series()).hasSize(10);
    int count = samples.series().getFirst().x().length;
    assertThat(count).isBetween(480, 520);
    for (SampleFacts.PlayerSeries series : samples.series()) {
      assertThat(series.y()).hasSize(count);
      assertThat(series.level()).hasSize(count);
      assertThat(Arrays.stream(series.x())).allMatch(x -> Math.abs(x) <= 9000);
      assertThat(Arrays.stream(series.y())).allMatch(y -> Math.abs(y) <= 9000);
      assertThat(Arrays.stream(series.hpPercent())).allMatch(hp -> hp >= 0 && hp <= 100);
      int[] level = series.level();
      int known = 0;
      for (int i = 0; i < count; i++) {
        if (level[i] > 0) {
          assertThat(level[i]).as("level never drops").isGreaterThanOrEqualTo(known);
          known = level[i];
        }
      }
      assertThat(known).as("the heroes are in play to the end").isGreaterThan(10);
      assertThat(level[18]).as("level at 0:00").isEqualTo(1);
      assertThat(series.flags()[18]).as("alive at 0:00").isEqualTo(1);
    }
  }

  @Test
  void parse_snapshotsTheCountersOncePerMinuteWithEveryFieldPresent() {
    SampleFacts samples =
        parser.parse(replay(MATCH), TestModes.ABILITY_DRAFT, () -> false).samples();

    var minutes = samples.minutes();
    assertThat(minutes).hasSize(10 * 40);
    assertThat(minutes.stream().mapToInt(SampleFacts.MinuteSnapshot::gameMinute).min())
        .hasValue(-1);
    assertThat(minutes.stream().mapToInt(SampleFacts.MinuteSnapshot::gameMinute).max())
        .hasValue(38);
    assertThat(minutes).allSatisfy(m -> assertThat(m.values()).doesNotContain(MinuteStat.MISSING));
    var first = minutes.stream().filter(m -> m.replayPlayerId() == 0).toList();
    for (int i = 1; i < first.size(); i++) {
      long before = first.get(i - 1).values()[MinuteStat.CREEP_KILL_GOLD.ordinal()];
      long after = first.get(i).values()[MinuteStat.CREEP_KILL_GOLD.ordinal()];
      assertThat(after).as("cumulative counters never drop").isGreaterThanOrEqualTo(before);
    }
    long teamKills =
        minutes.stream()
            .filter(m -> m.gameMinute() == 38 && m.replayPlayerId() < 10)
            .mapToLong(m -> m.values()[MinuteStat.KILLS.ordinal()])
            .sum();
    assertThat(teamKills).isPositive();
  }

  @Test
  void parse_snapshotsEveryMinuteWhenTheClockIsOffTheGrid() {
    SampleFacts samples =
        parser.parse(replay("9058487700.dem"), TestModes.ABILITY_DRAFT, () -> false).samples();

    var minutes = samples.minutes();
    assertThat(samples.series()).hasSize(10);
    int last = minutes.stream().mapToInt(SampleFacts.MinuteSnapshot::gameMinute).max().orElse(-9);
    assertThat(minutes.stream().mapToInt(SampleFacts.MinuteSnapshot::gameMinute).min())
        .hasValue(-1);
    assertThat(last).isGreaterThanOrEqualTo(39);
    assertThat(minutes).hasSize(10 * (last + 2));
    assertThat(minutes).allSatisfy(m -> assertThat(m.values()).doesNotContain(MinuteStat.MISSING));
    var ids = minutes.stream().map(SampleFacts.MinuteSnapshot::replayPlayerId).distinct().toList();
    assertThat(ids).hasSize(10);
    for (int id : ids) {
      assertThat(
              minutes.stream()
                  .filter(m -> m.replayPlayerId() == id)
                  .mapToInt(SampleFacts.MinuteSnapshot::gameMinute))
          .containsExactlyElementsOf(
              java.util.stream.IntStream.rangeClosed(-1, last).boxed().toList());
    }
    long teamKills =
        minutes.stream()
            .filter(m -> m.gameMinute() == last)
            .mapToLong(m -> m.values()[MinuteStat.KILLS.ordinal()])
            .sum();
    assertThat(teamKills).isPositive();
  }

  @Test
  void parse_keepsWhatItReadWhenTheReplayIsCutShort(@org.junit.jupiter.api.io.TempDir Path dir)
      throws Exception {
    Path cut = dir.resolve("cut.dem");
    long keep = Files.size(replay(MATCH)) * 3 / 4;
    try (var in = Files.newInputStream(replay(MATCH))) {
      Files.write(cut, in.readNBytes((int) keep));
    }

    ReplayFacts facts = parser.parse(cut, TestModes.ABILITY_DRAFT, () -> false);

    assertThat(facts.cutShort()).isTrue();
    assertThat(facts.players()).hasSize(10);
    assertThat(facts.draft().picks()).hasSize(40);
    assertThat(facts.samples().series().getFirst().x().length).isGreaterThan(100);
  }

  @Test
  void parse_doesNotMarkAWholeReplayAsCutShort() {
    assertThat(parser.parse(replay(MATCH), TestModes.ABILITY_DRAFT, () -> false).cutShort())
        .isFalse();
  }

  @Test
  void parse_startsTheSamplesAtTheRealClockStart() {
    SampleFacts samples =
        parser.parse(replay(MATCH), TestModes.ABILITY_DRAFT, () -> false).samples();

    assertThat(samples.startSeconds()).isEqualTo(-90);
  }

  @Test
  void parse_keepsTheHeroCombatLogThatAgreesWithOpenDota() {
    var events =
        parser.parse(replay(MATCH), TestModes.ABILITY_DRAFT, () -> false).combatLog().events();

    long phantomDamage = heroDamage(events, "npc_dota_hero_phantom_assassin");
    long furionDamage = heroDamage(events, "npc_dota_hero_furion");
    assertThat(phantomDamage).as("OpenDota hero_damage 30805").isEqualTo(30805);
    assertThat(furionDamage).as("OpenDota hero_damage 20104").isEqualTo(20104);
    assertThat(uses(events, CombatEventKind.ABILITY, "npc_dota_hero_sniper", "sniper_take_aim"))
        .isEqualTo(11);
    assertThat(
            uses(events, CombatEventKind.ABILITY, "npc_dota_hero_sniper", "furion_teleportation"))
        .isEqualTo(37);
    assertThat(uses(events, CombatEventKind.ITEM, "npc_dota_hero_sniper", "item_tpscroll"))
        .isEqualTo(9);
    var deaths =
        events.stream()
            .filter(e -> e.kind() == CombatEventKind.DEATH && e.targetIsRealHero())
            .toList();
    assertThat(deaths).as("OpenDota counts 94; the 95th is the Aegis of the Immortal").hasSize(95);
    assertThat(deaths.getFirst().gameSecond()).isBetween(3.0, 4.5);
    assertThat(events.stream().filter(e -> e.kind() == CombatEventKind.BUILDING_KILL)).hasSize(22);
    assertThat(events.size()).as("a fraction of the 57 thousand entries").isLessThan(45_000);
    assertThat(CombatLogCodec.encode(events).length).isLessThan(1_000_000);
  }

  @Test
  void parse_stopsWhenCancelled() {
    assertThatThrownBy(() -> parser.parse(replay(MATCH), TestModes.ABILITY_DRAFT, () -> true))
        .isInstanceOfSatisfying(
            ReplayException.class,
            e -> assertThat(e.getStatus()).isEqualTo(ReplayStatus.FAILED_TIMEOUT));
  }

  @Test
  void parse_reportsAFileThatIsNotAReplayAsAParserFailure(
      @org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
    Path garbage = dir.resolve("garbage.dem");
    Files.writeString(garbage, "this is not a replay".repeat(100));

    assertThatThrownBy(() -> parser.parse(garbage, TestModes.ABILITY_DRAFT, () -> false))
        .isInstanceOfSatisfying(
            ReplayException.class,
            e -> assertThat(e.getStatus()).isEqualTo(ReplayStatus.FAILED_PARSER));
  }

  private static Path replay(String name) {
    return Path.of(System.getenv("REPLAY_FIXTURE_DIR")).resolve(name);
  }

  private static long heroDamage(java.util.List<CombatEvent> events, String hero) {
    return events.stream()
        .filter(
            e ->
                e.kind() == CombatEventKind.DAMAGE
                    && e.gameSecond() > 0
                    && e.attackerIsRealHero()
                    && e.targetIsRealHero()
                    && e.attacker().equals(hero))
        .mapToLong(CombatEvent::value)
        .sum();
  }

  private static long uses(
      java.util.List<CombatEvent> events, CombatEventKind kind, String hero, String inflictor) {
    return events.stream()
        .filter(
            e ->
                e.kind() == kind
                    && e.gameSecond() > 0
                    && e.attacker().equals(hero)
                    && e.inflictor().equals(inflictor))
        .count();
  }
}
