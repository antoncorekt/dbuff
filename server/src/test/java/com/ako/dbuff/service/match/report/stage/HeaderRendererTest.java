package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.service.match.report.stage.MatchFacts.Result;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ReportMessage;
import java.util.List;
import java.util.stream.Stream;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderRendererTest {

  private final HeaderRenderer renderer = new HeaderRenderer();

  private static MatchFacts facts(Result result, Long radiant, Long dire) {
    return new MatchFacts(77L, "ability draft", result, 2100L, radiant, dire);
  }

  static Stream<Arguments> titles() {
    Result win = new Result(2, 0);
    return Stream.of(
        Arguments.of(
            MatchReportStage.REQUESTED,
            win,
            "Match 77 · ability draft · Win · 35 min · OpenDota parse requested"),
        Arguments.of(
            MatchReportStage.PARSED,
            win,
            "Match 77 · ability draft · Win (40-25) · 35 min · OpenDota data ready"),
        Arguments.of(
            MatchReportStage.REPLAY_PENDING,
            new Result(0, 1),
            "Match 77 · ability draft · Loss (40-25) · 35 min · replay processing"),
        Arguments.of(
            MatchReportStage.PARSE_TIMED_OUT,
            new Result(1, 1),
            "Match 77 · ability draft · Mixed (1W/1L) (40-25) · 35 min · OpenDota did not parse"
                + " this match"),
        Arguments.of(
            MatchReportStage.REPLAY_DONE,
            win,
            "Match 77 · ability draft · Win (40-25) · 35 min · replay analysis ready"),
        Arguments.of(
            MatchReportStage.REPLAY_UNAVAILABLE,
            win,
            "Match 77 · ability draft · Win (40-25) · 35 min · replay analysis not available"));
  }

  @ParameterizedTest
  @MethodSource("titles")
  void titleStatesTheStageAndTheFacts(MatchReportStage stage, Result result, String expected) {
    assertThat(renderer.title(stage, facts(result, 40L, 25L))).isEqualTo(expected);
  }

  @Test
  void stageOneLeavesOutTheScoreEvenIfKnown() {
    assertThat(renderer.title(MatchReportStage.REQUESTED, facts(new Result(1, 0), 40L, 25L)))
        .doesNotContain("40-25");
  }

  @Test
  void missingFactsAreLeftOutOfTheTitle() {
    MatchFacts bare = new MatchFacts(5L, null, new Result(0, 0), null, null, null);

    assertThat(renderer.title(MatchReportStage.PARSED, bare))
        .isEqualTo("Match 5 · OpenDota data ready");
  }

  static Stream<Arguments> accents() {
    return Stream.of(
        Arguments.of(new Result(3, 0), Accent.WIN),
        Arguments.of(new Result(0, 2), Accent.LOSS),
        Arguments.of(new Result(1, 1), Accent.NEUTRAL),
        Arguments.of(new Result(0, 0), Accent.INFO));
  }

  @ParameterizedTest
  @MethodSource("accents")
  void cardAccentFollowsTheTrackedPlayersResult(Result result, Accent accent) {
    for (MatchReportStage stage : MatchReportStage.values()) {
      ReportMessage.Components header = renderer.render(stage, facts(result, 1L, 2L));

      Container card = (Container) header.components().get(0);
      assertThat(card.getAccentColorRaw()).isEqualTo(accent.rgb());
    }
  }

  @Test
  void cardHoldsTheTitleAndTheLinkAndTheFallbackIsPlainText() {
    MatchFacts facts = facts(new Result(1, 0), 40L, 25L);

    ReportMessage.Components header = renderer.render(MatchReportStage.PARSED, facts);

    Container card = (Container) header.components().get(0);
    List<String> texts =
        card.getComponents().stream().map(c -> ((TextDisplay) c).getContent()).toList();
    assertThat(texts)
        .containsExactly(
            "**" + renderer.title(MatchReportStage.PARSED, facts) + "**",
            "-# https://www.dotabuff.com/matches/77");
    assertThat(header.fallback())
        .isEqualTo(
            renderer.title(MatchReportStage.PARSED, facts)
                + "\nhttps://www.dotabuff.com/matches/77");
    assertThat(header.componentCount()).isEqualTo(3);
  }

  @Test
  void legacyHeadersKeepTheirWording() {
    MatchDomain match =
        MatchDomain.builder()
            .id(9L)
            .gameModeName("game_mode_ability_draft")
            .radiantScore(30L)
            .direScore(20L)
            .duration(1850L)
            .build();

    assertThat(renderer.legacyWaiting(9L)).isEqualTo("Match 9 — waiting for parsed data...");
    assertThat(renderer.legacyParsed(match, "Win"))
        .isEqualTo("Match 9 - game_mode_ability_draft - Win (30-20) with duration 30 min");
  }

  @Test
  void readableModeStripsThePrefix() {
    assertThat(MatchFacts.readableMode("game_mode_all_draft")).isEqualTo("all draft");
    assertThat(MatchFacts.readableMode(null)).isNull();
  }

  @Test
  void stagesMoveOnlyForward() {
    assertThat(MatchReportStage.PARSED.mayFollow(MatchReportStage.REQUESTED)).isTrue();
    assertThat(MatchReportStage.REPLAY_DONE.mayFollow(MatchReportStage.REPLAY_PENDING)).isTrue();
    assertThat(MatchReportStage.REPLAY_PENDING.mayFollow(MatchReportStage.REPLAY_DONE)).isFalse();
    assertThat(MatchReportStage.PARSED.mayFollow(MatchReportStage.PARSED)).isFalse();
    assertThat(MatchReportStage.REPLAY_UNAVAILABLE.mayFollow(MatchReportStage.REPLAY_DONE))
        .isFalse();
    assertThat(MatchReportStage.PARSE_TIMED_OUT.mayFollow(MatchReportStage.REQUESTED)).isTrue();
    assertThat(MatchReportStage.PARSE_TIMED_OUT.mayFollow(MatchReportStage.PARSED)).isFalse();
    assertThat(MatchReportStage.PARSED.mayFollow(MatchReportStage.PARSE_TIMED_OUT)).isFalse();
    assertThat(MatchReportStage.REPLAY_DONE.mayFollow(MatchReportStage.PARSE_TIMED_OUT)).isTrue();
    assertThat(MatchReportStage.REPLAY_PENDING.mayFollow(MatchReportStage.PARSE_TIMED_OUT))
        .isTrue();
    assertThat(MatchReportStage.PARSED.mayFollow(null)).isFalse();
  }
}
