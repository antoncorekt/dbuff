package com.ako.dbuff.service.replay.looprunner;

import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoopWordingTest {
  @Test
  void clockIsMinutesAndTwoDigitSeconds() {
    assertThat(LoopWording.clock(131.4)).isEqualTo("2:11");
    assertThat(LoopWording.clock(3605)).isEqualTo("60:05");
    assertThat(LoopWording.clock(-95)).isEqualTo("-1:35");
    assertThat(LoopWording.clock(null)).isEqualTo("?");
  }

  @Test
  void goldIsPlainUnderAThousandAndInKAbove() {
    assertThat(LoopWording.gold(850L)).isEqualTo("850");
    assertThat(LoopWording.gold(1200L)).isEqualTo("1.2k");
    assertThat(LoopWording.gold(1000L)).isEqualTo("1k");
    assertThat(LoopWording.gold(-1000L)).isEqualTo("-1k");
    assertThat(LoopWording.gold(12_340L)).isEqualTo("12.3k");
    assertThat(LoopWording.gold(null)).isEqualTo("?");
  }

  @Test
  void teamsAreRadiantAndDire() {
    assertThat(LoopWording.team("RADIANT")).isEqualTo("Radiant");
    assertThat(LoopWording.team("DIRE")).isEqualTo("Dire");
    assertThat(LoopWording.team("both")).isEqualTo("?");
    assertThat(LoopWording.team((String) null)).isEqualTo("?");
    assertThat(LoopWording.team(TeamSide.DIRE)).isEqualTo("Dire");
    assertThat(LoopWording.teamOfSlot(130)).isEqualTo("Dire");
    assertThat(LoopWording.teamOfSlot(2)).isEqualTo("Radiant");
  }

  @Test
  void aHeroIsItsNameOrItsTeamAndPlaceNeverAnId() {
    Map<Long, String> names = Map.of(0L, "Mirana");

    assertThat(LoopWording.hero(names, 0L)).isEqualTo("Mirana");
    assertThat(LoopWording.hero(names, 129L)).isEqualTo("Dire player 2");
    assertThat(LoopWording.hero(names, 4L)).isEqualTo("Radiant player 5");
    assertThat(LoopWording.hero(names, null)).isEqualTo("?");
  }

  @Test
  void rolesAndTheirBestFitsAreInPositionWords() {
    assertThat(LoopWording.role(5)).isEqualTo("5 hard support");
    assertThat(LoopWording.role(null)).isEqualTo("unknown");
    assertThat(LoopWording.role(6)).isEqualTo("unknown");
    assertThat(LoopWording.bestFits("-0.50,2.00,3.25,1.10,-2.00"))
        .isEqualTo("offlane 3.25, mid 2.00, soft support 1.10");
    assertThat(LoopWording.bestFits(" ")).isEqualTo("unknown");
  }

  @Test
  void compositionTokensAndEnumNamesAreWords() {
    assertThat(LoopWording.tokens("CARRIES_3,NO_INITIATOR", "none"))
        .isEqualTo("3 carries, no initiator");
    assertThat(LoopWording.tokens("", "no findings")).isEqualTo("no findings");
    assertThat(LoopWording.words("FIGHT_WIN")).isEqualTo("fight win");
  }

  @Test
  void aReasonIsTheProductionPhraseAboutTheHero() {
    FightReasonDomain reason =
        FightReasonDomain.builder().scope("PLAYER").code("OUT_OF_POSITION").evidence("x").build();

    assertThat(LoopWording.reason(reason, "Mirana")).isEqualTo("Mirana stood too far in front");
    assertThat(LoopWording.reasonWithoutHero("NO_TP", "Mirana")).isEqualTo("had no teleport");
  }

  @Test
  void laneRolesAndAbilitiesAreReadable() {
    assertThat(LoopWording.laneRole(3L)).isEqualTo("offlane");
    assertThat(LoopWording.laneRole(null)).isEqualTo("?");
    assertThat(LoopWording.ability("sniper_shrapnel")).isEqualTo("Sniper Shrapnel");
  }
}
