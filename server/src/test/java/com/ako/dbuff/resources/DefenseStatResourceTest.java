package com.ako.dbuff.resources;

import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import com.ako.dbuff.dao.repo.DefenseStatRepository.Bucket;
import com.ako.dbuff.service.replay.analysis.defense.DefenseStatReader;
import com.ako.dbuff.service.replay.analysis.defense.DefenseStatReader.MatchDefense;
import com.ako.dbuff.service.replay.analysis.defense.DefenseStatReader.Stats;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DefenseStatResourceTest {
  private final DefenseStatReader reader = mock(DefenseStatReader.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new DefenseStatResource(reader)).build();

  @Test
  void aMatchWithNoRowsIsNotFound() throws Exception {
    when(reader.match(6L)).thenReturn(new MatchDefense(6L, true, List.of(), List.of()));

    mvc.perform(get("/api/v1/defense/match/6")).andExpect(status().isNotFound());
  }

  @Test
  void aMatchReturnsPointsAndStatesTheEstimate() throws Exception {
    TeamDefensePointDomain point =
        TeamDefensePointDomain.builder().team("RADIANT").minute(10).ehpMix(5000).build();
    when(reader.match(5L)).thenReturn(new MatchDefense(5L, true, List.of(point), List.of()));

    mvc.perform(get("/api/v1/defense/match/5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.armorEstimated").value(true))
        .andExpect(jsonPath("$.points[0].ehpMix").value(5000))
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void anotherCheckpointIsABadRequest() throws Exception {
    mvc.perform(get("/api/v1/defense/stats?mode=1&checkpoint=M15"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void statsReturnTheBuckets() throws Exception {
    Bucket bucket = new Bucket(-0.25, -0.2, 10, 3);
    when(reader.stats(1, "M20")).thenReturn(new Stats(1, "M20", 12, List.of(bucket), List.of()));

    mvc.perform(get("/api/v1/defense/stats?mode=1&checkpoint=M20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checkpoint").value("M20"))
        .andExpect(jsonPath("$.buckets[0].games").value(10))
        .andExpect(jsonPath("$.buckets[0].wins").value(3));
  }
}
