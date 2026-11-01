package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.ai.reasons.AiReasonsSection;
import com.ako.dbuff.service.ai.reasons.ReplayReportAiProperties;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.ako.dbuff.service.replay.report.view.MessageLimits;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MoreReportsMessageTest {

  private static final String EVERYTHING = "`/match report section:everything` every report";

  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final ReplayReportAiProperties ai = new ReplayReportAiProperties();
  private final GameModeProfile mode = TestModes.registry().activeFor(18L).orElseThrow();

  @SuppressWarnings("unchecked")
  private ObjectProvider<ReplayReportAiProperties> provider() {
    ObjectProvider<ReplayReportAiProperties> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(ai);
    return provider;
  }

  private static TestSection section(String name, int order) {
    return new TestSection(name, order, "what " + name + " shows", true);
  }

  private MoreReportsMessage message(List<ReplayReportSection> all) {
    return new MoreReportsMessage(new SectionSwitch(properties, all), provider());
  }

  private String text(List<ReplayReportSection> all, ReplayReportSection... selected) {
    return message(all).text(all, mode, List.of(selected)).orElseThrow();
  }

  @Test
  void listsWhatWasNotPostedInOrderThenEverything() {
    TestSection posted = section("alpha", 1);
    List<ReplayReportSection> all = List.of(section("gamma", 3), posted, section("beta", 2));

    assertThat(text(all, posted))
        .isEqualTo(
            "`/match report section:beta` what beta shows\n"
                + "`/match report section:gamma` what gamma shows\n"
                + EVERYTHING);
  }

  @Test
  void leavesOutHiddenSectionsAndThoseOfOtherModes() {
    hide("hidden");
    TestSection posted = section("alpha", 1);
    List<ReplayReportSection> all =
        List.of(
            posted,
            section("hidden", 2),
            new TestSection("other-mode", 3, "x", false),
            section("shown", 4));

    assertThat(text(all, posted))
        .contains("section:shown")
        .doesNotContain("section:hidden")
        .doesNotContain("other-mode");
  }

  @Test
  void leavesOutTheAiReasonsSection() {
    TestSection posted = section("alpha", 1);
    List<ReplayReportSection> all = List.of(posted, section(AiReasonsSection.NAME, 2));

    assertThat(text(all, posted)).isEqualTo(EVERYTHING);
  }

  @Test
  void offersTheAiReportOnlyWhenItIsEnabled() {
    TestSection posted = section("alpha", 1);
    List<ReplayReportSection> all = List.of(posted);
    assertThat(text(all, posted)).doesNotContain("/match ai");

    ai.setEnabled(true);
    assertThat(text(all, posted)).endsWith(EVERYTHING + "\n`/match ai` top 5 reasons");
  }

  @Test
  void isEmptyWhenNothingWasSelected() {
    List<ReplayReportSection> all = List.of(section("alpha", 1));

    assertThat(message(all).text(all, mode, List.of())).isEmpty();
  }

  @Test
  void stopsAtAWholeLineWithinTheLimitAndStillEndsWithEverything() {
    ai.setEnabled(true);
    TestSection posted = section("alpha", 1);
    List<ReplayReportSection> all = new ArrayList<>(List.of(posted));
    for (int i = 0; i < 100; i++) {
      all.add(new TestSection("section-" + i, 10 + i, "a fairly long summary ".repeat(4), true));
    }

    String text = text(all, posted);

    assertThat(text.length()).isLessThanOrEqualTo(MessageLimits.PLAIN_TEXT);
    assertThat(text).contains("section:section-0").doesNotContain("section:section-99");
    assertThat(text.lines().toList()).allMatch(line -> line.startsWith("`/match "));
    assertThat(text).contains(EVERYTHING + "\n`/match ai` top 5 reasons");
  }

  private void hide(String name) {
    ReplayReportProperties.SectionToggle toggle = new ReplayReportProperties.SectionToggle();
    toggle.setEnabled(false);
    properties.getSections().put(name, toggle);
  }

  private record TestSection(String name, int order, String summary, boolean supported)
      implements ReplayReportSection {
    @Override
    public boolean supports(GameModeProfile mode) {
      return supported;
    }

    @Override
    public ReportDetail minimumDetail() {
      return ReportDetail.STANDARD;
    }

    @Override
    public List<String> lines(ReplayReportInput input) {
      return List.of();
    }
  }
}
