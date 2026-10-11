package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.DraftEventDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.DraftEventRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DraftEventMapper implements MatchDetailMapper {

  private final DraftEventRepo repo;

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<DraftEventDomain> events =
        CollectionUtils.emptyIfNull(response.getPicksBans()).stream()
            .map(
                pb ->
                    DraftEventDomain.builder()
                        .matchId(match.getId())
                        .draftOrder(pb.getOrder() == null ? null : pb.getOrder().intValue())
                        .pick(pb.getIsPick())
                        .heroId(pb.getHeroId())
                        .team(pb.getTeam() == null ? null : pb.getTeam().intValue())
                        .build())
            .toList();
    repo.saveAll(events);
  }

  @Override
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }
}
