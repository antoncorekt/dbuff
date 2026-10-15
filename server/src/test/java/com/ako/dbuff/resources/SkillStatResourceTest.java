package com.ako.dbuff.resources;

import com.ako.dbuff.config.ApiKeyFilter;
import com.ako.dbuff.config.ApiSecurityProperties;
import com.ako.dbuff.resources.model.SkillStatResponse;
import com.ako.dbuff.resources.model.SkillStatResponse.PlayerStats;
import com.ako.dbuff.resources.model.SkillStatResponse.Stat;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatKind;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SkillStatResourceTest {
  private final SkillStatReader reader = mock(SkillStatReader.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new SkillStatResource(reader)).build();

  private static Stat stat() {
    return new Stat(SkillStatKind.SKILL, 1L, null, 5L, 14, 8, 8 / 14.0, null, 0.3, 12, "text");
  }

  @Test
  void aMatchReturnsItsPlayersWithTheirFigures() throws Exception {
    when(reader.match(5L))
        .thenReturn(new SkillStatResponse(5L, List.of(new PlayerStats(0L, 10L, List.of(stat())))));

    mvc.perform(get("/api/v1/skills/match/5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.players[0].heroId").value(10))
        .andExpect(jsonPath("$.players[0].stats[0].games").value(14))
        .andExpect(jsonPath("$.players[0].stats[0].text").value("text"));
  }

  @Test
  void aMatchWithNothingStoredIsNotFound() throws Exception {
    when(reader.match(6L)).thenReturn(new SkillStatResponse(6L, List.of()));

    mvc.perform(get("/api/v1/skills/match/6")).andExpect(status().isNotFound());
  }

  @Test
  void aHeroReturnsItsFiguresOrNotFound() throws Exception {
    when(reader.hero(10L)).thenReturn(List.of(stat()));
    when(reader.hero(11L)).thenReturn(List.of());

    mvc.perform(get("/api/v1/skills/hero/10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].kind").value("SKILL"));
    mvc.perform(get("/api/v1/skills/hero/11")).andExpect(status().isNotFound());
  }

  @Test
  void bothReadsNeedTheApiKey() throws Exception {
    ApiSecurityProperties properties = new ApiSecurityProperties();
    properties.setKey("secret");
    MockMvc guarded =
        MockMvcBuilders.standaloneSetup(new SkillStatResource(reader))
            .addFilters(new ApiKeyFilter(properties))
            .build();
    when(reader.hero(10L)).thenReturn(List.of(stat()));

    guarded.perform(get("/api/v1/skills/match/5")).andExpect(status().isUnauthorized());
    guarded.perform(get("/api/v1/skills/hero/10")).andExpect(status().isUnauthorized());
    guarded
        .perform(get("/api/v1/skills/hero/10").header(ApiKeyFilter.HEADER, "secret"))
        .andExpect(status().isOk());
  }
}
