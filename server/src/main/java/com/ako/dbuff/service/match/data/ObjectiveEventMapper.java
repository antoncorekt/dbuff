package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ObjectiveEventDomain;
import com.ako.dbuff.dao.repo.ObjectiveEventRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ObjectiveEventMapper implements MatchDetailMapper {

  private final ObjectiveEventRepo repo;

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<Object> objectives = response.getObjectives();
    List<ObjectiveEventDomain> rows = new ArrayList<>();
    for (int i = 0; objectives != null && i < objectives.size(); i++) {
      Map<String, Object> event = MatchPayload.asMap(objectives.get(i));
      rows.add(
          ObjectiveEventDomain.builder()
              .matchId(match.getId())
              .objectiveIndex(i)
              .eventTime(MatchPayload.asLong(event.get("time")))
              .eventType(MatchPayload.asString(event.get("type")))
              .unit(MatchPayload.asString(event.get("unit")))
              .objectiveKey(event.get("key") == null ? null : String.valueOf(event.get("key")))
              .playerSlot(MatchPayload.asLong(event.get("player_slot")))
              .victimPlayerSlot(MatchPayload.asLong(event.get("victim_player_slot")))
              .team(MatchPayload.asLong(event.get("team")))
              .eventValue(MatchPayload.asLong(event.get("value")))
              .killer(MatchPayload.asLong(event.get("killer")))
              .build());
    }
    repo.saveAll(rows);
  }

  @Override
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }
}
