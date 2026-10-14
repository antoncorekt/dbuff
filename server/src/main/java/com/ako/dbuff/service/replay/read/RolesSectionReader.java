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
  public Roles read(long matchId, RunStatus run) {
    return new Roles(
        roleRepo.findByMatchId(matchId).stream()
            .sorted(
                RowOrder.nullsLast(PlayerRoleDomain::getPlayerSlot)
                    .thenComparing(RowOrder.nullsLast(PlayerRoleDomain::getId)))
            .map(
                r ->
                    new PlayerRole(
                        r.getPlayerSlot(), r.getRole(), r.getSource(), r.getConfidence()))
            .toList(),
        compositionRepo.findByMatchId(matchId).stream()
            .sorted(
                RowOrder.nullsLast(TeamCompositionDomain::getTeam)
                    .thenComparing(RowOrder.nullsLast(TeamCompositionDomain::getStage))
                    .thenComparing(RowOrder.nullsLast(TeamCompositionDomain::getId)))
            .map(c -> new Composition(c.getTeam(), c.getStage(), c.getTokens()))
            .toList());
  }
}
