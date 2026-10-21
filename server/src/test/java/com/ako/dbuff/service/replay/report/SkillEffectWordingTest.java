package com.ako.dbuff.service.replay.report;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillEffectWordingTest {
  private static String line(
      String key, String hero, String skill, int casts, Map<String, Integer> f) {
    return SkillEffectWording.line(key, hero, skill, casts, f).orElseThrow();
  }

  @Test
  void hookLineCountsSeenHooksHitsDarkHitsAndSaves() {
    Map<String, Integer> f =
        Map.of("SEEN_HIT", 7, "SEEN_MISS", 5, "DARK_HIT", 2, "ALLY_SAVE", 1, "NOT_COUNTED", 4);

    assertThat(line("meat_hook", "Pudge", "Meat Hook", 19, f))
        .isEqualTo(
            "Pudge: 12 hooks at seen enemies, 7 hit (58%); 2 hits in the dark; 1 ally saved.");
  }

  @Test
  void hookLineLeavesOutWhatIsZeroAndSaysWhenNoEnemyWasSeen() {
    assertThat(line("meat_hook", "Pudge", "Meat Hook", 3, Map.of("SEEN_HIT", 1)))
        .isEqualTo("Pudge: 1 hook at seen enemies, 1 hit (100%).");
    assertThat(line("meat_hook", "Pudge", "Meat Hook", 3, Map.of("DARK_HIT", 1, "ALLY_SAVE", 2)))
        .isEqualTo("Pudge: no hook at a seen enemy; 1 hit in the dark; 2 allies saved.");
  }

  @Test
  void trapLineIsAFloorAndNamesTheEstimate() {
    assertThat(line("ice_shards", "Doom", "Ice Shards", 26, Map.of("TRAP", 4, "MISS", 3)))
        .isEqualTo(
            "Doom's Ice Shards trapped at least 4 enemies in 26 casts; 3 casts missed a seen enemy"
                + " (the shard centre is estimated).");
    assertThat(line("ice_shards", "Doom", "Ice Shards", 26, Map.of("MISS", 1)))
        .contains("trapped no enemy that could be told in 26 casts; 1 cast missed a seen enemy");
  }

  @Test
  void promiseLineTellsSavesImpactAndMissedChances() {
    Map<String, Integer> f = Map.of("SAVE", 3, "IMPACT", 2, "MISSED", 2, "NOT_COUNTED", 5);

    assertThat(line("false_promise", "Sniper", "False Promise", 10, f))
        .isEqualTo(
            "Sniper's False Promise saved 3 allies, 2 more died but dealt damage; 2 chances missed.");
    assertThat(line("false_promise", "Sniper", "False Promise", 10, Map.of("IMPACT", 1)))
        .isEqualTo("Sniper's False Promise saved no ally; 1 ally died but dealt damage.");
    assertThat(line("false_promise", "Lotus", "False Promise", 2, Map.of("MISSED", 1)))
        .isEqualTo("Lotus' False Promise saved no ally; 1 chance missed.");
  }

  @Test
  void saysNothingWhenOnlyNotCountedCastsExistOrTheEntryIsUnknown() {
    assertThat(SkillEffectWording.line("meat_hook", "Pudge", "x", 5, Map.of("NOT_COUNTED", 5)))
        .isEmpty();
    assertThat(SkillEffectWording.line("ice_shards", "Doom", "x", 5, Map.of())).isEmpty();
    assertThat(SkillEffectWording.line("other", "Doom", "x", 5, Map.of("SAVE", 1))).isEmpty();
    assertThat(SkillEffectWording.line(null, "Doom", "x", 5, Map.of())).isEmpty();
  }
}
