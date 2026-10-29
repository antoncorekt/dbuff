package com.ako.dbuff.service.replay.analysis.rune;

import com.ako.dbuff.dao.model.OpeningSummaryDomain;
import com.ako.dbuff.dao.model.PlayerActivitySegmentDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.model.RuneEventDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.dao.repo.OpeningSummaryRepo;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.RuneEventRepo;
import com.ako.dbuff.dao.repo.RunePickupRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogBlobs;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
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
  RunePickupAnalyzer.class,
  RuneProperties.class,
  StoredActivity.class,
  CombatLogReader.class,
  MapLayouts.class,
  MatchMapLayout.class,
  ReplayProcessingProperties.class,
  RunePickupAnalyzerTest.Json.class
})
class RunePickupAnalyzerTest {
  private static final long MATCH = 9L;
  private static final long RADIANT = 0L;
  private static final long DIRE = 128L;
  private static final String SNIPER = "npc_dota_hero_sniper";
  private static final String VENOMANCER = "npc_dota_hero_venomancer";
  private static final int BOTTOM_LANE_X = 4859;
  private static final int BOTTOM_LANE_Y = -6379;
  private static final int BOUNTY_GOLD = 17;
  private static final int CREEP_GOLD = 13;
  private static final int DEATH_GOLD = 1;
  private static final int CREEP_XP = 2;

  @Autowired private RunePickupAnalyzer analyzer;
  @Autowired private MapLayouts layouts;
  @Autowired private ReplayProcessingProperties processing;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private PlayerActivitySegmentRepo segmentRepo;
  @Autowired private RuneEventRepo runeRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private RunePickupRepo pickupRepo;
  @Autowired private OpeningSummaryRepo summaryRepo;

  @Test
  void analyze_storesEachPickupWithItsPlaceGoldAndMidLaneFlag_andReplacesAnEarlierRun() {
    stored(layouts.frozenVersion());

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<RunePickupDomain> rows = pickupRepo.findByMatchId(MATCH);
    assertThat(rows).hasSize(4);
    RunePickupDomain bounty = row(rows, RADIANT, "BOUNTY");
    assertThat(bounty.getTeam()).isEqualTo("RADIANT");
    assertThat(bounty.getSeconds()).isEqualTo(3);
    assertThat(bounty.getGold()).isEqualTo(40);
    assertThat(bounty.getZone()).isEqualTo("LANE");
    assertThat(bounty.getLane()).isEqualTo("BOTTOM");
    assertThat(bounty.getMidLane()).isFalse();
    assertThat(bounty.getAnalysisVersion()).isEqualTo(2);
    RunePickupDomain power = row(rows, RADIANT, "DOUBLE_DAMAGE");
    assertThat(power.getGold()).as("only a bounty rune pays gold").isNull();
    RunePickupDomain direBounty = row(rows, DIRE, "BOUNTY");
    assertThat(direBounty.getTeam()).isEqualTo("DIRE");
    assertThat(direBounty.getGold()).isEqualTo(40);
    assertThat(direBounty.getZone()).as("no track for him").isNull();
    assertThat(direBounty.getMidLane()).isTrue();
    assertThat(row(rows, DIRE, "WISDOM").getMidLane()).isTrue();
  }

  @Test
  void analyze_marksMidLaneOnlyInTheMidZoneOrForAnEarlyPickupOfAMainMidLaner() {
    stored(layouts.frozenVersion());
    segmentRepo.deleteAll();
    segmentRepo.flush();
    segmentRepo.saveAndFlush(segment(DIRE, 0, 100, "MID"));
    segmentRepo.saveAndFlush(segment(DIRE, 100, 500, "TOP"));
    segmentRepo.saveAndFlush(segment(RADIANT, 0, 200, "BOTTOM"));
    rune(DIRE, 2, 700, 8);

    analyzer.analyze(MATCH);

    List<RunePickupDomain> rows = pickupRepo.findByMatchId(MATCH);
    assertThat(row(rows, DIRE, "BOUNTY").getMidLane())
        .as("no position, mid by his segment at 20 s")
        .isTrue();
    assertThat(
            rows.stream()
                .filter(r -> r.getPlayerSlot() == DIRE && r.getRuneType().equals("WISDOM"))
                .map(RunePickupDomain::getMidLane))
        .as("main early lane top, not mid at 420 s or 700 s")
        .containsOnly(false);
    assertThat(row(rows, RADIANT, "DOUBLE_DAMAGE").getMidLane()).as("in the bottom lane").isFalse();
  }

