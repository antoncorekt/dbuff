package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The fights that moved the game, one line each: when, who won, what it was worth in net worth and
 * why the loser lost. A fight the swings section already explains under a swing gets a pointer to
 * it instead of the reasons again, and only while that section is switched on.
 */
@Component
@RequiredArgsConstructor
public class ImportantFightsSection implements ReplayReportSection {
  private static final int FIRST_DIRE_SLOT = 128;
  private static final int MAX_REASONS = 2;
  private static final String IN_SWING = " (reasons in the swing above)";

  private final FightExplanationReader explanationReader;
  private final ReportHeroNames heroNames;
  private final SwingSection swingSection;

  /** Lazy: the switch itself is built from every section, this one included. */
  private final ObjectProvider<SectionSwitch> sectionSwitch;

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
    List<LostFight> important = explanationReader.importantFights(input.matchId());
    if (important.isEmpty()) {
      return List.of();
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
    Set<Integer> inSwings =
        sectionSwitch.getObject().isEnabled(swingSection)
            ? swingSection.shownFightIndexes(input)
            : Set.of();
    String yours = yourTeam(input.focusSlots());
    List<String> lines = new ArrayList<>();
    lines.add("**Important fights**");
    for (LostFight fight : listed) {
      int index = fight.fight().getFightIndex();
      LostFight full = withReasons.getOrDefault(index, fight);
      lines.add(line(fight, full, heroes, yours, inSwings.contains(index)));
    }
    return lines;
  }

  private static long worth(LostFight fight) {
    Long change = fight.importance().getGoldChange();
    return change == null ? 0 : change;
  }

  private static String line(
      LostFight fight,
      LostFight withReasons,
      Map<Long, String> heroes,
      String yours,
      boolean inSwing) {
    FightImportanceDomain importance = fight.importance();
    String winner = Prettify.team(importance.getWinningTeam());
    String reasons =
        inSwing ? "" : ReasonWording.ranked(withReasons.reasons(), heroes, MAX_REASONS);
    return String.format(
        Locale.ROOT,
        "%s %s%s won the fight%s%s%s",
        GameTime.clock(fight.fight().getStartSeconds()),
        winner,
        winner.equalsIgnoreCase(yours) ? " (your team)" : "",
        worthText(importance),
        inSwing ? IN_SWING : "",
        reasons.isEmpty() ? "" : ": " + reasons);
  }

  private static String worthText(FightImportanceDomain importance) {
    Long gold = importance.getGoldChange();
    boolean worthSaying = gold != null && Math.round(gold / 100.0) > 0;
    boolean lead =
        Boolean.TRUE.equals(importance.getLeadFlip())
            && Boolean.TRUE.equals(importance.getFlipToWinner());
    return (worthSaying ? String.format(Locale.ROOT, ", %.1fk net worth swing", gold / 1000.0) : "")
        + (lead ? ", took the lead" : "");
  }

  /** The tracked players' side when they are all on one, otherwise null. */
  private static String yourTeam(Set<Long> focusSlots) {
    boolean dire = focusSlots.stream().anyMatch(s -> s >= FIRST_DIRE_SLOT);
    boolean radiant = focusSlots.stream().anyMatch(s -> s < FIRST_DIRE_SLOT);
    return dire == radiant ? null : dire ? "Dire" : "Radiant";
  }
}
