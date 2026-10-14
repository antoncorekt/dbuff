package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
@RequiredArgsConstructor
class RolesSectionReader implements AnalysisSectionReader {
  private final PlayerRoleRepo roleRepo;
  private final TeamCompositionRepo compositionRepo;

  public record Roles(List<PlayerRole> players, List<Composition> compositions) {}

  public record PlayerRole(Long slot, Integer role, String source, Double confidence) {}

  public record Composition(String team, String stage, String tokens) {}

  @Override
  public String name() {
    return "roles";
  }

  @Override
  @Transactional(readOnly = true)
  public Roles read(long matchId) {
    return new Roles(
        Sorted.of(roleRepo.findByMatchId(matchId), Sorted.by(PlayerRoleDomain::getPlayerSlot))
            .stream()
            .map(
                r ->
                    new PlayerRole(
                        r.getPlayerSlot(), r.getRole(), r.getSource(), r.getConfidence()))
            .toList(),
        Sorted.of(
                compositionRepo.findByMatchId(matchId),
                Sorted.by(TeamCompositionDomain::getTeam, TeamCompositionDomain::getStage))
            .stream()
            .map(c -> new Composition(c.getTeam(), c.getStage(), c.getTokens()))
            .toList());
  }
}
