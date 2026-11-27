package com.ako.dbuff.service.replay.analysis.defense;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefenseItemsFileTest {

  private static DefenseItemsFile parse(String yaml) {
    return new DefenseItemsFile(
        new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yaml");
  }

  @Test
  void theShippedFileLoads() {
    DefenseItemsFile file = new DefenseItemsFile("replay/defense-items.yaml");

    assertThat(file.kinds()).hasSize(20);
    assertThat(file.kindOfItemName("item_pipe")).isEqualTo(DefenseItemKind.MAGIC);
    assertThat(file.kindOfItemName("item_assault")).isEqualTo(DefenseItemKind.PHYSICAL);
    assertThat(file.kindOfItemName("item_heart")).isEqualTo(DefenseItemKind.HP);
    assertThat(file.kindOfItemName("item_manta")).isEqualTo(DefenseItemKind.STATUS);
    assertThat(file.kindOfItemName("item_blink")).isNull();
    assertThat(file.kindOfItemName("pipe")).isNull();
  }

  @Test
  void anUnknownTopLevelKeyIsRejected() {
    assertThatThrownBy(() -> parse("things: []\nitems: [{key: pipe, kind: MAGIC}]"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("things");
  }

  @Test
  void aDuplicateKeyIsRejectedWithTheEntryNamed() {
    assertThatThrownBy(() -> parse("items:\n- {key: pipe, kind: MAGIC}\n- {key: pipe, kind: HP}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'pipe' is listed twice");
  }

  @Test
  void anUnknownKindIsRejectedWithTheEntryNamed() {
    assertThatThrownBy(() -> parse("items:\n- {key: pipe, kind: SHINY}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'pipe'")
        .hasMessageContaining("SHINY");
  }

  @Test
  void aKeyWithTheItemPrefixIsRejectedWithTheEntryNamed() {
    assertThatThrownBy(() -> parse("items:\n- {key: item_pipe, kind: MAGIC}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'item_pipe'");
  }

  @Test
  void anEmptyListIsRejected() {
    for (String yaml : new String[] {"items: []", "items:", ""}) {
      assertThatThrownBy(() -> parse(yaml))
          .as(yaml)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("no items");
    }
  }
}
