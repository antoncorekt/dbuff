package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectiveWordingTest {
  private static final Map<Long, String> HEROES =
      Map.of(0L, "Sniper", 1L, "Phantom Assassin", 2L, "Axe");

  private static ObjectiveConversionDomain roshan(String near, String call) {
    return ObjectiveConversionDomain.builder().roshanNearSlots(near).roshanCall(call).build();
  }

  @Test
  void joinsTheNamesAtThePitWithAnd() {
    assertThat(ObjectiveWording.roshanNote(roshan("0", "EXPECTED"), HEROES))
        .isEqualTo("Sniper stood at the pit; expected, the winner was ahead");
    assertThat(ObjectiveWording.roshanNote(roshan("0,1", "GAMBLE"), HEROES))
        .isEqualTo("Sniper and Phantom Assassin stood at the pit; a gamble, the winner was behind");
    assertThat(ObjectiveWording.roshanNote(roshan("0,1,2", null), HEROES))
        .isEqualTo(
            "Sniper, Phantom Assassin and Axe stood at the pit; the state before the fight is not"
                + " known");
  }

  @Test
  void unreadableSlotsAreLeftOutAndAnAllUnreadableListNeverLeavesASubjectlessSentence() {
    assertThat(ObjectiveWording.roshanNote(roshan("0,x,", "EVEN"), HEROES))
        .isEqualTo("Sniper stood at the pit; the winner was about even");
    assertThat(ObjectiveWording.roshanNote(roshan("x, ,y", "EVEN"), HEROES))
        .isEqualTo("a hero who is not known stood at the pit; the winner was about even");
    assertThat(ObjectiveWording.roshanNote(roshan(" ", null), HEROES))
        .startsWith("no winner hero was found at the pit");
    assertThat(ObjectiveWording.roshanNote(roshan(null, null), null))
        .startsWith("where the killer stood is not known");
  }

  @Test
  void aSlotWithoutAKnownHeroGetsTheSharedFallbackName() {
    assertThat(ObjectiveWording.roshanNote(roshan("3,128", null), Map.of()))
        .startsWith("Radiant player 4 and Dire player 1 stood at the pit");
  }

  @Test
  void theWindowIsOnlyTheStoredOne() {
    assertThat(ObjectiveWording.window(ObjectiveConversionDomain.builder().build())).isNull();
    assertThat(
            ObjectiveWording.window(ObjectiveConversionDomain.builder().windowSeconds(0).build()))
        .isNull();
    assertThat(
            ObjectiveWording.window(ObjectiveConversionDomain.builder().windowSeconds(75).build()))
        .isEqualTo(75);
  }

  @Test
  void aTowerIsNamedByLaneAndTier() {
    assertThat(ObjectiveWording.tower("npc_dota_badguys_tower1_top", "TOP"))
        .isEqualTo("the top lane tier 1 tower");
    assertThat(ObjectiveWording.tower("dota_goodguys_tower3_mid", null))
        .isEqualTo("the tier 3 tower");
    assertThat(ObjectiveWording.tower("junk", "TOP")).isEqualTo("a tower");
    assertThat(ObjectiveWording.tower(null, null)).isEqualTo("a tower");
  }
}