  @Test
  void analyze_countsAnEarlyPickupOfAMainMidLanerButNotALateOneOutsideTheMidZone() {
    stored(layouts.frozenVersion());
    segmentRepo.deleteAll();
    segmentRepo.flush();
    segmentRepo.saveAndFlush(segment(RADIANT, 0, 240, "MID"));
    rune(RADIANT, 2, 700, 0);

    analyzer.analyze(MATCH);

    List<RunePickupDomain> radiant =
        pickupRepo.findByMatchId(MATCH).stream().filter(r -> r.getPlayerSlot() == RADIANT).toList();
    assertThat(radiant)
        .as("the track puts him in the bottom lane; he is early, so his main lane counts")
        .filteredOn(r -> r.getSeconds() < 600)
        .allMatch(r -> r.getMidLane());
    assertThat(radiant)
        .filteredOn(r -> r.getSeconds() >= 600)
        .as("after 10:00 only the zone counts")
        .allMatch(r -> !r.getMidLane());
  }

  @Test
  void analyze_endsTheEffectAtTheModifierRemovalOrTheDeathAndLeavesItOpenWithoutAModifier() {
    stored(layouts.frozenVersion());
    combatLogRepo.deleteAll();
    combatLogRepo.flush();
    save(
        List.of(
            modifier(CombatEventKind.MODIFIER_ADD, 50.3, SNIPER, "modifier_rune_doubledamage"),
            modifier(CombatEventKind.MODIFIER_REMOVE, 95.3, SNIPER, "modifier_rune_doubledamage"),
            modifier(CombatEventKind.MODIFIER_ADD, 20.5, VENOMANCER, "modifier_rune_haste"),
            death(40, SNIPER, VENOMANCER),
            modifier(CombatEventKind.MODIFIER_ADD, 3.2, SNIPER, "modifier_rune_doubledamage")));
    rune(DIRE, 2, 20, 1);
    rune(DIRE, 3, 300, 3);

    analyzer.analyze(MATCH);

    List<RunePickupDomain> rows = pickupRepo.findByMatchId(MATCH);
    assertThat(row(rows, RADIANT, "DOUBLE_DAMAGE").getEffectEndSeconds()).isEqualTo(95.3);
    assertThat(row(rows, DIRE, "HASTE").getEffectEndSeconds())
        .as("the hero died at 40 s, which ends it")
        .isEqualTo(40.0);
    assertThat(row(rows, DIRE, "INVISIBILITY").getEffectEndSeconds())
        .as("no modifier near the pickup")
        .isNull();
    assertThat(row(rows, RADIANT, "BOUNTY").getEffectEndSeconds()).isNull();
  }

  @Test
  void analyze_keepsAPickupWhoseTrackHasNoStepWithoutAZone() {
    stored(layouts.frozenVersion());
    trackRepo.deleteAll();
    trackRepo.flush();
    trackRepo.saveAndFlush(
        ReplayPlayerTrackDomain.builder()
            .matchId(MATCH)
            .playerSlot(RADIANT)
            .sampleCount(40)
            .x(new int[40])
            .y(new int[40])
            .build());

    analyzer.analyze(MATCH);

    assertThat(pickupRepo.findByMatchId(MATCH)).hasSize(4).allMatch(r -> r.getZone() == null);
  }

