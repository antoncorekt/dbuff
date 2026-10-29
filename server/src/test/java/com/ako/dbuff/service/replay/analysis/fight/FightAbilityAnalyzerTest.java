package com.ako.dbuff.service.replay.analysis.fight;

import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.analysis.importance.AdvantageSeriesReader;
import com.ako.dbuff.service.replay.analysis.importance.ImportantFightProperties;
import com.ako.dbuff.service.replay.analysis.importance.ImportantFightRule;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimelines;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  FightAnalyzer.class,
  StoredActivity.class,
  FightAbilityAnalyzer.class,
  CombatLogReader.class,
  HeroStateTimelines.class,
  PlayerTraces.class,
  MapLayouts.class,
  MatchMapLayout.class,
  ReplayProcessingProperties.class,
  ReplayAnalysisProperties.class,
  ImportantFightRule.class,
  ImportantFightProperties.class,
  FightAbilityAnalyzerTest.Json.class
})
class FightAbilityAnalyzerTest {

  private static final long MATCH = 12L;

  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;

  @Autowired private FightAnalyzer fightAnalyzer;
  @MockBean private AdvantageSeriesReader seriesReader;
  @Autowired private FightAbilityAnalyzer analyzer;
  @Autowired private FightAbilityUseRepo useRepo;
  @Autowired private ReplayStateChangeRepo stateRepo;

  @Test
  void analyze_storesWhatEachFightingHeroDidWithHisAbilities_andReplacesAnEarlierRun() {
    stored();
    fightAnalyzer.analyze(MATCH);

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<FightAbilityUseDomain> uses = useRepo.findAll();
    assertThat(uses).hasSize(2);
    FightAbilityUseDomain cast =
        uses.stream().filter(u -> u.getPlayerSlot() == 0L).findFirst().orElseThrow();
    assertThat(cast.getAbilityName()).isEqualTo("sven_storm_bolt");
    assertThat(cast.getCasts()).isEqualTo(1);
    assertThat(cast.getManaCost()).isEqualTo(100);
    assertThat(cast.getManaAtStart()).isEqualTo(400);
    assertThat(cast.getNonCastReason()).isNull();
    assertThat(cast.getAnalysisVersion()).isEqualTo(3);
    FightAbilityUseDomain missed =
        uses.stream().filter(u -> u.getPlayerSlot() == 128L).findFirst().orElseThrow();
    assertThat(missed.getCasts()).isZero();
    assertThat(missed.getNonCastReason()).isEqualTo("DEAD_EARLY");
  }

  @Test
  void analyze_leavesAnIgnoredItemOutAndKeepsAnotherItem() {
    stored();
    item("item_quelling_blade", StateChangeKind.ITEM_ADDED, 1.0, null);
    item("item_quelling_blade", StateChangeKind.COOLDOWN, 50.0, 400.0);
    item("item_blink", StateChangeKind.ITEM_ADDED, 1.0, null);
    item("item_blink", StateChangeKind.COOLDOWN, 50.0, 400.0);
    fightAnalyzer.analyze(MATCH);

    analyzer.analyze(MATCH);

    assertThat(useRepo.findAll())
        .filteredOn(u -> "ITEM".equals(u.getKind()))
        .extracting(FightAbilityUseDomain::getAbilityName)
        .contains("item_blink")
        .doesNotContain("item_quelling_blade");
  }

  private void item(String name, StateChangeKind kind, double at, Double readyAt) {
    stateRepo.saveAndFlush(
        ReplayStateChangeDomain.builder()
            .matchId(MATCH)
            .playerSlot(0L)
            .gameSeconds(at)
            .kind(kind.name())
            .name(name)
            .readyAtSeconds(readyAt)
            .cooldownLength(readyAt == null ? null : 15.0)
            .build());
  }

