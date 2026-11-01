package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HighlightsSectionTest {
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final ReplayReportInput input = new ReplayReportInput(1L, null, Set.of(), Map.of(), 3);

  @SuppressWarnings("unchecked")
  private HighlightsSection section(HighlightSource... sources) {
    ObjectProvider<HighlightSource> provider = mock(ObjectProvider.class);
    when(provider.orderedStream()).thenAnswer(i -> java.util.stream.Stream.of(sources));
    return new HighlightsSection(provider, properties);
  }

  private static HighlightSource of(Highlight... found) {
    return in -> List.of(found);
  }

  private static Highlight h(String source, Double seconds, double score, String text) {
    return new Highlight(source, seconds, score, text);
  }

  @Test
  void scaleIsZeroAtNormalOneAtExtremeAndLinearBetween() {
    assertThat(Highlight.scale(5, 5, 20)).isZero();
    assertThat(Highlight.scale(1, 5, 20)).isZero();
    assertThat(Highlight.scale(20, 5, 20)).isEqualTo(1);
    assertThat(Highlight.scale(99, 5, 20)).isEqualTo(1);
    assertThat(Highlight.scale(12.5, 5, 20)).isEqualTo(0.5);
  }

  @Test
  void isPartOfTheStandardReportAndOnByDefault() {
    HighlightsSection section = section();
    assertThat(section.name()).isEqualTo("highlights");
    assertThat(section.order()).isEqualTo(55);
    assertThat(section.minimumDetail()).isEqualTo(ReportDetail.STANDARD);
    assertThat(section.defaultEnabled()).isTrue();
    assertThat(section.summary()).isEqualTo("the most unusual moments of the match");
  }

  @Test
  void keepsTheBestTwoOfEachSource() {
    var lines =
        section(
                of(
                    h("a", 60.0, 0.9, "one"),
                    h("a", 120.0, 0.8, "two"),
                    h("a", 180.0, 0.7, "three"),
                    h("b", 240.0, 0.3, "four")))
            .lines(input);
    assertThat(lines).containsExactly("**What stood out**", "1:00 one", "2:00 two", "4:00 four");
  }

  @Test
  void keepsTheBestSixInAll() {
    properties.getHighlights().setMaxPerSource(5);
    Highlight[] many = new Highlight[8];
    for (int i = 0; i < many.length; i++) {
      many[i] = h("s" + i / 2, 60.0 * (i + 1), 0.9 - i * 0.05, "t" + i);
    }
    assertThat(section(of(many)).lines(input)).hasSize(7).doesNotContain("7:00 t6", "8:00 t7");
  }

  @Test
  void dropsWhatIsBelowTheMinimumScore() {
    assertThat(section(of(h("a", 60.0, 0.5, "kept"), h("b", 90.0, 0.1, "dropped"))).lines(input))
        .containsExactly("**What stood out**", "1:00 kept");
  }

  @Test
  void showsTheSingleBestWhenNothingPassesTheMinimumScore() {
    assertThat(section(of(h("a", 60.0, 0.1, "low"), h("b", 90.0, 0.2, "better"))).lines(input))
        .containsExactly("**What stood out**", "1:30 better");
  }

  @Test
  void isEmptyWithoutCandidates() {
    assertThat(section().lines(input)).isEmpty();
    assertThat(section(of()).view(input)).isEmpty();
  }

  @Test
  void listsInGameTimeWithUntimedLinesLastAndScoreBreakingTies() {
    var lines =
        section(
                of(
                    h("a", null, 0.9, "untimed strong"),
                    h("b", 300.0, 0.5, "late"),
                    h("c", 60.0, 0.4, "early"),
                    h("d", null, 0.6, "untimed weak"),
                    h("e", 300.0, 0.8, "late strong")))
            .lines(input);
    assertThat(lines)
        .containsExactly(
            "**What stood out**",
            "1:00 early",
            "5:00 late strong",
            "5:00 late",
            "untimed strong",
            "untimed weak");
  }

  @Test
  void skipsASourceThatThrows() {
    HighlightSource broken =
        in -> {
          throw new IllegalStateException("boom");
        };
    assertThat(section(broken, of(h("a", 60.0, 0.5, "fine"))).lines(input))
        .containsExactly("**What stood out**", "1:00 fine");
  }

  @Test
  void cutsALongLine() {
    properties.getHighlights().setMaxLineLength(60);
    String text = "word ".repeat(30).strip();
    assertThat(section(of(h("a", 60.0, 0.5, text))).lines(input).get(1))
        .hasSizeLessThanOrEqualTo(60)
        .endsWith("...");
  }

  @Test
  void viewTitlesTheCard() {
    var view = section(of(h("a", 60.0, 0.5, "x"))).view(input).orElseThrow();
    assertThat(view.title()).isEqualTo("What stood out");
    assertThat(view.bullets()).containsExactly("1:00 x");
  }

  @Test
  void validationNamesABadScaleKey() {
    properties.getHighlights().setLanes(new ReplayReportProperties.Scale(1, 1));
    assertThatThrownBy(properties::validate).hasMessageContaining("highlights.lanes");
    properties.getHighlights().setLanes(new ReplayReportProperties.Scale(0.3, 1));
    properties.getHighlights().setMaxLines(16);
    assertThatThrownBy(properties::validate).hasMessageContaining("highlights.max-lines");
  }
}
