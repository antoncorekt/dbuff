package com.ako.dbuff.resources;

import com.ako.dbuff.resources.model.SkillStatResponse;
import com.ako.dbuff.resources.model.SkillStatResponse.Stat;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatReader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Reads what the skill statistics analyzer stored; the API key filter covers these paths. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/skills")
public class SkillStatResource {
  private final SkillStatReader reader;

  @GetMapping("/match/{matchId}")
  public SkillStatResponse match(@PathVariable long matchId) {
    SkillStatResponse response = reader.match(matchId);
    if (response.players().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "No skill statistics are stored for this match");
    }
    return response;
  }

  @GetMapping("/hero/{heroId}")
  public List<Stat> hero(@PathVariable long heroId) {
    List<Stat> stats = reader.hero(heroId);
    if (stats.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "No skill statistics are stored for this hero");
    }
    return stats;
  }
}
