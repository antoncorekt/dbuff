package com.ako.dbuff.service.ai.reasons;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReasonsParserTest {
  private final ReasonsParser parser = new ReasonsParser(new ObjectMapper());
  private static final Set<String> IDS = Set.of("RESULT", "F1", "F3", "LANE_1");

  private static String reason(int rank, String evidence) {
    return "{\"rank\":"
        + rank
        + ",\"title\":\"T"
        + rank
        + "\",\"explanation\":\"E"
        + rank
        + "\",\"evidence\":["
        + evidence
        + "]}";
  }

  @Test
  void parsesAPlainArray() {
    List<Reason> reasons =
        parser.parse(
            "[" + reason(1, "\"F3\",\"LANE_1\"") + "," + reason(2, "\"RESULT\"") + "]", IDS);

    assertThat(reasons).hasSize(2);
    assertThat(reasons.get(0)).isEqualTo(new Reason(1, "T1", "E1", List.of("F3", "LANE_1")));
  }

  @Test
  void stripsCodeFencesAndTalkAroundTheArray() {
    String raw = "Here you go:\n```json\n[" + reason(1, "\"F1\"") + "]\n```\nHope it helps";

    assertThat(parser.parse(raw, IDS)).hasSize(1);
  }

  @Test
  void badJsonOrNoArrayIsRejected() {
    assertThatThrownBy(() -> parser.parse("[{\"rank\":1,", IDS))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> parser.parse("no reasons today", IDS))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> parser.parse(null, IDS)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unknownEvidenceIdsAreDropped_andAReasonWithNoValidEvidenceGoesWithThem() {
    String raw =
        "["
            + reason(1, "\"F1\",\"NOPE\"")
            + ","
            + reason(2, "\"INVENTED\"")
            + ","
            + reason(3, "")
            + ","
            + reason(4, "\"LANE_1\"")
            + "]";

    List<Reason> reasons = parser.parse(raw, IDS);

    assertThat(reasons).extracting(Reason::title).containsExactly("T1", "T4");
    assertThat(reasons.get(0).evidence()).containsExactly("F1");
    assertThat(reasons).extracting(Reason::rank).containsExactly(1, 2);
  }

  @Test
  void moreThanFiveKeepsTheFiveBestRanked() {
    StringBuilder raw = new StringBuilder("[");
    for (int rank = 7; rank >= 1; rank--) {
      raw.append(reason(rank, "\"F1\"")).append(rank > 1 ? "," : "");
    }

    List<Reason> reasons = parser.parse(raw.append("]").toString(), IDS);

    assertThat(reasons).extracting(Reason::title).containsExactly("T1", "T2", "T3", "T4", "T5");
    assertThat(reasons).extracting(Reason::rank).containsExactly(1, 2, 3, 4, 5);
  }

  @Test
  void fewerThanFiveIsAccepted_andAnEmptyArrayGivesNothing() {
    assertThat(parser.parse("[" + reason(1, "\"F1\"") + "]", IDS)).hasSize(1);
    assertThat(parser.parse("[]", IDS)).isEmpty();
  }

  @Test
  void trailingProseWithBracketsAndBracketsInsideStringsDoNotBreakTheScan() {
    String raw =
        "```json\n[{\"rank\":1,\"title\":\"A [x] ]\",\"explanation\":\"say \\\"[\\\" ok\","
            + "\"evidence\":[\"F1\"]}]\n```\nNote [see F3] for more";

    List<Reason> reasons = parser.parse(raw, IDS);

    assertThat(reasons).hasSize(1);
    assertThat(reasons.get(0).title()).isEqualTo("A [x] ]");
    assertThat(reasons.get(0).explanation()).isEqualTo("say \"[\" ok");
  }

  @Test
  void leadingBracketedProseIsSkippedAndTheFirstOfTwoArraysWins() {
    String raw =
        "Sure [here it is]: [" + reason(1, "\"F1\"") + "] and also [" + reason(2, "\"F3\"") + "]";

    assertThat(parser.parse(raw, IDS)).extracting(Reason::title).containsExactly("T1");
  }

  @Test
  void anArrayOfNonObjectsIsNotTheAnswer() {
    assertThatThrownBy(() -> parser.parse("[1, 2, 3]", IDS))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aMissingRankKeepsTheOrderOfTheAnswer() {
    String raw =
        "[{\"title\":\"A\",\"explanation\":\"a\",\"evidence\":[\"F1\"]},"
            + "{\"title\":\"B\",\"explanation\":\"b\",\"evidence\":[\"F1\"]}]";

    assertThat(parser.parse(raw, IDS)).extracting(Reason::title).containsExactly("A", "B");
  }

  @Test
  void longTitlesAndExplanationsAreCut() {
    String raw =
        "[{\"rank\":1,\"title\":\""
            + "t".repeat(300)
            + "\",\"explanation\":\""
            + "e".repeat(2000)
            + "\",\"evidence\":[\"F1\"]}]";

    Reason reason = parser.parse(raw, IDS).get(0);

    assertThat(reason.title()).hasSize(ReasonsPromptBuilder.MAX_TITLE_LENGTH).endsWith("…");
    assertThat(reason.explanation())
        .hasSize(ReasonsPromptBuilder.MAX_EXPLANATION_LENGTH)
        .endsWith("…");
  }
}
