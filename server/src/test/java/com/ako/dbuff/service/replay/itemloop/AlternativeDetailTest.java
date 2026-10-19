package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlternativeDetailTest {

  @Test
  void handlesOnlyTheAlternativesRows() {
    assertThat(AlternativeDetail.handles(new Detail("ALTERNATIVE", 60, "bfury", Map.of())))
        .isTrue();
    assertThat(AlternativeDetail.handles(new Detail("POPULAR", 60, "bfury", Map.of()))).isTrue();
    assertThat(AlternativeDetail.handles(new Detail("BEFORE", 60, null, Map.of()))).isFalse();
  }

  @Test
  void aRowWithoutFiguresStillReadsWithTheMissingOnesUnknown() {
    String[] cells =
        AlternativeDetail.cells(new Detail("ALTERNATIVE", 600, "maelstrom", Map.of("games", 12.0)));

    assertThat(cells[3]).isEqualTo("12 games, win rate unknown, lead unknown");
  }

  @Test
  void aPopularRowNamesTheItem() {
    String[] cells = AlternativeDetail.cells(new Detail("POPULAR", 600, "maelstrom", Map.of()));

    assertThat(cells[1]).isEqualToIgnoringCase("maelstrom");
    assertThat(cells[2]).isEqualTo("popular for this hero at this point");
  }
}