  @Test
  void analyze_storesNoOpeningWhenNoHeroResolvesToATeam() {
    stored(layouts.frozenVersion());
    playerRepo.deleteAll();
    playerRepo.flush();

    analyzer.analyze(MATCH);

    assertThat(pickupRepo.findByMatchId(MATCH)).hasSize(4);
    assertThat(summaryRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void analyze_decidesTheOpeningByGoldAndExperienceAndCountsKills() {
    stored(layouts.frozenVersion());

    analyzer.analyze(MATCH);

    List<OpeningSummaryDomain> rows = summaryRepo.findByMatchId(MATCH);
    OpeningSummaryDomain radiant = summary(rows, "RADIANT");
    OpeningSummaryDomain dire = summary(rows, "DIRE");
    assertThat(radiant.getGold()).as("40 bounty + 38 creep - 50 death").isEqualTo(28);
    assertThat(radiant.getXp()).isZero();
    assertThat(dire.getGold()).isEqualTo(40);
    assertThat(dire.getXp()).isEqualTo(20);
    assertThat(dire.getBalance()).isEqualTo(32);
    assertThat(radiant.getBalance()).isEqualTo(-32);
    assertThat(dire.getResult()).isEqualTo("WON");
    assertThat(radiant.getResult()).isEqualTo("LOST");
    assertThat(dire.getKills()).isEqualTo(1);
    assertThat(radiant.getKills()).isZero();
    assertThat(radiant.getRunesTaken())
        .as("bounty and double damage, inside the window")
        .isEqualTo(2);
    assertThat(radiant.getBountyGold()).isEqualTo(40);
    assertThat(radiant.getFirstTakerSlot()).isEqualTo(RADIANT);
    assertThat(radiant.getFirstTakerSeconds()).isEqualTo(3);
    assertThat(dire.getRunesTaken()).as("the wisdom rune at 7:00 is outside").isEqualTo(1);
    assertThat(dire.getBountyGold()).isEqualTo(40);
    assertThat(dire.getBountyGoldGame()).isEqualTo(40);
    assertThat(dire.getWindowStart()).isEqualTo(-30);
    assertThat(dire.getWindowEnd()).isEqualTo(90);
  }

  @Test
  void analyze_letsKillsBreakATieOfGoldAndExperience() {
    stored(layouts.frozenVersion());
    combatLogRepo.deleteAll();
    combatLogRepo.flush();
    save(
        List.of(
            event(CombatEventKind.GOLD, 10, VENOMANCER, SNIPER, 40, CREEP_GOLD),
            event(CombatEventKind.GOLD, 11, SNIPER, VENOMANCER, 40, CREEP_GOLD),
            death(30, VENOMANCER, SNIPER)));

    analyzer.analyze(MATCH);

    OpeningSummaryDomain dire = summary(summaryRepo.findByMatchId(MATCH), "DIRE");
    assertThat(dire.getBalance()).isZero();
    assertThat(dire.getResult()).isEqualTo("WON");
  }

  @Test
  void analyze_callsItADrawWhenEverythingIsEven() {
    stored(layouts.frozenVersion());
    combatLogRepo.deleteAll();
    combatLogRepo.flush();
    save(List.of(event(CombatEventKind.GOLD, 10, VENOMANCER, SNIPER, 40, CREEP_GOLD)));

    analyzer.analyze(MATCH);

    assertThat(summaryRepo.findByMatchId(MATCH))
        .extracting(OpeningSummaryDomain::getResult)
        .containsOnly("WON", "LOST");
    combatLogRepo.deleteAll();
    combatLogRepo.flush();
    save(
        List.of(
            event(CombatEventKind.GOLD, 10, VENOMANCER, SNIPER, 40, CREEP_GOLD),
            event(CombatEventKind.GOLD, 10, SNIPER, VENOMANCER, 40, CREEP_GOLD)));

    analyzer.analyze(MATCH);

    assertThat(summaryRepo.findByMatchId(MATCH))
        .extracting(OpeningSummaryDomain::getResult)
        .containsOnly("DRAW");
  }

  @Test
  void analyze_storesPickupsWithoutGoldAndNoOpeningForACombatLogWithoutReasons() {
    stored(layouts.frozenVersion());
    analyzer.analyze(MATCH);
    combatLogRepo.deleteAll();
    combatLogRepo.flush();
    combatLogRepo.saveAndFlush(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(1)
            .eventCount(0)
            .data(CombatLogBlobs.versionOne())
            .build());

    analyzer.analyze(MATCH);

    assertThat(pickupRepo.findByMatchId(MATCH)).hasSize(4).allMatch(r -> r.getGold() == null);
    assertThat(summaryRepo.findByMatchId(MATCH)).as("no stale summary is left").isEmpty();
  }

  @Test
  void version_isTwo() {
    assertThat(analyzer.version()).isEqualTo(2);
  }

  @Test
  void analyze_collapsesBountyEventsOfOneSlotAndSecondIntoOnePickupWithTheSummedGold() {
    stored(layouts.frozenVersion());
    runeRepo.deleteAll();
    runeRepo.flush();
    rune(RADIANT, 0, 744, 5);
    rune(RADIANT, 1, 744, 5);
    rune(RADIANT, 2, 744, 5);
    combatLogRepo.deleteAll();
    combatLogRepo.flush();
    save(
        List.of(
            event(CombatEventKind.GOLD, 744.1, "", SNIPER, 46, BOUNTY_GOLD),
            event(CombatEventKind.GOLD, 744.2, "", SNIPER, 58, BOUNTY_GOLD),
            event(CombatEventKind.GOLD, 744.3, "", SNIPER, 52, BOUNTY_GOLD)));

    analyzer.analyze(MATCH);

    List<RunePickupDomain> rows = pickupRepo.findByMatchId(MATCH);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getGold()).isEqualTo(156);
    assertThat(rows.get(0).getRuneIndex()).isZero();
  }

  @Test
  void analyze_keepsTwoSlotsAtTheSameSecondAndOneSlotAtTwoSecondsApart() {
    stored(layouts.frozenVersion());
    runeRepo.deleteAll();
    runeRepo.flush();
    rune(RADIANT, 0, 744, 5);
    rune(DIRE, 0, 744, 5);
    rune(RADIANT, 1, 745, 5);
    combatLogRepo.deleteAll();
    combatLogRepo.flush();
    save(
        List.of(
            event(CombatEventKind.GOLD, 744.1, "", SNIPER, 46, BOUNTY_GOLD),
            event(CombatEventKind.GOLD, 744.2, "", VENOMANCER, 58, BOUNTY_GOLD),
            event(CombatEventKind.GOLD, 745.1, "", SNIPER, 52, BOUNTY_GOLD)));

    analyzer.analyze(MATCH);

    assertThat(pickupRepo.findByMatchId(MATCH)).hasSize(3);
  }

  @Test
  void analyze_storesNothingWithoutRunesOrACombatLog() {
    analyzer.analyze(MATCH);

    assertThat(pickupRepo.findByMatchId(MATCH)).isEmpty();
    assertThat(summaryRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void analyze_keepsAPickupWhoseMapVersionIsNotFrozenWithoutAZone() {
    stored("0123456789ab");

    analyzer.analyze(MATCH);

    assertThat(pickupRepo.findByMatchId(MATCH)).hasSize(4).allMatch(r -> r.getZone() == null);
  }

  @Test
  void deleteByMatchId_removesPickupsAndSummaries() {
    stored(layouts.frozenVersion());
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(pickupRepo.findByMatchId(MATCH)).isEmpty();
    assertThat(summaryRepo.findByMatchId(MATCH)).isEmpty();
  }

  private static RunePickupDomain row(List<RunePickupDomain> rows, long slot, String type) {
    return rows.stream()
        .filter(r -> r.getPlayerSlot() == slot && r.getRuneType().equals(type))
        .findFirst()
        .orElseThrow();
  }

  private static OpeningSummaryDomain summary(List<OpeningSummaryDomain> rows, String team) {
    return rows.stream().filter(r -> r.getTeam().equals(team)).findFirst().orElseThrow();
  }

  private static PlayerActivitySegmentDomain segment(long slot, int start, int end, String lane) {
    return PlayerActivitySegmentDomain.builder()
        .matchId(MATCH)
        .playerSlot(slot)
        .startSeconds(start)
        .endSeconds(end)
        .activity("LANE_FARM")
        .zone("LANE")
        .lane(lane)
        .side("OWN")
        .analysisVersion(1)
        .build();
  }

  private void stored(String mapVersion) {
    runRepo.saveAndFlush(
        ReplayParseRunDomain.builder()
            .matchId(MATCH)
            .parserVersion(processing.getParserVersion())
            .status(ReplayStatus.DONE)
            .attempts(1)
            .mapVersion(mapVersion)
            .build());
    player(RADIANT, 0, 2, "Sniper");
    player(DIRE, 1, 3, "Venomancer");
    int[] x = new int[40];
    int[] y = new int[40];
    Arrays.fill(x, BOTTOM_LANE_X);
    Arrays.fill(y, BOTTOM_LANE_Y);
    trackRepo.saveAndFlush(
        ReplayPlayerTrackDomain.builder()
            .matchId(MATCH)
            .playerSlot(RADIANT)
            .startSeconds(-90)
            .stepSeconds(5)
            .sampleCount(40)
            .x(x)
            .y(y)
            .build());
    segmentRepo.saveAndFlush(segment(DIRE, 0, 600, "MID"));
    rune(RADIANT, 0, 3, 5);
    rune(RADIANT, 1, 50, 0);
    rune(DIRE, 0, 20, 5);
    rune(DIRE, 1, 420, 8);
    save(
        List.of(
            event(CombatEventKind.GOLD, 3.2, "", SNIPER, 40, BOUNTY_GOLD),
            event(CombatEventKind.GOLD, 20.4, "", VENOMANCER, 40, BOUNTY_GOLD),
            event(CombatEventKind.GOLD, 10, "", SNIPER, 38, CREEP_GOLD),
            event(CombatEventKind.XP, 15, "", VENOMANCER, 20, CREEP_XP),
            event(CombatEventKind.GOLD, 30, "", SNIPER, -50, DEATH_GOLD),
            death(30, VENOMANCER, SNIPER),
            event(CombatEventKind.GOLD, 200, "", SNIPER, 500, CREEP_GOLD)));
  }

  private void player(long slot, int replayId, int team, String hero) {
    playerRepo.saveAndFlush(
        ReplayPlayerDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .replayPlayerId(replayId)
            .teamNum(team)
            .heroEntityName(hero)
            .build());
  }

  private void rune(long slot, int index, long seconds, int type) {
    runeRepo.saveAndFlush(
        RuneEventDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .runeIndex(index)
            .eventTime(seconds)
            .runeType(type)
            .build());
  }

  private void save(List<CombatEvent> events) {
    combatLogRepo.saveAndFlush(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(CombatLogCodec.VERSION)
            .eventCount(events.size())
            .data(CombatLogCodec.encode(events))
            .build());
  }

  private static CombatEvent modifier(
      CombatEventKind kind, double second, String hero, String name) {
    return new CombatEvent(
        kind, second, "", hero, name, CombatEvent.TARGET_HERO, 0, 0, 0, 0, 0, 0, 0);
  }

  private static CombatEvent death(double second, String killer, String victim) {
    return new CombatEvent(
        CombatEventKind.DEATH,
        second,
        killer,
        victim,
        "",
        CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
        0,
        0,
        0,
        0,
        0,
        0,
        0);
  }

  private static CombatEvent event(
      CombatEventKind kind, double second, String attacker, String target, int value, int aux) {
    return new CombatEvent(
        kind, second, attacker, target, "", CombatEvent.TARGET_HERO, value, 0, aux, 0, 0, 0, 0);
  }

  @TestConfiguration
  static class Json {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
