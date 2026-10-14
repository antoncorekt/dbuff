package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Gathers the facts a header states, at the first sight of a match and once it is parsed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchFactsFactory {
  private final ConstantsManagers constants;
  private final PlayerGameStatisticRepo statRepo;

  public MatchFacts fromSummary(long matchId, NewMatchSummary summary) {
    return new MatchFacts(
        matchId,
        modeOf(summary.gameMode()),
        MatchFacts.Result.ofLines(summary.lines()),
        summary.duration(),
        null,
        null);
  }

  public MatchFacts fromParsed(MatchDomain match, DbufInstanceConfigDomain config) {
    Set<Long> focus = config.getPlayerIds();
    List<PlayerMatchStatisticDomain> focusStats =
        statRepo.findAllByMatchId(match.getId()).stream()
            .filter(s -> focus.contains(s.getPlayerId()))
            .toList();
    String mode =
        match.getGameModeName() != null
            ? MatchFacts.readableMode(match.getGameModeName())
            : modeOf(match.getGameModeId());
    return new MatchFacts(
        match.getId(),
        mode,
        MatchFacts.Result.ofStats(focusStats),
        match.getDuration(),
        match.getRadiantScore(),
        match.getDireScore());
  }

  private String modeOf(Long gameModeId) {
    if (gameModeId == null) {
      return null;
    }
    try {
      MatchTypeConstant type = constants.getMatchTypeConstantMap().get(String.valueOf(gameModeId));
      return type == null ? null : MatchFacts.readableMode(type.getName());
    } catch (RuntimeException e) {
      log.debug("Game mode {} has no name: {}", gameModeId, e.getMessage());
      return null;
    }
  }
}
