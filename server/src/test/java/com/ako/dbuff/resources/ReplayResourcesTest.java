package com.ako.dbuff.resources;

import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisService;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.ako.dbuff.service.replay.read.AnalysisAssembler;
import com.ako.dbuff.service.replay.read.AnalysisSectionReader;
import com.ako.dbuff.service.replay.read.RunStatus;
import com.ako.dbuff.service.replay.read.RunStatusReader;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReplayResourcesTest {

  private final ReplayParseRunRepo runRepo = mock(ReplayParseRunRepo.class);
  private final ReplayAnalysisService analysis = mock(ReplayAnalysisService.class);
  private final StoredMatchModes matchModes = mock(StoredMatchModes.class);
  private final ReplayProcessingProperties properties = new ReplayProcessingProperties();
  private final RunStatusReader statusReader = new RunStatusReader(runRepo, properties);
  private MockMvc mvc;

  private static AnalysisSectionReader section(String name, Object value) {
    return new AnalysisSectionReader() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public Object read(long matchId, RunStatus run) {
        return value;
      }
    };
  }

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new ReplayResources(
                    runRepo,
                    analysis,
                    matchModes,
                    properties,
                    statusReader,
                    new AnalysisAssembler(
                        List.of(section("run", 1), section("roles", 2)), statusReader)))
            .build();
  }

  private ReplayParseRunDomain run(long matchId, ReplayStatus status) {
    return ReplayParseRunDomain.builder().matchId(matchId).status(status).attempts(1).build();
  }

  @Test
  void run_reportsHowTheRunEnded_orNotFound() throws Exception {
    when(runRepo.findByMatchIdAndParserVersion(5L, properties.getParserVersion()))
        .thenReturn(Optional.of(run(5L, ReplayStatus.PARTIAL)));
    when(runRepo.findByMatchIdAndParserVersion(6L, properties.getParserVersion()))
        .thenReturn(Optional.empty());

    mvc.perform(get("/api/v1/replay/5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchId").value(5))
        .andExpect(jsonPath("$.status").value("PARTIAL"))
        .andExpect(jsonPath("$.attempts").value(1))
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasKey("error")))
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasKey("mapVersion")))
        .andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(5)));
    mvc.perform(get("/api/v1/replay/6")).andExpect(status().isNotFound());
  }

  @Test
  void analysis_answersEverySectionBySelectionOr400ForAnUnknownOne_404ForAnUnstoredMatch()
      throws Exception {
    when(runRepo.findByMatchIdAndParserVersion(5L, properties.getParserVersion()))
        .thenReturn(Optional.of(run(5L, ReplayStatus.DONE)));
    when(runRepo.findByMatchIdAndParserVersion(6L, properties.getParserVersion()))
        .thenReturn(Optional.empty());

    mvc.perform(get("/api/v1/replay/5/analysis"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.run").value(1))
        .andExpect(jsonPath("$.roles").value(2));
    mvc.perform(get("/api/v1/replay/5/analysis").param("sections", "roles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles").value(2))
        .andExpect(jsonPath("$.run").doesNotExist());
    mvc.perform(get("/api/v1/replay/5/analysis").param("sections", "roles,nope"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("nope")))
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("run, roles")));
    mvc.perform(get("/api/v1/replay/5/analysis").param("sections", "run,,roles"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("empty entry")));
    mvc.perform(get("/api/v1/replay/5/analysis").param("sections", ","))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/replay/6/analysis")).andExpect(status().isNotFound());
  }

  @Test
  void reanalyze_runsTheAnalyzersOnlyForAFinishedRun() throws Exception {
    when(runRepo.findByMatchIdAndParserVersion(5L, properties.getParserVersion()))
        .thenReturn(Optional.of(run(5L, ReplayStatus.DONE)));
    when(runRepo.findByMatchIdAndParserVersion(7L, properties.getParserVersion()))
        .thenReturn(Optional.of(run(7L, ReplayStatus.PARTIAL)));
    when(matchModes.activeFor(anyLong())).thenReturn(Optional.of(TestModes.ABILITY_DRAFT));
    when(analysis.analyze(5L, TestModes.ABILITY_DRAFT, List.of())).thenReturn(List.of("spikes"));

    mvc.perform(post("/api/v1/replay/5/reanalyze"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.failedAnalyzers[0]").value("spikes"));
    mvc.perform(post("/api/v1/replay/7/reanalyze")).andExpect(status().isNotFound());

    verify(analysis, never()).analyze(eq(7L), any(), anyCollection());
  }

  @Test
  void reanalyzeAll_rejectsALimitOutOfRange() throws Exception {
    mvc.perform(post("/api/v1/replay/reanalyze").param("limit", "0"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/replay/reanalyze").param("limit", "501"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void reanalyzeAll_goesThroughTheFinishedRunsUpToTheLimit() throws Exception {
    when(runRepo.findByParserVersionAndStatusInOrderByMatchId(
            eq(properties.getParserVersion()), anyCollection()))
        .thenReturn(
            List.of(
                run(1L, ReplayStatus.DONE),
                run(2L, ReplayStatus.DONE),
                run(3L, ReplayStatus.DONE)));
    when(matchModes.activeFor(anyLong())).thenReturn(Optional.of(TestModes.ABILITY_DRAFT));
    when(matchModes.activeFor(3L)).thenReturn(Optional.empty());
    when(analysis.analyze(anyLong(), any(), anyCollection())).thenReturn(List.of());
    when(analysis.analyze(2L, TestModes.ABILITY_DRAFT, List.of())).thenReturn(List.of("fights"));

    mvc.perform(post("/api/v1/replay/reanalyze").param("limit", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.analysed").value(2))
        .andExpect(jsonPath("$.skipped").value(0))
        .andExpect(jsonPath("$.available").value(3))
        .andExpect(jsonPath("$.withFailures").value(1));
  }

  @Test
  void reanalyze_saysSoWhenTheMatchHasNoActiveMode() throws Exception {
    when(runRepo.findByMatchIdAndParserVersion(5L, properties.getParserVersion()))
        .thenReturn(Optional.of(run(5L, ReplayStatus.DONE)));
    when(matchModes.activeFor(5L)).thenReturn(Optional.empty());

    mvc.perform(post("/api/v1/replay/5/reanalyze"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());

    verify(analysis, never()).analyze(anyLong(), any(), anyCollection());
  }

  @Test
  void reanalyzeAll_countsAMatchWithoutAnActiveModeAsSkipped() throws Exception {
    when(runRepo.findByParserVersionAndStatusInOrderByMatchId(
            eq(properties.getParserVersion()), anyCollection()))
        .thenReturn(List.of(run(1L, ReplayStatus.DONE), run(3L, ReplayStatus.DONE)));
    when(matchModes.activeFor(1L)).thenReturn(Optional.of(TestModes.ABILITY_DRAFT));
    when(matchModes.activeFor(3L)).thenReturn(Optional.empty());
    when(analysis.analyze(anyLong(), any(), anyCollection())).thenReturn(List.of());

    mvc.perform(post("/api/v1/replay/reanalyze"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.analysed").value(1))
        .andExpect(jsonPath("$.skipped").value(1));

    verify(analysis, never()).analyze(eq(3L), any(), anyCollection());
  }
}
