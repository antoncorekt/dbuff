package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.match.MatchDetailService;
import com.ako.dbuff.service.replay.read.AnalysisAssembler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchToolsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final AskContext CTX =
      new AskContext("i", "c", List.of(), null, LocalDate.of(2026, 11, 22), Set.of());

  private final AskToolArgs args = new AskToolArgs(null);

  private static JsonNode json(String text) throws Exception {
    return MAPPER.readTree(text);
  }

  @Test
  void missingMatchIsAnError() throws Exception {
    MatchDetailService service = mock(MatchDetailService.class);
    when(service.findById(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> new MatchDetailsTool(service, args).call(json("{\"match_id\":5}"), CTX))
        .isInstanceOf(AskToolException.class)
        .hasMessageContaining("match not stored");
  }

  @Test
  void sectionsArrayBecomesCsv() throws Exception {
    AnalysisAssembler assembler = mock(AnalysisAssembler.class);
    when(assembler.assemble(7L, "lanes,fights")).thenReturn(Map.of("lanes", 1));

    Object result =
        new MatchAnalysisTool(assembler, args)
            .call(json("{\"match_id\":7,\"sections\":[\"lanes\",\"fights\"]}"), CTX);

    assertThat(result).isEqualTo(Map.of("lanes", 1));
    verify(assembler).assemble(7L, "lanes,fights");
  }

  @Test
  void noSectionsMeansAll() throws Exception {
    AnalysisAssembler assembler = mock(AnalysisAssembler.class);

    new MatchAnalysisTool(assembler, args).call(json("{\"match_id\":7}"), CTX);

    verify(assembler).assemble(7L, null);
  }
}
