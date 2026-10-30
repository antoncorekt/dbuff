package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.EndGameSummaryDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.EndGameSummaryRepo;
import com.ako.dbuff.service.replay.analysis.behaviour.ObjectiveKind;
import com.ako.dbuff.service.replay.analysis.building.BuildingName;
import com.ako.dbuff.service.replay.analysis.endgame.EndGameProperties;
import com.ako.dbuff.service.replay.analysis.endgame.EndGameTurningPoint;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.team.TeamNetWorth;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * How the game ended, in at most two lines: who won, the lead the loser held and the turning point
 * (or that the loser threw the lead), and the buybacks of the last minutes. It reads {@code
 * end_game_summary} only, so it is independent of other sections.
 */
@Component
@RequiredArgsConstructor
public class EndGameSection implements ReplayReportSection {
  private final EndGameSummaryRepo summaryRepo;
  private final ReplayReportProperties properties;

  private final EndGameProperties endGameProperties;

  @Override
  public String name() {
    return "endgame";
  }

  @Override
  public int order() {
    return 79;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  private record Ended(List<String> lines, TeamSide winner, TeamSide yours) {}

  @Override
  public List<String> lines(ReplayReportInput input) {
    return ended(input).map(Ended::lines).orElse(List.of());
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    return ended(input)
        .map(
            e ->
                new SectionView(
                    "How the game ended",
                    e.lines().get(0),
                    SectionViews.forTeam(e.winner(), e.yours()),
                    e.lines().subList(1, e.lines().size()),
                    null));
  }

  private Optional<Ended> ended(ReplayReportInput input) {
    List<EndGameSummaryDomain> stored = summaryRepo.findByMatchId(input.matchId());
    EndGameSummaryDomain row =
        stored == null
            ? null
            : stored.stream()
                .filter(Objects::nonNull)
                .max(
                    Comparator.comparing(
                        EndGameSummaryDomain::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    TeamSide winner = row == null ? null : Enums.find(TeamSide.class, row.getWinnerTeam());
    if (winner == null) {
      return Optional.empty();
    }
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    int max = Math.max(LineText.ELLIPSIS.length() + 1, properties.getMaxEndGameLineLength());
    List<String> lines = new ArrayList<>();
    lines.add(LineText.cut(verdict(row, winner, yours), max));
    buybacks(row, winner, yours).ifPresent(l -> lines.add(LineText.cut(l, max)));
    return Optional.of(new Ended(lines, winner, yours));
  }

  private String verdict(EndGameSummaryDomain r, TeamSide winner, TeamSide yours) {
    String won = Prettify.teamOf(winner.name(), yours);
    String loser = Prettify.teamOf(winner.opponent().name(), yours);
    String end =
        r.getDurationSeconds() == null ? "" : " at " + GameTime.clock(r.getDurationSeconds());
    if (!Boolean.TRUE.equals(r.getLeadKnown())) {
      return won + " won" + end + ".";
    }
    if (r.getPeakLeadSeconds() == null || r.getPeakLeadGold() == null) {
      return "%s won%s; %s never led by %s gold or more."
          .formatted(won, end, loser, BehaviourWording.gold(endGameProperties.getMinLeadGold()));
    }
    if (Boolean.FALSE.equals(r.getLeadLost())) {
      return "%s won%s although %s still led%s at the last minute."
          .formatted(
              won,
              end,
              loser,
              r.getLastLeadGold() == null
                  ? ""
                  : " by " + BehaviourWording.gold(r.getLastLeadGold()));
    }
    String lead =
        "%s at %s"
            .formatted(
                BehaviourWording.gold(r.getPeakLeadGold()), GameTime.clock(r.getPeakLeadSeconds()));
    String head =
        Boolean.TRUE.equals(r.getThrown())
            ? "%s threw a lead of %s and lost%s".formatted(loser, lead, end)
            : "%s won%s after %s led by %s".formatted(won, end, loser, lead);
    return head + turningPoint(r, winner, yours);
  }

  private static String turningPoint(EndGameSummaryDomain r, TeamSide winner, TeamSide yours) {
    if (r.getTurningSeconds() == null) {
      return ".";
    }
    String fight = "the won fight at " + GameTime.clock(r.getTurningSeconds());
    String won = Prettify.teamOf(winner.name(), yours);
    if (EndGameTurningPoint.CONVERTED.equals(r.getTurningKind())) {
      return "; the turning point was %s, which %s turned into %s."
          .formatted(fight, won, objective(r));
    }
    return "; the turning point was %s, which %s gained %s."
        .formatted(
            fight,
            won,
            r.getTurningGoldChange() == null
                ? "gold"
                : BehaviourWording.gold(r.getTurningGoldChange()));
  }

  private static String objective(EndGameSummaryDomain r) {
    ObjectiveKind kind = Enums.find(ObjectiveKind.class, r.getObjectiveKind());
    if (kind == ObjectiveKind.TOWER) {
      String lane = BuildingName.parse(r.getObjectiveKey()).map(BuildingName::lane).orElse(null);
      return ObjectiveWording.tower(r.getObjectiveKey(), lane);
    }
    return kind == null ? "an objective" : kind.words();
  }

  private Optional<String> buybacks(EndGameSummaryDomain r, TeamSide winner, TeamSide yours) {
    if (r.getRadiantBuybacks() == null
        || r.getDireBuybacks() == null
        || r.getBuybackWindowSeconds() == null
        || r.getRadiantBuybacks() + r.getDireBuybacks() == 0) {
      return Optional.empty();
    }
    TeamSide loser = winner.opponent();
    int loserCount = count(r, loser);
    int winnerCount = count(r, winner);
    return Optional.of(
        "%s used %s in the last %s, %s %d."
            .formatted(
                Prettify.teamOf(loser.name(), yours),
                buyback(loserCount),
                window(r.getBuybackWindowSeconds()),
                Prettify.teamOf(winner.name(), yours),
                winnerCount));
  }

  private static int count(EndGameSummaryDomain r, TeamSide side) {
    return side == TeamSide.RADIANT ? r.getRadiantBuybacks() : r.getDireBuybacks();
  }

  private static String buyback(int count) {
    return count + (count == 1 ? " buyback" : " buybacks");
  }

  private static String window(int seconds) {
    return seconds % TeamNetWorth.STEP_SECONDS == 0
        ? seconds / TeamNetWorth.STEP_SECONDS
            + (seconds == TeamNetWorth.STEP_SECONDS ? " minute" : " minutes")
        : seconds + " seconds";
  }
}