  @Test
  void analyze_copesWithTracksStoredBeforeManaPointsWereRecorded() {
    stored();
    fightAnalyzer.analyze(MATCH);
    trackRepo
        .findAll()
        .forEach(
            t -> {
              t.setMana(null);
              trackRepo.saveAndFlush(t);
            });

    analyzer.analyze(MATCH);

    assertThat(useRepo.findAll()).allMatch(u -> u.getManaAtStart() == null);
  }

  @Test
  void analyze_ignoresTheManaOfAHeroWhoWasNotAliveInTheSample() {
    stored();
    fightAnalyzer.analyze(MATCH);
    trackRepo
        .findAll()
        .forEach(
            t -> {
              t.setFlags(new int[t.getFlags().length]);
              trackRepo.saveAndFlush(t);
            });

    analyzer.analyze(MATCH);

    assertThat(useRepo.findAll()).allMatch(u -> u.getManaAtStart() == null);
  }

  @Test
  void deleteByMatchId_removesTheUses() {
    stored();
    fightAnalyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(useRepo.findAll()).isEmpty();
  }

  private void state(long slot, String name, StateChangeKind kind, int level, int cost) {
    stateRepo.saveAndFlush(
        ReplayStateChangeDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .gameSeconds(10.0)
            .kind(kind.name())
            .name(name)
            .abilityLevel(kind == StateChangeKind.ABILITY_LEVEL ? level : null)
            .manaCost(kind == StateChangeKind.MANA_COST ? cost : null)
            .build());
  }

  private void stored() {
    for (int i = 0; i < 10; i++) {
      playerRepo.saveAndFlush(
          ReplayPlayerDomain.builder()
              .matchId(MATCH)
              .playerSlot(i < 5 ? (long) i : 128L + i - 5)
              .replayPlayerId(i * 2)
              .teamNum(i < 5 ? 2 : 3)
              .heroEntityName("Hero" + i)
              .build());
      trackRepo.saveAndFlush(
          ReplayPlayerTrackDomain.builder()
              .matchId(MATCH)
              .playerSlot(i < 5 ? (long) i : 128L + i - 5)
              .startSeconds(0)
              .stepSeconds(5)
              .sampleCount(40)
              .x(filled(40, 100 * i))
              .y(filled(40, 0))
              .hpPercent(filled(40, 100))
              .manaPercent(filled(40, 100))
              .mana(filled(40, 400))
              .level(filled(40, 10))
              .flags(filled(40, 1))
              .build());
    }
    for (long slot : new long[] {0L, 128L}) {
      state(slot, "sven_storm_bolt", StateChangeKind.ABILITY_LEVEL, 1, 0);
      state(slot, "sven_storm_bolt", StateChangeKind.MANA_COST, 0, 100);
    }
    List<CombatEvent> events = new ArrayList<>();
    events.add(
        new CombatEvent(
            CombatEventKind.ABILITY,
            101,
            "npc_dota_hero_hero0",
            "",
            "sven_storm_bolt",
            CombatEvent.ATTACKER_HERO,
            0,
            0,
            0,
            0,
            0,
            0,
            0));
    for (double t : new double[] {100, 100.5, 101}) {
      events.add(
          new CombatEvent(
              CombatEventKind.DAMAGE,
              t,
              "npc_dota_hero_hero0",
              "npc_dota_hero_hero5",
              "",
              CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
              300,
              0,
              0,
              0,
              0,
              0,
              0));
    }
    events.add(
        new CombatEvent(
            CombatEventKind.DEATH,
            102,
            "npc_dota_hero_hero0",
            "npc_dota_hero_hero5",
            "",
            CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
            0,
            0,
            0,
            0,
            0,
            0,
            1));
    combatLogRepo.saveAndFlush(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(CombatLogCodec.VERSION)
            .eventCount(events.size())
            .data(CombatLogCodec.encode(events))
            .build());
  }

  private static int[] filled(int size, int value) {
    int[] values = new int[size];
    java.util.Arrays.fill(values, value);
    return values;
  }

  @TestConfiguration
  static class Json {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
