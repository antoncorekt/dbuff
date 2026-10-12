package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.GameSpikeFactorDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.repo.GameSpikeFactorRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.MatchExplanation;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.SwingExplanation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SwingSectionTest {

  private final GameSpikeRepo spikeRepo = mock(GameSpikeRepo.class);
  private final GameSpikeFactorRepo factorRepo = mock(GameSpikeFactorRepo.class);
  private final FightExplanationReader reader = mock(FightExplanationReader.class);
  private final ReplayPlayerRepo playerRepo = mock(ReplayPlayerRepo.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final SwingSection section =
      new SwingSection(spikeRepo, factorRepo, reader, playerRepo, properties);

  SwingSectionTest() {
    when(reader.read(anyLong(), anyInt(), anyInt()))
        .thenReturn(new MatchExplanation(List.of(), List.of()));
  }

  private static RankedReason ranked(int rank, String scope, Long slot, String code, String ev) {
    return new RankedReason(
        rank,
        FightReasonDomain.builder()
            .scope(scope)
            .subjectSlot(slot)
            .code(code)
            .evidence(ev)
            .severity(0.5)
            .build());
  }

  private static LostFight lost(int fightIndex, double start, Long gold, RankedReason... reasons) {
    return new LostFight(
        FightDomain.builder().fightIndex(fightIndex).startSeconds(start).build(),
        FightExplanationDomain.builder()
            .fightIndex(fightIndex)
            .losingTeam("RADIANT")
            .goldLost(gold)
            .build(),
        List.of(reasons),
        reasons.length);
  }

  private void explain(int spikeIndex, LostFight... fights) {
    when(reader.read(anyLong(), anyInt(), anyInt()))
        .thenReturn(
            new MatchExplanation(
                List.of(
                    new SwingExplanation(
                        spike(spikeIndex, 1380, 5000, "FIGHT_WIN", false), List.of(fights))),
                List.of()));
  }

  private void heroes() {
    when(playerRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                ReplayPlayerDomain.builder().playerSlot(2L).heroEntityName("Terrorblade").build(),
                ReplayPlayerDomain.builder().playerSlot(3L).heroEntityName("Venomancer").build()));
  }

  private ReplayReportInput input() {
    return new ReplayReportInput(1L, null, Set.of(), Map.of(), 2);
  }

  private GameSpikeDomain spike(int index, int start, long gold, String cause, boolean flip) {
    return GameSpikeDomain.builder()
        .spikeIndex(index)
        .startSeconds(start)
        .endSeconds(start + 180)
        .benefitingTeam("DIRE")
        .deltaGold(gold)
        .directionChange(flip)
        .primaryCause(cause)
        .build();
  }

  @Test
  void lines_listTheBiggestSwingsInTimeOrderWithTheirCauses() {
    when(spikeRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                spike(0, 1680, 7400, "FIGHT_WIN", true),
                spike(1, 600, 1600, "CARRY_FARM", false),
                spike(2, 1200, 3000, "OBJECTIVE", false),
                spike(3, 300, 9000, "OTHER", false)));
    when(factorRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                GameSpikeFactorDomain.builder()
                    .spikeIndex(0)
                    .factorType("SOURCE")
                    .ref("HERO_KILLS")
                    .sharePercent(62.0)
                    .build(),
                GameSpikeFactorDomain.builder()
                    .spikeIndex(0)
                    .factorType("SOURCE")
                    .ref("ROSHAN")
                    .sharePercent(21.0)
                    .build()));

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(3);
    assertThat(lines.get(0)).isEqualTo("**Swings**");
    assertThat(lines.get(1))
        .startsWith("20:00-23:00: Dire gained 3.0k net worth")
        .contains("buildings");
    assertThat(lines.get(2))
        .startsWith("28:00-31:00: Dire gained 7.4k net worth and took the lead")
        .contains("a won fight (kills 62%, Roshan 21%)");
  }

  @Test
  void lines_areEmptyWhenNoSwingHasACause() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 300, 9000, "OTHER", false)));

    assertThat(section.lines(input())).isEmpty();
  }

  @Test
  void lines_addTheLostFightReasonsUnderTheirSwing() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 1300, 5000, "FIGHT_WIN", false)));
    heroes();
    explain(
        0,
        lost(
            4,
            1390.0,
            3200L,
            ranked(
                1,
                "PLAYER",
                2L,
                "CARRY_FARMING_ELSEWHERE",
                "carry was farming the jungle when the fight began and stayed out of it"),
            ranked(2, "PLAYER", 3L, "NO_TP", "ABSENT_ALIVE"),
            ranked(3, "TEAM", null, "NO_DISABLE", "Radiant, the losing team: no disable")));

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(3);
    assertThat(lines.get(2))
        .isEqualTo(
            "Radiant lost 3.2k from the fight at 23:10: Terrorblade was farming the jungle,"
                + " Venomancer had no teleport, the team had no disable");
  }

  @Test
  void lines_leaveOutTheGoldWhenNoneIsKnownAndSkipAFightWithoutReasons() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 1300, 5000, "FIGHT_WIN", false)));
    heroes();
    explain(
        0,
        lost(4, 1390.0, null, ranked(1, "PLAYER", 3L, "NO_TP", "ABSENT_ALIVE")),
        lost(5, 1500.0, 900L));

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(3);
    assertThat(lines.get(2))
        .isEqualTo("Radiant lost the fight at 23:10: Venomancer had no teleport");
  }

  @Test
  void lines_askTheReaderForThreeReasonsAndTwoFights() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 1300, 5000, "FIGHT_WIN", false)));

    section.lines(input());

    verify(reader).read(1L, 3, 2);
  }

  @Test
  void lines_stayWithinOneDiscordMessageWhenEveryCapIsUsed() {
    List<GameSpikeDomain> spikes =
        List.of(
            spike(0, 300, 5000, "FIGHT_WIN", false),
            spike(1, 900, 5000, "FIGHT_WIN", false),
            spike(2, 1500, 5000, "FIGHT_WIN", false));
    when(spikeRepo.findByMatchId(1L)).thenReturn(spikes);
    heroes();
    String longEvidence = "item_black_king_bar, item_blink";
    LostFight fight =
        lost(
            1,
            1390.0,
            12300L,
            ranked(1, "PLAYER", 2L, "ABILITY_ON_COOLDOWN", longEvidence),
            ranked(2, "PLAYER", 3L, "ITEM_ON_COOLDOWN", longEvidence),
            ranked(3, "PLAYER", 2L, "NO_MANA", longEvidence));
    when(reader.read(anyLong(), anyInt(), anyInt()))
        .thenReturn(
            new MatchExplanation(
                spikes.stream().map(s -> new SwingExplanation(s, List.of(fight, fight))).toList(),
                List.of()));

    String text =
        String.join("\n", section.lines(new ReplayReportInput(1L, null, Set.of(), Map.of(), 3)));

    assertThat(text.length()).isLessThan(2000);
  }
}
