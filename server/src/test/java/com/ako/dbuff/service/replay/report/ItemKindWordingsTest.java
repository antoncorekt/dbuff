package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemKindWordingsTest {
  private final ItemKindWordings wordings = ItemKindWordings.standard();

  @Test
  void aRowIsWordedByItsMainAspect() {
    assertThat(wordings.find("charge-restore")).containsInstanceOf(StickKindWording.class);
    assertThat(wordings.find("midas-uses")).containsInstanceOf(MidasKindWording.class);
    assertThat(wordings.find("farm-rate")).containsInstanceOf(FarmingKindWording.class);
    assertThat(wordings.find("bottle-runes")).containsInstanceOf(BottleKindWording.class);
    assertThat(wordings.find("cooldown")).containsInstanceOf(CooldownKindWording.class);
    assertThat(wordings.find("vision")).containsInstanceOf(VisionKindWording.class);
    assertThat(wordings.find("team-restore")).containsInstanceOf(TeamRestoreKindWording.class);
    assertThat(wordings.find("self-harm")).containsInstanceOf(SoulRingKindWording.class);
    assertThat(wordings.find("target-use")).containsInstanceOf(TargetUseKindWording.class);
  }

  @Test
  void theBottleLineNamesSipsHealthManaAndRunes() {
    ItemEfficiency row =
        new ItemEfficiency(
            0,
            "bottle",
            "bottle",
            "bottle-runes",
            60.0,
            31,
            1000,
            675L,
            1.5,
            null,
            Map.of("hp", 2400.0, "mana", 1600.0, "runes", 7.0, "bottled", 3.0),
            List.of(),
            List.of());

    assertThat(new BottleKindWording().shortVerdict(row, "Axe"))
        .isEqualTo("Axe: 31 sips, 2.4k HP, 1.6k mana, 7 runes (3 bottled), 1.5x its cost");
  }

  @Test
  void rowsStoredBeforeTheAspectsKeepTheWordingOfTheirKind() {
    assertThat(wordings.find("magic-stick")).containsInstanceOf(StickKindWording.class);
    assertThat(wordings.find("hand-of-midas")).containsInstanceOf(MidasKindWording.class);
    assertThat(wordings.find("farming-group")).containsInstanceOf(FarmingKindWording.class);
  }

  @Test
  void anUnknownOrMissingKindHasNoWording() {
    assertThat(wordings.find("new-kind")).isEmpty();
    assertThat(wordings.find(null)).isEmpty();
  }
}
