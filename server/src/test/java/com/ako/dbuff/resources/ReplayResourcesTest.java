package com.ako.dbuff.resources;

import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new ReplayResources(runRepo, analysis, new ReplayProcessingProperties()))
            .build();
  }

  private ReplayParseRunDomain run(long matchId, ReplayStatus status) {
    return ReplayParseRunDomain.builder().matchId(matchId).status(status).attempts(1).build();
  }

  @Test
  void run_reportsHowTheRunEnded_orNotFound() throws Exception {
    when(runRepo.findByMatchIdAndParserVersion(5L, 1))
        .thenReturn(Optional.of(run(5L, ReplayStatus.PARTIAL)));
    when(runRepo.findByMatchIdAndParserVersion(6L, 1)).thenReturn(Optional.empty());

    mvc.perform(get("/api/v1/replay/5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PARTIAL"));
    mvc.perform(get("/api/v1/replay/6")).andExpect(status().isNotFound());
  }

  @Test
  void reanalyze_runsTheAnalyzersOnlyForAFinishedRun() throws Exception {
    when(runRepo.findByMatchIdAndParserVersion(5L, 1))
        .thenReturn(Optional.of(run(5L, ReplayStatus.DONE)));
    when(runRepo.findByMatchIdAndParserVersion(7L, 1))
        .thenReturn(Optional.of(run(7L, ReplayStatus.FAILED_DOWNLOAD)));
    when(analysis.analyze(5L, List.of())).thenReturn(List.of("spikes"));

    mvc.perform(post("/api/v1/replay/5/reanalyze"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.failedAnalyzers[0]").value("spikes"));
    mvc.perform(post("/api/v1/replay/7/reanalyze")).andExpect(status().isNotFound());

    verify(analysis, never()).analyze(eq(7L), anyCollection());
  }

  @Test
  void reanalyzeAll_goesThroughTheFinishedRunsUpToTheLimit() throws Exception {
    when(runRepo.findByParserVersionAndStatusInOrderByMatchId(eq(1), anyCollection()))
        .thenReturn(
            List.of(
                run(1L, ReplayStatus.DONE), run(2L, ReplayStatus.DONE), run(3L, ReplayStatus.PARTIAL)));
    when(analysis.analyze(anyLong(), anyCollection())).thenReturn(List.of());
    when(analysis.analyze(2L, List.of())).thenReturn(List.of("fights"));

    mvc.perform(post("/api/v1/replay/reanalyze").param("limit", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.analysed").value(2))
        .andExpect(jsonPath("$.available").value(3))
        .andExpect(jsonPath("$.withFailures").value(1));
  }
}
