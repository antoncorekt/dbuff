package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import java.time.ZoneId;
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
  private final DbufInstanceConfigRepository instanceRepo;

  public MatchFacts fromSummary(
      MatchDomain match, DbufInstanceConfigDomain config, NewMatchSummary summary) {
    return new MatchFacts(
        match.getId(),
        modeOf(summary.gameMode()),
        MatchFacts.Result.ofLines(summary.lines()),
        summary.duration(),
        null,
        null,
        match.getStartTime(),
        config.effectiveSettings().zone());
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
        match.getDireScore(),
        match.getStartTime(),
        config.effectiveSettings().zone());
  }

  /** The facts kept on the row, shown in the zone of the instance the row belongs to. */
  public MatchFacts fromRow(MatchReportThreadDomain row, MatchDomain match) {
    return MatchFacts.fromRow(row, match, zoneOf(row.getInstanceId()));
  }

  private ZoneId zoneOf(String instanceId) {
    try {
      return instanceId == null
          ? ZoneId.systemDefault()
          : instanceRepo
              .findById(instanceId)
              .map(i -> i.effectiveSettings().zone())
              .orElse(ZoneId.systemDefault());
    } catch (RuntimeException e) {
      log.debug("Instance {} has no readable zone: {}", instanceId, e.getMessage());
      return ZoneId.systemDefault();
    }
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
