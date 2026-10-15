package com.ako.dbuff.service.replay.analysis.mid;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MidActivityDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MidActivityRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.analysis.behaviour.BehaviourProperties;
import com.ako.dbuff.service.replay.analysis.fight.PlayerTraces;
import java.util.Arrays;
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
  MidActivityAnalyzer.class,
  MidInputsLoader.class,
  MidMoveFinder.class,
  MidVerdictRule.class,
  MidActivityProperties.class,
  BehaviourProperties.class,
  StoredActivity.class,
  PlayerTraces.class,
  CombatLogReader.class
})
class MidActivityAnalyzerTest {
  private static final long MATCH = 31L;
  private static final long PUCK = 0L;
  private static final long RADIANT_CARRY = 1L;
  private static final long LINA = 128L;
  private static final long DIRE_CARRY = 129L;
  private static final String PUCK_NAME = "npc_dota_hero_puck";
  private static final String LINA_NAME = "npc_dota_hero_lina";

  @Autowired private MidActivityAnalyzer analyzer;
  @Autowired private MidActivityRepo midRepo;
  @Autowired private MatchRepo matchRepo;
  @Autowired private PlayerRoleRepo roleRepo;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private LaneResultRepo laneRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightPlayerRepo fightPlayerRepo;
  @Autowired private TeamRotationRepo rotationRepo;
  @Autowired private PlayerTimelineRepo timelineRepo;

  @Test
  void analyze_storesWhatEachMidHeroDidAfterLevelSixAndInTheMidGame() {
    stored();

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    assertThat(midRepo.findByMatchId(MATCH)).hasSize(2);
    MidActivityDomain puck = row(PUCK);
    assertThat(puck.getTeam()).isEqualTo("RADIANT");
    assertThat(puck.getLevel6Seconds()).isEqualTo(400);
    assertThat(puck.getMaxLevel()).isEqualTo(6);
    assertThat(puck.getNextAction()).isEqualTo("ROTATED");
    assertThat(puck.getLeaveSeconds()).isEqualTo(450);
    assertThat(puck.getNextPlace()).isEqualTo("TOP");
    assertThat(puck.getKillsAfterLeaving()).isEqualTo(2);
    assertThat(puck.getWindowStartSeconds()).isEqualTo(600);
    assertThat(puck.getWindowEndSeconds()).isEqualTo(1800);
    assertThat(puck.getKills()).isEqualTo(1);
    assertThat(puck.getAssists()).isZero();
    assertThat(puck.getFightsJoined()).isEqualTo(2);
    assertThat(puck.getFightsTotal()).isEqualTo(3);
    assertThat(puck.getTeamMedianFights()).isEqualTo(1.5);
    assertThat(puck.getRotations()).isZero();
    assertThat(puck.getGoldPerMinute()).isEqualTo(600);
    assertThat(puck.getEnemyGoldPerMinute()).isEqualTo(500);
    assertThat(puck.getEnemyMidSlot()).isEqualTo(LINA);
    assertThat(puck.getTeamGoldPerMinute()).isEqualTo(450);
    assertThat(puck.getXpPerMinute()).isEqualTo(700);
    assertThat(puck.getEnemyXpPerMinute()).isEqualTo(800);
    assertThat(puck.getVerdict()).isEqualTo("ACTIVE");
    assertThat(puck.getAnalysisVersion()).isEqualTo(analyzer.version());
  }

  @Test
  void analyze_aMidHeroWithNoTrackIsPassiveAndHisMoveIsUnknown() {
    stored();

    analyzer.analyze(MATCH);

    MidActivityDomain lina = row(LINA);
    assertThat(lina.getLevel6Seconds()).isNull();
    assertThat(lina.getMaxLevel()).isNull();
    assertThat(lina.getNextAction()).isNull();
    assertThat(lina.getKillsAfterLeaving()).isNull();
    assertThat(lina.getFightsJoined()).isZero();
    assertThat(lina.getVerdict()).isEqualTo("PASSIVE");
  }

  @Test
  void analyze_aTeamWithoutARoleTwoGetsNoRow() {
    stored();
    roleRepo.deleteAll(
        roleRepo.findByMatchId(MATCH).stream().filter(r -> r.getPlayerSlot() == LINA).toList());

    analyzer.analyze(MATCH);

    assertThat(midRepo.findByMatchId(MATCH))
        .extracting(MidActivityDomain::getPlayerSlot)
        .containsExactly(PUCK);
    assertThat(row(PUCK).getEnemyMidSlot()).isNull();
    assertThat(row(PUCK).getEnemyGoldPerMinute()).isNull();
  }

