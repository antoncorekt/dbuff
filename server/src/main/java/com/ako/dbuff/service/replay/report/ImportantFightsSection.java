package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The fights that moved the game, one line each: when, who won, what it was worth in net worth and
 * why the loser lost. It repeats fights the swings section also explains, so sections stay
 * independent.
 */
@Component
@RequiredArgsConstructor
public class ImportantFightsSection implements ReplayReportSection {
  private static final int MAX_REASONS = 2;
  private static final PlainViewRenderer PLAIN = new PlainViewRenderer();

  private final FightExplanationReader explanationReader;
  private final ReportHeroNames heroNames;
  private final ReasonWording wording;
  private final ReplayReportProperties properties;

  @Override
  public String name() {
    return "important-fights";
  }

  @Override
  public int order() {
    return 65;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    return view(input).map(PLAIN::lines).orElse(List.of());
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    List<LostFight> important =
        explanationReader.importantFights(input.matchId()).stream()
            .filter(l -> Enums.find(TeamSide.class, l.importance().getWinningTeam()) != null)
            .toList();
    if (important.isEmpty()) {
      return Optional.empty();
    }
    List<LostFight> listed =
        important.stream()
            .sorted(Comparator.comparingLong(ImportantFightsSection::worth).reversed())
            .limit(properties.getMaxImportantFightLines())
            .sorted(Comparator.comparing(l -> l.fight().getStartSeconds()))
            .toList();
    Map<Integer, LostFight> withReasons =
        explanationReader.lostFights(
            input.matchId(),
            listed.stream().map(l -> l.fight().getFightIndex()).toList(),
            MAX_REASONS);
    Map<Long, String> heroes = heroNames.bySlot(input.matchId());
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    List<String> lines = new ArrayList<>();
    for (LostFight fight : listed) {
      int index = fight.fight().getFightIndex();
      LostFight full = withReasons.getOrDefault(index, fight);
      lines.add(line(fight, full, heroes, yours));
    }
    return Optional.of(
        new SectionView(
            "Important fights", verdict(listed, yours), accent(listed, yours), lines, null));
  }

  private static long wonBy(List<LostFight> fights, TeamSide side) {
    return fights.stream()
        .filter(l -> Enums.find(TeamSide.class, l.importance().getWinningTeam()) == side)
        .count();
  }

  private static String verdict(List<LostFight> fights, TeamSide yours) {
    TeamSide side = yours;
    if (side == null) {
      side =
          wonBy(fights, TeamSide.RADIANT) >= wonBy(fights, TeamSide.DIRE)
              ? TeamSide.RADIANT
              : TeamSide.DIRE;
    }
    return String.format(
        Locale.ROOT,
        "%s won %d of %d",
        Prettify.teamOf(side.name(), yours),
        wonBy(fights, side),
        fights.size());
  }

  private static Accent accent(List<LostFight> fights, TeamSide yours) {
    if (yours == null) {
      return Accent.NEUTRAL;
    }
    long won = wonBy(fights, yours);
    long lost = fights.size() - won;
    return won > lost ? Accent.WIN : won < lost ? Accent.LOSS : Accent.NEUTRAL;
  }

  /** The tenths of a thousand gold the fight moved, 0 when too small to tell, -1 when unknown. */
  private static long worth(LostFight fight) {
    Long change = fight.importance().getGoldChange();
    return change == null ? -1 : Math.round(Math.abs(change) / 100.0);
  }

  private String line(
      LostFight fight, LostFight withReasons, Map<Long, String> heroes, TeamSide yours) {
    FightImportanceDomain importance = fight.importance();
    TeamSide winner = Enums.find(TeamSide.class, importance.getWinningTeam());
    String reasons = wording.ranked(withReasons.reasons(), heroes, MAX_REASONS);
    return String.format(
        Locale.ROOT,
        "%s %s won the fight%s%s",
        GameTime.clock(fight.fight().getStartSeconds()),
        Prettify.teamOf(winner.name(), yours),
        worthText(fight),
        reasons.isEmpty() ? "" : ": " + reasons);
  }

  private static String worthText(LostFight fight) {
    FightImportanceDomain importance = fight.importance();
    long tenths = worth(fight);
    boolean lead =
        Boolean.TRUE.equals(importance.getLeadFlip())
            && Boolean.TRUE.equals(importance.getFlipToWinner());
    return (tenths > 0 ? String.format(Locale.ROOT, ", %.1fk net worth swing", tenths / 10.0) : "")
        + (lead ? ", took the lead" : "");
  }
}
