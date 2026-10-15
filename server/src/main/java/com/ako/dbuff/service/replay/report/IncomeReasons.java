package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.PlayerOutlierDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.PlayerOutlierRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.replay.analysis.behaviour.RotationReason;
import com.ako.dbuff.service.replay.analysis.behaviour.RotationVerdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Why a hero's income fell, in plain words, from what the analyzers stored: his deaths, the point
 * where his income broke away from his team's (with the stored explanation), and the rotations that
 * cost him farm. Nothing is guessed; a dip no stored fact explains has no reason.
 */
@Component
@RequiredArgsConstructor
public class IncomeReasons {
  private static final double OUTLIER_SLACK_SECONDS = 60;
  private static final int MAX_REASONS = 3;
  private static final int MAX_DEATHS_NAMED = 2;

  private final DeathAnalysisRepo deathRepo;
  private final PlayerOutlierRepo outlierRepo;
  private final TeamRotationRepo rotationRepo;

  /** The stored facts of one match, read once. */
  public Explainer read(long matchId, Map<Long, String> heroes) {
    return new Explainer(
        nonNull(deathRepo.findByMatchId(matchId)),
        nonNull(outlierRepo.findByMatchId(matchId)),
        nonNull(rotationRepo.findByMatchId(matchId)),
        heroes);
  }

  private static <T> List<T> nonNull(List<T> rows) {
    return rows == null ? List.of() : rows.stream().filter(Objects::nonNull).toList();
  }

  public record Explainer(
      List<DeathAnalysisDomain> deaths,
      List<PlayerOutlierDomain> outliers,
      List<TeamRotationDomain> rotations,
      Map<Long, String> heroes) {

    /** The reasons the stored facts give for a stretch of a hero's income, empty if none. */
    List<String> why(long slot, double fromSeconds, double toSeconds) {
      List<String> reasons = new ArrayList<>();
      String hero = BehaviourWording.hero(heroes, slot);
      List<DeathAnalysisDomain> died =
          deaths.stream()
              .filter(d -> d.getPlayerSlot() != null && d.getPlayerSlot() == slot)
              .filter(d -> d.getDeathSeconds() != null && !Boolean.TRUE.equals(d.getReincarnated()))
              .filter(d -> d.getDeathSeconds() >= fromSeconds && d.getDeathSeconds() <= toSeconds)
              .toList();
      if (!died.isEmpty()) {
        reasons.add(deaths(hero, died));
      }
      outliers.stream()
          .filter(o -> o.getPlayerSlot() != null && o.getPlayerSlot() == slot)
          .filter(o -> o.getIncomeBreakSeconds() != null && o.getExplanation() != null)
          .filter(
              o ->
                  o.getIncomeBreakSeconds() >= fromSeconds - OUTLIER_SLACK_SECONDS
                      && o.getIncomeBreakSeconds() <= toSeconds + OUTLIER_SLACK_SECONDS)
          .findFirst()
          .map(o -> o.getExplanation().strip())
          .ifPresent(reasons::add);
      rotations.stream()
          .filter(r -> r.getPlayerSlot() != null && r.getPlayerSlot() == slot)
          .filter(r -> r.getStartSeconds() != null)
          .filter(r -> overlaps(r, fromSeconds, toSeconds))
          .map(r -> rotation(r, hero))
          .filter(Objects::nonNull)
          .findFirst()
          .ifPresent(reasons::add);
      return reasons.stream().limit(MAX_REASONS).toList();
    }

    private static String deaths(String hero, List<DeathAnalysisDomain> died) {
      String listed =
          died.stream()
              .limit(MAX_DEATHS_NAMED)
              .map(d -> "at " + GameTime.clock(d.getDeathSeconds()) + killedBy(d))
              .collect(Collectors.joining(", and "));
      int more = died.size() - MAX_DEATHS_NAMED;
      return hero
          + " died "
          + listed
          + (more > 0 ? ", and " + (more == 1 ? "once more" : more + " more times") : "");
    }

    private static boolean overlaps(TeamRotationDomain r, double from, double to) {
      double end = r.getEndSeconds() == null ? r.getStartSeconds() : r.getEndSeconds();
      return r.getStartSeconds() <= to && end >= from;
    }

    private static String rotation(TeamRotationDomain r, String hero) {
      boolean farmCut = BehaviourWording.reasons(r).contains(RotationReason.INCOME_DROP);
      boolean bad =
          RotationVerdict.WASTED.name().equals(r.getVerdict())
              || RotationVerdict.DIED.name().equals(r.getVerdict());
      if (!farmCut && !bad) {
        return null;
      }
      String trip =
          "rotation at %s%s"
              .formatted(
                  GameTime.clock(r.getStartSeconds()), bad ? " did not pay off" : " cost him farm");
      return farmCut
          ? trip
              + ": "
              + BehaviourWording.rotationSentence(RotationReason.INCOME_DROP, hero, r, false)
                  .replaceAll(" \\(experience.*\\)$", "")
          : hero + "'s " + trip;
    }

    private static String killedBy(DeathAnalysisDomain d) {
      String killer = d.getKillerName() == null ? "" : Prettify.unit(d.getKillerName());
      String reason = d.getReason() == null ? "" : DeathSection.reason(d);
      String inside =
          killer.isEmpty() ? reason : "to " + killer + (reason.isEmpty() ? "" : ", " + reason);
      return inside.isEmpty() ? "" : " (" + inside + ")";
    }
  }
}
