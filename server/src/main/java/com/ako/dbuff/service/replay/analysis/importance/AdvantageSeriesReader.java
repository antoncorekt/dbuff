package com.ako.dbuff.service.replay.analysis.importance;

import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.TeamTimelineDomain;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.TeamTimelineRepo;
import com.ako.dbuff.service.replay.analysis.team.TeamNetWorth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads OpenDota's per-minute gold and experience advantage of a match with the teams' net worth.
 * The net worth is the players' {@code networth}, or their {@code gold} when a parser version did
 * not store it.
 */
@Component
@RequiredArgsConstructor
public class AdvantageSeriesReader {
  private final TeamTimelineRepo teamRepo;
  private final PlayerTimelineRepo playerRepo;

  /** Empty when a minute has no advantage figure, the minutes have a gap or no net worth exists. */
  public Optional<AdvantageSeries> read(long matchId) {
    List<TeamTimelineDomain> rows =
        teamRepo.findByMatchId(matchId).stream()
            .filter(r -> r.getGameMinute() != null)
            .sorted(Comparator.comparing(TeamTimelineDomain::getGameMinute))
            .toList();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    double[] gold = new double[rows.size()];
    double[] xp = new double[rows.size()];
    for (int i = 0; i < rows.size(); i++) {
      TeamTimelineDomain row = rows.get(i);
      if (row.getGameMinute() != i || row.getRadiantGoldAdv() == null) {
        return Optional.empty();
      }
      gold[i] = row.getRadiantGoldAdv();
      xp[i] = row.getRadiantXpAdv() == null ? 0 : row.getRadiantXpAdv();
    }
    List<PlayerTimelineDomain> players = playerRepo.findByMatchId(matchId);
    TeamNetWorth netWorth = TeamNetWorth.of(players);
    if (netWorth == null) {
      netWorth =
          TeamNetWorth.of(players.stream().map(AdvantageSeriesReader::goldAsNetWorth).toList());
    }
    return netWorth == null
        ? Optional.empty()
        : Optional.of(new AdvantageSeries(gold, xp, netWorth.radiant(), netWorth.dire()));
  }

  private static PlayerTimelineDomain goldAsNetWorth(PlayerTimelineDomain row) {
    return PlayerTimelineDomain.builder()
        .playerSlot(row.getPlayerSlot())
        .gameMinute(row.getGameMinute())
        .networth(row.getGold())
        .build();
  }
}
