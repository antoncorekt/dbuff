package com.ako.dbuff.service.replay.analysis.vision;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.model.WardKind;
import com.ako.dbuff.dao.model.WardRemovalKind;
import com.ako.dbuff.dao.repo.FightExplanationRepo;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.FightVisionRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.WardEventRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.fight.PlayerTraces;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightLossProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  VisionAnalyzer.class,
  VisionProperties.class,
  InvisibleHeroFile.class,
  FightExplanationReader.class,
  FightLossProperties.class,
  CombatLogReader.class,
  PlayerTraces.class,
  ReplayProcessingProperties.class
})
class VisionAnalyzerTest {
  private static final long MATCH = 91L;

  @Autowired private VisionAnalyzer analyzer;
  @Autowired private FightVisionRepo visionRepo;
  @Autowired private WardEventRepo wardRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightExplanationRepo explanationRepo;
  @Autowired private FightImportanceRepo importanceRepo;
  @Autowired private FightPlayerRepo fightPlayerRepo;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;

  private static long slot(int i) {
    return i < 5 ? i : 128L + i - 5;
  }

  private void fight(int index, double start, boolean important) {
    fightRepo.save(
        FightDomain.builder()
            .matchId(MATCH)
            .fightIndex(index)
            .startSeconds(start)
            .endSeconds(start + 20)
            .centerX(0)
            .centerY(0)
            .winner("RADIANT")
            .build());
    explanationRepo.save(
        FightExplanationDomain.builder()
            .matchId(MATCH)
            .fightIndex(index)
            .losingTeam("DIRE")
            .build());
    importanceRepo.save(
        FightImportanceDomain.builder()
            .matchId(MATCH)
            .fightIndex(index)
            .winningTeam("RADIANT")
            .important(important)
            .build());
    for (int i : new int[] {0, 1, 5, 6}) {
      fightPlayerRepo.save(
          FightPlayerDomain.builder()
              .matchId(MATCH)
              .fightIndex(index)
              .playerSlot(slot(i))
              .presence("ACTIVE")
              .build());
    }
  }

  private void ward(long owner, WardKind kind, long placed, double worldX) {
    wardRepo.save(
        WardEventDomain.builder()
            .matchId(MATCH)
            .playerSlot(owner)
            .wardIndex((int) placed)
            .wardKind(kind)
            .placedTime(placed)
            .x(worldX / 128 + 128)
            .y(128.0)
            .removalKind(WardRemovalKind.UNKNOWN)
            .build());
  }

  private void stored() {
    for (int i = 0; i < 10; i++) {
      playerRepo.save(
          ReplayPlayerDomain.builder()
              .matchId(MATCH)
              .playerSlot(slot(i))
              .replayPlayerId(i * 2)
              .teamNum(i < 5 ? 2 : 3)
              .heroEntityName("Hero" + i)
              .build());
    }
    List<CombatEvent> events =
        List.of(
            new CombatEvent(
                CombatEventKind.ITEM,
                995,
                "npc_dota_hero_hero5",
                "",
                "item_glimmer_cape",
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                0));
    combatLogRepo.save(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(CombatLogCodec.VERSION)
            .eventCount(events.size())
            .data(CombatLogCodec.encode(events))
            .build());
    track(0, 0);
    track(1, 0);
    track(5, 0);
    track(6, 0);
    fight(0, 1000, true);
    fight(1, 2000, false);
    ward(0, WardKind.OBSERVER, 800, 500);
  }

