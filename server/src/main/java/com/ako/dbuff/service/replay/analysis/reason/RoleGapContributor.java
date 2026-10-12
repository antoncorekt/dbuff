package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.service.replay.analysis.composition.CompositionStage;
import com.ako.dbuff.service.replay.analysis.composition.CompositionToken;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A function the losing team's final composition lacked (no initiator, no disable and so on), as a
 * team reason with the token's name as its code.
 */
@Component
@RequiredArgsConstructor
public class RoleGapContributor implements ReasonContributor {
  private final ReasonProperties properties;

  @Override
  public boolean supports(GameModeProfile mode) {
    return true;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    TeamSide loser = context.loser().orElse(null);
    if (loser == null) {
      return List.of();
    }
    return context
        .composition(loser, CompositionStage.FINAL)
        .map(TeamCompositionDomain::getTokens)
        .filter(tokens -> !tokens.isBlank())
        .map(tokens -> Arrays.stream(tokens.split(",")).map(String::trim).toList())
        .orElse(List.of())
        .stream()
        .map(name -> Enums.find(CompositionToken.class, name))
        .filter(Objects::nonNull)
        .filter(token -> token.function() != null)
        .map(token -> reason(loser, token))
        .toList();
  }

  private Reason reason(TeamSide team, CompositionToken token) {
    return Reason.team(
        Reason.COMMON,
        token.name(),
        team,
        properties.severity(token),
        "%s, the losing team: %s".formatted(team, CompositionToken.words(token.name())));
  }
}
