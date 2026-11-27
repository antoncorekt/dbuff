package com.ako.dbuff.resources;

import com.ako.dbuff.service.replay.analysis.defense.DefenseStatReader;
import com.ako.dbuff.service.replay.analysis.defense.DefenseStatReader.MatchDefense;
import com.ako.dbuff.service.replay.analysis.defense.DefenseStatReader.Stats;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Reads what the defense analyzers stored; the API key filter covers these paths. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/defense")
public class DefenseStatResource {
  private final DefenseStatReader reader;

  @GetMapping("/match/{matchId}")
  public MatchDefense match(@PathVariable long matchId) {
    MatchDefense response = reader.match(matchId);
    if (response.points().isEmpty() && response.items().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "No defense rows are stored for this match");
    }
    return response;
  }

  @GetMapping("/stats")
  public Stats stats(@RequestParam int mode, @RequestParam String checkpoint) {
    if (!DefenseStatReader.CHECKPOINTS.contains(checkpoint)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "checkpoint must be one of M10, M20, M30, END");
    }
    return reader.stats(mode, checkpoint);
  }
}