  private void track(int player, int x) {
    trackRepo.save(
        ReplayPlayerTrackDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot(player))
            .startSeconds(0)
            .stepSeconds(5)
            .sampleCount(2)
            .x(new int[] {x, x})
            .y(new int[] {0, 0})
            .level(new int[] {5, 5})
            .build());
  }

  private FightVisionDomain row(int fight, String team) {
    return visionRepo.findByMatchId(MATCH).stream()
        .filter(r -> r.getFightIndex() == fight && r.getTeam().equals(team))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void anImportantFightGetsARowPerTeam_theOthersNone() {
    stored();

    analyzer.analyze(MATCH);

    assertThat(visionRepo.findByMatchId(MATCH)).hasSize(2);
    FightVisionDomain radiant = row(0, "RADIANT");
    assertThat(radiant.getActiveObservers()).isEqualTo(1);
    assertThat(radiant.getYoungestObserverAge()).isEqualTo(200);
    assertThat(radiant.isLateWard()).isFalse();
    assertThat(radiant.getWindowStart()).isEqualTo(910.0);
    assertThat(radiant.getWindowEnd()).isEqualTo(1035.0);
    assertThat(radiant.isInvisibleHero()).as("Dire's Hero5 used a Glimmer Cape").isTrue();
    assertThat(radiant.getInvisibleHeroes()).isEqualTo("128");
    assertThat(radiant.isDetection()).isFalse();
    assertThat(radiant.getVerdict()).isEqualTo("INVISIBLE_HERO_NO_DETECTION");
    assertThat(radiant.isLost()).isFalse();
    assertThat(radiant.getNearestWardDistance()).isEqualTo(500);
    assertThat(radiant.getAnalysisVersion()).isEqualTo(1);
    FightVisionDomain dire = row(0, "DIRE");
    assertThat(dire.getActiveObservers()).isZero();
    assertThat(dire.isInvisibleHero()).isFalse();
    assertThat(dire.getVerdict()).isEqualTo("NO_VISION");
    assertThat(dire.isLost()).isTrue();
    assertThat(dire.getNearestWardDistance()).isNull();
  }

  @Test
  void aWardCoversTheFightThroughAHeroNotInItsCentre() {
    stored();
    trackRepo.deleteAllInBatch(trackRepo.findByMatchId(MATCH));
    track(0, 0);
    track(1, 4500);
    track(5, 0);
    track(6, 0);
    wardRepo.deleteAllInBatch(wardRepo.findByMatchId(MATCH));
    ward(0, WardKind.OBSERVER, 800, 5000);

    analyzer.analyze(MATCH);

    FightVisionDomain radiant = row(0, "RADIANT");
    assertThat(radiant.getActiveObservers()).isEqualTo(1);
    assertThat(radiant.getNearestWardDistance()).isEqualTo(500);
    assertThat(radiant.getVerdict()).isEqualTo("INVISIBLE_HERO_NO_DETECTION");
  }

  @Test
  void aKillerNoPlayerIsFoundForIsKeptAsUnknown() {
    stored();
    wardRepo.save(
        WardEventDomain.builder()
            .matchId(MATCH)
            .playerSlot(128L)
            .wardIndex(1)
            .wardKind(WardKind.OBSERVER)
            .placedTime(900L)
            .x(128.0)
            .y(128.0)
            .removedTime(1010L)
            .removedBy("npc_dota_creep_goodguys")
            .removalKind(WardRemovalKind.KILLED)
            .build());

    analyzer.analyze(MATCH);

    assertThat(row(0, "RADIANT").getWardsDestroyed()).isEqualTo(1);
    assertThat(row(0, "RADIANT").getDestroyedBy()).isEqualTo("?");
  }

  @Test
  void aLateObserverAndASentryChangeTheRow() {
    stored();
    ward(0, WardKind.OBSERVER, 985, 300);
    ward(0, WardKind.SENTRY, 990, 200);
    wardRepo.deleteAll(
        wardRepo.findByMatchId(MATCH).stream().filter(w -> w.getPlacedTime() == 800).toList());

    analyzer.analyze(MATCH);

    FightVisionDomain radiant = row(0, "RADIANT");
    assertThat(radiant.isLateWard()).isTrue();
    assertThat(radiant.getLateWardSeconds()).isEqualTo(15);
    assertThat(radiant.getVisionWeight()).isEqualTo(0.5);
    assertThat(radiant.isDetection()).isTrue();
    assertThat(radiant.isDetectionUsed()).isTrue();
    assertThat(radiant.getVerdict()).isEqualTo("LATE_WARD");
  }

  @Test
  void aSecondRunReplacesTheRows_andAMatchWithoutWardsGetsNone() {
    stored();
    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);
    assertThat(visionRepo.findByMatchId(MATCH)).hasSize(2);

    wardRepo.deleteAllInBatch(wardRepo.findByMatchId(MATCH));
    analyzer.analyze(MATCH);

    assertThat(visionRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void deleteByMatchId_removesTheRows() {
    stored();
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(visionRepo.findAll()).isEmpty();
  }

  @Test
  void itNamesWhatItReads() {
    assertThat(analyzer.name()).isEqualTo("vision");
    assertThat(analyzer.requires())
        .contains("important-fights", "fights", "combat-log", "state-changes", "players", "tracks");
  }
}
