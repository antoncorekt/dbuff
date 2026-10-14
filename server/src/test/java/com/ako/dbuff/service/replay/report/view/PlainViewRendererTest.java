package com.ako.dbuff.service.replay.report.view;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlainViewRendererTest {

  private final PlainViewRenderer renderer = new PlainViewRenderer();

  @Test
  void isABoldTitleLineThenTheBulletsAsTheyAreWithoutTheVerdict() {
    SectionView view =
        new SectionView(
            "Important fights", "Radiant won 1 of 2", Accent.WIN, List.of("one", "two"), null);

    assertThat(renderer.lines(view)).containsExactly("**Important fights**", "one", "two");
    assertThat(renderer.render(view))
        .containsExactly(new ReportMessage.Plain("**Important fights**\none\ntwo"));
  }

  @Test
  void anUntitledViewIsJustItsLinesAndAFootnoteComesLast() {
    SectionView view = new SectionView("", null, Accent.INFO, List.of("one"), "note");

    assertThat(renderer.lines(view)).containsExactly("one", "note");
  }

  @Test
  void anEmptyViewRendersNothing() {
    assertThat(renderer.render(new SectionView("", null, Accent.INFO, List.of(), null))).isEmpty();
  }
}
