package com.ako.dbuff.service.replay.runeloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.OpeningSummaryDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.service.replay.analysis.rune.RuneProperties;
import com.ako.dbuff.service.replay.runeloop.RuneLoopReport.MatchData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuneLoopReportTest {
  private static final Map<Long, String> HEROES = Map.of(0L, "Windrunner", 128L, "Axe");

  private static MatchData data(
      List<RunePickupDomain> pickups,
      List<OpeningSummaryDomain> summaries,
      List<FightReasonDomain> reasons) {
    MatchDomain match = new MatchDomain();
    match.setId(1L);
    return new MatchData(
        1,
        match,
        "Ability Draft",
        HEROES,
        pickups,
        summaries,
        List.of(
            FightDomain.builder()
                .fightIndex(3)
                .startSeconds(600.0)
                .endSeconds(640.0)
                .winner("DIRE")
                .build()),
        List.of(FightImportanceDomain.builder().fightIndex(3).important(true).build()),
        reasons,
        new RuneProperties());
  }

  private static RunePickupDomain pickup(long slot, String type, int seconds, Integer gold) {
    return RunePickupDomain.builder()
        .playerSlot(slot)
        .team(slot >= 128 ? "DIRE" : "RADIANT")
        .runeType(type)
        .seconds(seconds)
        .zone("LANE")
        .lane("MID")
        .gold(gold)
        .midLane(true)
        .build();
  }

  private static OpeningSummaryDomain summary(String team, String result, long balance) {
    return OpeningSummaryDomain.builder()
        .team(team)
        .result(result)
        .runesTaken(2)
        .bountyGold(200)
        .bountyGoldGame(500)
        .firstTakerSlot("RADIANT".equals(team) ? 0L : 128L)
        .firstTakerSeconds("RADIANT".equals(team) ? 3 : 16)
        .kills(1)
        .gold(1200L)
        .xp(1300L)
        .balance(balance)
        .build();
  }

  private static FightReasonDomain reason(String code, String team, Long slot, String scope) {
    return FightReasonDomain.builder()
        .fightIndex(3)
        .code(code)
        .team(team)
        .subjectSlot(slot)
        .scope(scope)
        .severity(0.5)
        .evidence(
            "double damage rune taken at 9:20 by Windrunner, 40 s from the fight start, active in"
                + " the fight")
        .build();
  }

  @Test
  void render_laysOutTheThreeTablesInWordsWithEmptyVerdictColumnsLast() {
    String report =
        RuneLoopReport.render(
            data(
                List.of(
                    pickup(0, "BOUNTY", 3, 40),
                    pickup(0, "DOUBLE_DAMAGE", 560, null),
                    pickup(128, "WATER", 130, null)),
                List.of(summary("RADIANT", "WON", 120), summary("DIRE", "LOST", -120)),
                List.of(
                    reason("RUNE_ADVANTAGE", "RADIANT", 0L, "PLAYER"),
                    reason("ENEMY_RUNE_ADVANTAGE", "DIRE", null, "TEAM"))));

    assertThat(report)
        .contains("**How to read this:**", "**Summary:**")
        .contains("3 runes taken (1 bounty, 1 double damage, 1 water)")
        .contains("Radiant won it")
        .contains("1 of 1 power runes had an important fight")
        .contains("## Opening", "## Runes", "## Rune advantage in fights")
        .contains(
            "| Radiant | won the opening | first of the game: Windrunner at 0:03 | 2 | 200 | 500 |"
                + " 1 | 1.2k | 1.3k | +120 | | |")
        .contains("| Windrunner took the bounty rune at 0:03 | Radiant | 40 | mid lane | yes |")
        .contains(
            "| Windrunner took the double damage rune at 9:20 | Radiant | - | mid lane | yes |"
                + " yes: fight 3 at 10:00 | | |")
        .contains(
            "| Axe took the water rune at 2:10 | Dire | - | mid lane | yes | not a power rune")
        .contains(
            "| Windrunner took a double damage rune 40 s before the fight | fight 3 at 10:00 | Dire"
                + " won | yes | Dire list: yes | 0.50 | | |")
        .doesNotContain("DOUBLE_DAMAGE", "RUNE_ADVANTAGE", "npc_dota");
  }

  @Test
  void render_saysSoWhenNothingIsStored() {
    String report = RuneLoopReport.render(data(List.of(), List.of(), List.of()));

    assertThat(report)
        .contains("no runes and no opening stored")
        .contains("**No opening stored for this match");
  }
}
