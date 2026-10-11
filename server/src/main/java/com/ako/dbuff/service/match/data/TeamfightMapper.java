package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.TeamfightDomain;
import com.ako.dbuff.dao.model.TeamfightPlayerDomain;
import com.ako.dbuff.dao.repo.TeamfightPlayerRepo;
import com.ako.dbuff.dao.repo.TeamfightRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TeamfightMapper implements MatchDetailMapper {

  private final TeamfightRepo fightRepo;
  private final TeamfightPlayerRepo playerRepo;

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<Object> teamfights = response.getTeamfights();
    List<MatchResponsePlayersInner> players = response.getPlayers();
    List<TeamfightDomain> fights = new ArrayList<>();
    List<TeamfightPlayerDomain> fightPlayers = new ArrayList<>();
    for (int i = 0; teamfights != null && i < teamfights.size(); i++) {
      Map<String, Object> fight = MatchPayload.asMap(teamfights.get(i));
      fights.add(
          TeamfightDomain.builder()
              .matchId(match.getId())
              .fightIndex(i)
              .startTime(MatchPayload.asLong(fight.get("start")))
              .endTime(MatchPayload.asLong(fight.get("end")))
              .lastDeathTime(MatchPayload.asLong(fight.get("last_death")))
              .deaths(MatchPayload.asLong(fight.get("deaths")))
              .build());
      collectPlayers(fightPlayers, match, i, fight, players);
    }
    fightRepo.saveAll(fights);
    playerRepo.saveAll(fightPlayers);
  }

  @Override
  public void deleteByMatchId(long matchId) {
    fightRepo.deleteByMatchId(matchId);
    playerRepo.deleteByMatchId(matchId);
  }

  /** Per-fight player entries follow the order of the match player list. */
  private void collectPlayers(
      List<TeamfightPlayerDomain> rows,
      MatchDomain match,
      int fightIndex,
      Map<String, Object> fight,
      List<MatchResponsePlayersInner> players) {
    List<?> entries = fight.get("players") instanceof List<?> list ? list : List.of();
    if (players == null || entries.size() != players.size()) {
      log.warn(
          "Match {} fight {} lists {} players against {} in the match, skipping player rows",
          match.getId(),
          fightIndex,
          entries.size(),
          players == null ? 0 : players.size());
      return;
    }
    for (int p = 0; p < entries.size(); p++) {
      Long slot = players.get(p).getPlayerSlot();
      if (slot == null) {
        continue;
      }
      Map<String, Object> entry = MatchPayload.asMap(entries.get(p));
      rows.add(
          TeamfightPlayerDomain.builder()
              .matchId(match.getId())
              .fightIndex(fightIndex)
              .playerSlot(slot)
              .deaths(MatchPayload.asLong(entry.get("deaths")))
              .buybacks(MatchPayload.asLong(entry.get("buybacks")))
              .damage(MatchPayload.asLong(entry.get("damage")))
              .healing(MatchPayload.asLong(entry.get("healing")))
              .goldDelta(MatchPayload.asLong(entry.get("gold_delta")))
              .xpDelta(MatchPayload.asLong(entry.get("xp_delta")))
              .build());
    }
  }
}
