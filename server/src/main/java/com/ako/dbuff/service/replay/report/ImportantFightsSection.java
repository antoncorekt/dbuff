package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.GameSpikeFactorDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.GameSpikeFactorRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.item.HeroInputs;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.spike.SpikeCause;
import com.ako.dbuff.service.replay.report.SectionViews.Verdict;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The fights and gold swings that moved the game in game-time order: for a fight when, who won,
 * what it was worth in net worth and why the loser lost; for a swing its window, the gold moved,
 * its cause and the key item behind it.
 */
@Component
@RequiredArgsConstructor
public class ImportantFightsSection implements ReplayReportSection {
  private static final int MAX_REASONS = 2;
  private static final int MAX_CAUSES = 2;
  private static final double MIN_SHARE = 10;
  private static final double CLEAR_MARGIN = 5;

  private static final Map<String, String> SOURCES =
      Map.of(
          "HERO_KILLS", "kills",
          "FARM", "farming",
          "BUILDINGS", "buildings",
          "ROSHAN", "Roshan",
          "BOUNTY", "bounties",
          "COMEBACK", "comeback gold",
          "BUYBACKS", "buybacks");

  private final GameSpikeRepo spikeRepo;
  private final GameSpikeFactorRepo factorRepo;
  private final FightExplanationReader explanationReader;
  private final ReportHeroNames heroNames;
  private final ReasonWording wording;
  private final AbilityLabels labels;
  private final ReplayReportProperties properties;
  private final PlainViewRenderer plain;

  @Override
  public String name() {
    return "important-fights";
  }

  @Override
  public int order() {
    return 40;
  }

  @Override
  public String summary() {
    return "the fights that decided the game";
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    return view(input).map(plain::lines).orElse(List.of());
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    List<LostFight> important =
        explanationReader.importantFights(input.matchId()).stream()
            .filter(l -> Enums.find(TeamSide.class, l.importance().getWinningTeam()) != null)
            .toList();
    List<GameSpikeDomain> swings = selectedSwings(input);
    if (important.isEmpty() && swings.isEmpty()) {
      return Optional.empty();
    }
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    Map<Long, String> heroes = heroNames.iconBySlot(input.matchId());
    List<Timed> timed = new ArrayList<>(fightLines(input, important, heroes, yours));
    Map<Integer, List<GameSpikeFactorDomain>> factors = factorsOf(input.matchId());
    GameSpikeDomain biggest =
        important.isEmpty()
            ? swings.stream()
                .max(Comparator.comparingLong(GameSpikeDomain::getDeltaGold))
                .orElseThrow()
            : null;
    for (GameSpikeDomain spike : swings) {
      if (spike != biggest) {
        List<GameSpikeFactorDomain> found = factors.getOrDefault(spike.getSpikeIndex(), List.of());
        timed.add(
            new Timed(
                spike.getStartSeconds(),
                swingLine(spike, found, yours) + keyItemClause(spike, found, heroes)));
      }
    }
    List<String> lines =
        timed.stream().sorted(Comparator.comparingDouble(Timed::at)).map(Timed::text).toList();
    Verdict outcome =
        biggest == null
            ? outcome(important, yours)
            : biggestSwing(
                biggest, factors.getOrDefault(biggest.getSpikeIndex(), List.of()), heroes, yours);
    return Optional.of(
        new SectionView(
            "Important fights and swings", outcome.text(), outcome.accent(), lines, null));
  }

  private List<Timed> fightLines(
      ReplayReportInput input,
      List<LostFight> important,
      Map<Long, String> heroes,
      TeamSide yours) {
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
    List<Timed> lines = new ArrayList<>();
    for (LostFight fight : listed) {
      int index = fight.fight().getFightIndex();
      LostFight full = withReasons.getOrDefault(index, fight);
      lines.add(new Timed(fight.fight().getStartSeconds(), line(fight, full, heroes, yours)));
    }
    return lines;
  }

  private Verdict biggestSwing(
      GameSpikeDomain biggest,
      List<GameSpikeFactorDomain> found,
      Map<Long, String> heroes,
      TeamSide yours) {
    return new Verdict(
        "Biggest swing: "
            + swingLine(biggest, found, yours)
            + keyItemClause(biggest, found, heroes),
        SectionViews.forTeam(Enums.find(TeamSide.class, biggest.getBenefitingTeam()), yours));
  }

