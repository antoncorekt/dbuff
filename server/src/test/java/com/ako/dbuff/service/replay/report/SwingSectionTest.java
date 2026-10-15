package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.GameSpikeFactorDomain;
import com.ako.dbuff.dao.repo.GameSpikeFactorRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.MatchExplanation;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.SwingExplanation;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
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
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final SwingSection section =
      new SwingSection(
          spikeRepo,
          factorRepo,
          reader,
          heroNames,
          new ReasonWording(Prettify::ability),
          Prettify::ability,
          properties);

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
            .build(),
        List.of(),
        true);
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
    when(heroNames.iconBySlot(1L)).thenReturn(Map.of(2L, "Terrorblade", 3L, "Venomancer"));
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
  void lines_nameACooldownAbilityByItsDisplayName() {
    heroes();
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 1380, 5000, "FIGHT_WIN", false)));
    explain(
        0,
        lost(
            1,
            1390,
            null,
            ranked(
                1,
                "PLAYER",
                2L,
                "ABILITY_ON_COOLDOWN",
                "terrorblade_metamorphosis, sven_gods_strength")));
    SwingSection real =
        new SwingSection(
            spikeRepo,
            factorRepo,
            reader,
            heroNames,
            new ReasonWording(TestLabels.labels()),
            TestLabels.labels(),
            properties);

    assertThat(real.lines(input()))
        .anyMatch(l -> l.endsWith("Terrorblade had Metamorphosis and Gods Strength on cooldown"));
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

  private static GameSpikeFactorDomain itemFactor(String ref, Long slot, String detail) {
    return GameSpikeFactorDomain.builder()
        .spikeIndex(0)
        .factorType("ITEM")
        .ref(ref)
        .playerSlot(slot)
        .goldValue(3975L)
        .detail(detail)
        .build();
  }

  @Test
  void lines_nameTheKeyItemBehindTheSwing() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 1800, 5000, "FIGHT_WIN", false)));
    heroes();
    when(factorRepo.findByMatchId(1L))
        .thenReturn(List.of(itemFactor("black_king_bar", 2L, "1660")));

    assertThat(section.lines(input()).get(1))
        .endsWith("; it came after Dire finished Black King Bar (Terrorblade) at 27:40");
  }

  @Test
  void lines_skipTheHeroWhenUnknownOrWithoutASlot() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 1800, 5000, "FIGHT_WIN", false)));
    heroes();
    when(factorRepo.findByMatchId(1L)).thenReturn(List.of(itemFactor("aether_lens", 9L, "600")));

    assertThat(section.lines(input()).get(1)).endsWith("finished Aether Lens at 10:00");
    when(factorRepo.findByMatchId(1L)).thenReturn(List.of(itemFactor("aether_lens", null, "600")));
    assertThat(section.lines(input()).get(1)).endsWith("finished Aether Lens at 10:00");
  }

  @Test
  void lines_dropTheClauseForANegativeTime_andPrettifyAnUnknownItem() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 1800, 5000, "FIGHT_WIN", false)));
    when(factorRepo.findByMatchId(1L)).thenReturn(List.of(itemFactor("aether_lens", 2L, "-5")));
    assertThat(section.lines(input()).get(1)).doesNotContain("finished");

    SwingSection raw =
        new SwingSection(
            spikeRepo,
            factorRepo,
            reader,
            heroNames,
            new ReasonWording(Prettify::ability),
            n -> n,
            properties);
    when(factorRepo.findByMatchId(1L)).thenReturn(List.of(itemFactor("new_thing", 2L, "600")));
    assertThat(raw.lines(input()).get(1)).endsWith("finished New Thing at 10:00");
  }

  @Test
  void lines_ignoreAnItemRowWithoutATimeAndAnUnknownFactorType() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 1800, 5000, "FIGHT_WIN", false)));
    when(factorRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                itemFactor("black_king_bar", 2L, null),
                itemFactor("black_king_bar", 2L, "soon"),
                GameSpikeFactorDomain.builder().spikeIndex(0).factorType("FUTURE").build(),
                GameSpikeFactorDomain.builder().spikeIndex(0).build()));

    assertThat(section.lines(input()).get(1)).doesNotContain("finished");
  }

  @Test
  void lines_areEmptyWhenNoSwingHasACause() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 300, 9000, "OTHER", false)));

    assertThat(section.lines(input())).isEmpty();
  }

  @Test
  void lines_leaveOutReasonsThatAreNotRanked() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 1300, 5000, "FIGHT_WIN", false)));
    heroes();
    RankedReason early =
        new RankedReason(
            0,
            FightReasonDomain.builder()
                .scope("PLAYER")
                .subjectSlot(2L)
                .code("ABILITY_ON_COOLDOWN")
                .severity(0.5)
                .build(),
            List.of(),
            false);
    explain(0, lost(4, 1390.0, 3200L, ranked(1, "PLAYER", 3L, "NO_TP", "x"), early));

    List<String> lines = section.lines(input());

    assertThat(lines.get(2)).endsWith("Venomancer had no teleport");
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

  private void oneSwing() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 1300, 5000, "FIGHT_WIN", false)));
    heroes();
  }

  @Test
  void lines_skipAFightWhoseLosingTeamOrStartIsUnknown() {
    oneSwing();
    LostFight unknownTeam = lost(4, 1390.0, 3200L, ranked(1, "PLAYER", 3L, "NO_TP", "x"));
    unknownTeam.explanation().setLosingTeam("NOBODY");
    LostFight noStart = lost(5, 1390.0, 3200L, ranked(1, "PLAYER", 3L, "NO_TP", "x"));
    noStart.fight().setStartSeconds(null);
    explain(0, unknownTeam, noStart);

    assertThat(section.lines(input())).hasSize(2);
  }

  @Test
  void lines_leaveOutGoldThatRoundsToNothing() {
    oneSwing();
    explain(0, lost(4, 1390.0, 40L, ranked(1, "PLAYER", 3L, "NO_TP", "x")));

    assertThat(section.lines(input()).get(2))
        .isEqualTo("Radiant lost the fight at 23:10: Venomancer had no teleport");
  }

  @Test
  void lines_skipAReasonWhoseHeroIsUnknownButKeepTeamReasons() {
    oneSwing();
    explain(
        0,
        lost(
            4,
            1390.0,
            3200L,
            ranked(1, "PLAYER", 99L, "NO_TP", "x"),
            ranked(2, "TEAM", null, "NO_DISABLE", "x")));

    assertThat(section.lines(input()).get(2))
        .isEqualTo("Radiant lost 3.2k from the fight at 23:10: the team had no disable");
  }

  @Test
  void lines_listAtMostTheFindingsCapOfSwingsEachWithItsFights() {
    List<GameSpikeDomain> spikes =
        java.util.stream.IntStream.range(0, 8)
            .mapToObj(i -> spike(i, 300 + i * 100, 5000 + i, "FIGHT_WIN", false))
            .toList();
    when(spikeRepo.findByMatchId(1L)).thenReturn(spikes);
    heroes();
    LostFight fight = lost(1, 1390.0, 3200L, ranked(1, "PLAYER", 3L, "NO_TP", "x"));
    when(reader.read(anyLong(), anyInt(), anyInt()))
        .thenReturn(
            new MatchExplanation(
                spikes.stream().map(sp -> new SwingExplanation(sp, List.of(fight, fight))).toList(),
                List.of()));

    List<String> lines = section.lines(new ReplayReportInput(1L, null, Set.of(), Map.of(), 3));

    assertThat(lines).hasSize(1 + 3 * 3);
  }

  @Test
  void view_namesTheBiggestSwingAndKeepsPlainTextAsItWas() {
    when(spikeRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                spike(0, 1380, 5000, "FIGHT_WIN", false), spike(1, 2400, 9000, "OBJECTIVE", true)));
    ReplayReportInput dire = new ReplayReportInput(1L, null, Set.of(128L), Map.of(), 2);
    ReplayReportInput radiant = new ReplayReportInput(1L, null, Set.of(0L), Map.of(), 2);

    SectionView view = section.view(dire).orElseThrow();

    ViewRendering.assertWithinLimits(view);
    assertThat(view.title()).isEqualTo("Swings");
    assertThat(view.verdict())
        .isEqualTo("Biggest swing: Dire (your team) gained 9.0k net worth between 40:00 and 43:00");
    assertThat(view.accent()).isEqualTo(Accent.WIN);
    assertThat(section.view(radiant).orElseThrow().accent()).isEqualTo(Accent.LOSS);
    assertThat(section.view(input()).orElseThrow().accent()).isEqualTo(Accent.NEUTRAL);
    assertThat(String.join("\n", section.lines(input())))
        .isEqualTo(
            "**Swings**\n"
                + "23:00-26:00: Dire gained 5.0k net worth, mostly from a won fight\n"
                + "40:00-43:00: Dire gained 9.0k net worth and took the lead, mostly from"
                + " buildings");
    assertThat(new PlainViewRenderer().lines(view)).isEqualTo(section.lines(dire));
  }

  @Test
  void view_isEmptyWithoutSwings() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.view(input())).isEmpty();
  }
}
