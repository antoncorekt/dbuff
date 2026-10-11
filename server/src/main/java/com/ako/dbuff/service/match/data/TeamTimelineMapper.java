package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.TeamTimelineDomain;
import com.ako.dbuff.dao.repo.TeamTimelineRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TeamTimelineMapper implements MatchDetailMapper {

  private final TeamTimelineRepo repo;

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<BigDecimal> gold = response.getRadiantGoldAdv();
    List<BigDecimal> xp = response.getRadiantXpAdv();
    int minutes = Math.max(gold == null ? 0 : gold.size(), xp == null ? 0 : xp.size());
    List<TeamTimelineDomain> rows = new ArrayList<>();
    for (int minute = 0; minute < minutes; minute++) {
      rows.add(
          TeamTimelineDomain.builder()
              .matchId(match.getId())
              .gameMinute(minute)
              .radiantGoldAdv(toLong(MatchPayload.at(gold, minute)))
              .radiantXpAdv(toLong(MatchPayload.at(xp, minute)))
              .build());
    }
    repo.saveAll(rows);
  }

  @Override
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }

  private static Long toLong(BigDecimal value) {
    return value == null ? null : value.longValue();
  }
}