  private List<GameSpikeDomain> selectedSwings(ReplayReportInput input) {
    return spikeRepo.findByMatchId(input.matchId()).stream()
        .filter(s -> !SpikeCause.OTHER.name().equals(s.getPrimaryCause()))
        .sorted(Comparator.comparingLong(GameSpikeDomain::getDeltaGold).reversed())
        .limit(input.maxFindings())
        .toList();
  }

  private Map<Integer, List<GameSpikeFactorDomain>> factorsOf(long matchId) {
    return factorRepo.findByMatchId(matchId).stream()
        .collect(Collectors.groupingBy(GameSpikeFactorDomain::getSpikeIndex));
  }

  private record Timed(double at, String text) {}

  private static long wonBy(List<LostFight> fights, TeamSide side) {
    return fights.stream()
        .filter(l -> Enums.find(TeamSide.class, l.importance().getWinningTeam()) == side)
        .count();
  }

  /** Who won more of all the important fights of the match, not only the listed ones. */
  private static Verdict outcome(List<LostFight> fights, TeamSide yours) {
    return SectionViews.contest(
        yours,
        wonBy(fights, TeamSide.RADIANT),
        wonBy(fights, TeamSide.DIRE),
        fights.size(),
        "important fights");
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
    String reasons =
        wording.ranked(
            withReasons.reasons(),
            heroes,
            MAX_REASONS,
            new ReasonWording.Perspective(winner.opponent(), yours));
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

  private String keyItemClause(
      GameSpikeDomain spike, List<GameSpikeFactorDomain> factors, Map<Long, String> heroes) {
    return factors.stream()
        .filter(f -> "ITEM".equals(f.getFactorType()) && f.getRef() != null)
        .findFirst()
        .map(
            f -> {
              Optional<Double> seconds = completedAt(f.getDetail());
              if (seconds.isEmpty()) {
                return "";
              }
              String hero = f.getPlayerSlot() == null ? null : heroes.get(f.getPlayerSlot());
              return String.format(
                  Locale.ROOT,
                  "; it came after %s finished %s%s at %s",
                  Prettify.team(spike.getBenefitingTeam()),
                  itemName(f.getRef()),
                  hero == null ? "" : " (" + hero + ")",
                  GameTime.clock(seconds.get()));
            })
        .orElse("");
  }

  private String itemName(String key) {
    String replayName = HeroInputs.replayName(key);
    String label = labels.label(replayName);
    String name =
        label == null || label.isBlank() || label.equals(replayName) || label.equals(key)
            ? Prettify.ability(replayName)
            : label;
    return labels.icon(replayName) + name;
  }

  private static Optional<Double> completedAt(String detail) {
    try {
      Double seconds = detail == null ? null : Double.valueOf(detail);
      return seconds == null || !(seconds >= 0) || seconds.isInfinite()
          ? Optional.empty()
          : Optional.of(seconds);
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static String swingLine(
      GameSpikeDomain spike, List<GameSpikeFactorDomain> factors, TeamSide yours) {
    String team = Prettify.teamOf(spike.getBenefitingTeam(), yours);
    List<GameSpikeFactorDomain> sources =
        factors.stream()
            .filter(
                f ->
                    "SOURCE".equals(f.getFactorType())
                        && f.getSharePercent() != null
                        && f.getSharePercent() >= MIN_SHARE)
            .sorted(Comparator.comparingDouble(GameSpikeFactorDomain::getSharePercent).reversed())
            .limit(MAX_CAUSES)
            .toList();
    String because =
        sources.stream()
            .map(f -> sourceName(f) + " " + Math.round(f.getSharePercent()) + "%")
            .collect(Collectors.joining(", "));
    boolean tie =
        sources.size() == MAX_CAUSES
            && sources.get(0).getSharePercent() - sources.get(1).getSharePercent() < CLEAR_MARGIN;
    String cause = SwingCauses.phrase(spike.getPrimaryCause());
    String from =
        cause.isEmpty()
            ? ""
            : tie
                ? ", from " + sourceName(sources.get(0)) + " and " + sourceName(sources.get(1))
                : ", mostly from " + cause;
    return String.format(
        Locale.ROOT,
        "%s-%s: swung %.1fk gold toward %s%s%s%s",
        GameTime.clock(spike.getStartSeconds()),
        GameTime.clock(spike.getEndSeconds()),
        spike.getDeltaGold() / 1000.0,
        team,
        Boolean.TRUE.equals(spike.getDirectionChange()) ? " and took the lead" : "",
        from,
        because.isEmpty() ? "" : " (" + because + ")");
  }

  private static String sourceName(GameSpikeFactorDomain factor) {
    return SOURCES.getOrDefault(factor.getRef(), factor.getRef());
  }
}