  @Test
  void analyze_storesNothingWithoutACombatLog() {
    stored();
    combatLogRepo.deleteAll();
    combatLogRepo.flush();

    analyzer.analyze(MATCH);

    assertThat(midRepo.findByMatchId(MATCH)).isEmpty();
  }

  private MidActivityDomain row(long slot) {
    return midRepo.findByMatchId(MATCH).stream()
        .filter(r -> r.getPlayerSlot() == slot)
        .findFirst()
        .orElseThrow();
  }

  private void stored() {
    matchRepo.save(MatchDomain.builder().id(MATCH).radiantWin(true).duration(1800L).build());
    player(PUCK, 0, "Puck");
    player(RADIANT_CARRY, 1, "Sniper");
    player(LINA, 5, "Lina");
    player(DIRE_CARRY, 6, "Venomancer");
    role(PUCK, 2);
    role(RADIANT_CARRY, 1);
    role(LINA, 2);
    role(DIRE_CARRY, 1);
    laneRepo.save(LaneResultDomain.builder().matchId(MATCH).pairId(1).endSeconds(600).build());
    int[] level = new int[100];
    Arrays.fill(level, 40, 100, 6);
    trackRepo.save(
        ReplayPlayerTrackDomain.builder()
            .matchId(MATCH)
            .playerSlot(PUCK)
            .startSeconds(0)
            .stepSeconds(10)
            .sampleCount(100)
            .x(new int[100])
            .y(new int[100])
            .level(level)
            .flags(new int[100])
            .build());
    fight(0, 500);
    fight(1, 700);
    fight(2, 900);
    fight(3, 1200);
    joined(0, PUCK);
    joined(1, PUCK);
    joined(1, RADIANT_CARRY);
    joined(2, PUCK);
    rotationRepo.save(
        TeamRotationDomain.builder()
            .matchId(MATCH)
            .team("RADIANT")
            .playerSlot(PUCK)
            .startSeconds(450)
            .toZone("LANE")
            .toLane("TOP")
            .verdict("GOOD")
            .build());
    for (int minute = 0; minute <= 30; minute++) {
      income(PUCK, minute, 600, 700);
      income(RADIANT_CARRY, minute, 300, 400);
      income(LINA, minute, 500, 800);
      income(DIRE_CARRY, minute, 400, 400);
    }
    combatLogRepo.saveAndFlush(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(CombatLogCodec.VERSION)
            .eventCount(4)
            .data(
                CombatLogCodec.encode(
                    List.of(
                        death(480, PUCK_NAME, LINA_NAME, 0),
                        death(500, PUCK_NAME, LINA_NAME, 0),
                        death(900, PUCK_NAME, "npc_dota_hero_venomancer", 0),
                        death(1000, "npc_dota_hero_venomancer", PUCK_NAME, 0))))
            .build());
  }

  private void player(long slot, int replayId, String hero) {
    playerRepo.save(
        ReplayPlayerDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .replayPlayerId(replayId * 2)
            .teamNum(slot < 128 ? 2 : 3)
            .heroEntityName(hero)
            .build());
  }

  private void role(long slot, int role) {
    roleRepo.save(PlayerRoleDomain.builder().matchId(MATCH).playerSlot(slot).role(role).build());
  }

  private void fight(int index, double start) {
    fightRepo.save(
        FightDomain.builder().matchId(MATCH).fightIndex(index).startSeconds(start).build());
  }

  private void joined(int fight, long slot) {
    fightPlayerRepo.save(
        FightPlayerDomain.builder()
            .matchId(MATCH)
            .fightIndex(fight)
            .playerSlot(slot)
            .presence("ACTIVE")
            .build());
  }

  private void income(long slot, int minute, long goldPerMinute, long xpPerMinute) {
    timelineRepo.save(
        PlayerTimelineDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .gameMinute(minute)
            .gold(goldPerMinute * minute)
            .xp(xpPerMinute * minute)
            .build());
  }

  private static CombatEvent death(double second, String killer, String victim, int assists) {
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
        assists);
  }
}
