package com.ako.dbuff.service.replay.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemKindWordingsTest {
  private final ItemKindWordings wordings = ItemKindWordings.standard();

  @Test
  void aRowIsWordedByItsMainAspect() {
    assertThat(wordings.find("charge-restore")).containsInstanceOf(StickKindWording.class);
    assertThat(wordings.find("midas-uses")).containsInstanceOf(MidasKindWording.class);
    assertThat(wordings.find("farm-rate")).containsInstanceOf(FarmingKindWording.class);
    assertThat(wordings.find("vision")).containsInstanceOf(VisionKindWording.class);
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
