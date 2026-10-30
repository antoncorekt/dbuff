package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.GameSpikeFactorDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.GameSpikeFactorRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.SwingExplanation;
import com.ako.dbuff.service.replay.analysis.item.HeroInputs;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.spike.SpikeCause;
import com.ako.dbuff.service.replay.report.SectionViews.Verdict;
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

/** The biggest swings of the game in net worth, each with what most of it is put down to. */
@Component
@RequiredArgsConstructor
public class SwingSection implements ReplayReportSection {
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

  @Override
  public String name() {
    return "swing";
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

  @Override
  public int order() {
    return 60;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    return render(input, selectedSwings(input), null);
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    List<GameSpikeDomain> spikes = selectedSwings(input);
    if (render(input, spikes, null).isEmpty()) {
      return Optional.empty();
    }
    GameSpikeDomain biggest =
        spikes.stream().max(Comparator.comparingLong(GameSpikeDomain::getDeltaGold)).orElseThrow();
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    String verdict =
        "Biggest swing: "
            + line(
                biggest,
                factorsOf(input.matchId()).getOrDefault(biggest.getSpikeIndex(), List.of()),
                yours);
    return SectionViews.titled(
        render(input, spikes, biggest),
        new Verdict(
            verdict,
            SectionViews.forTeam(Enums.find(TeamSide.class, biggest.getBenefitingTeam()), yours)));
  }

  private Map<Integer, List<GameSpikeFactorDomain>> factorsOf(long matchId) {
    return factorRepo.findByMatchId(matchId).stream()
        .collect(Collectors.groupingBy(GameSpikeFactorDomain::getSpikeIndex));
  }

  private List<String> render(
      ReplayReportInput input, List<GameSpikeDomain> spikes, GameSpikeDomain verdictSwing) {
    if (spikes.isEmpty()) {
      return List.of();
    }
    Map<Integer, List<GameSpikeFactorDomain>> factors = factorsOf(input.matchId());
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    Map<Integer, SwingExplanation> lostBySwing = lostFights(input.matchId());
    Map<Long, String> heroes = heroNames.iconBySlot(input.matchId());
    List<String> lines = new ArrayList<>();
    lines.add("**Swings**");
    for (GameSpikeDomain spike : spikes) {
      List<GameSpikeFactorDomain> found = factors.getOrDefault(spike.getSpikeIndex(), List.of());
      if (spike != verdictSwing) {
        lines.add(line(spike, found, yours) + keyItemClause(spike, found, heroes));
      }
      SwingExplanation told = lostBySwing.get(spike.getSpikeIndex());
      (told == null ? List.<LostFight>of() : told.fights())
          .stream()
              .sorted(
                  Comparator.comparing(
                      lost -> lost.fight().getStartSeconds(),
                      Comparator.nullsLast(Comparator.naturalOrder())))
              .map(lost -> lostLine(lost, heroes, told.noImportantFight()))
              .flatMap(Optional::stream)
              .forEach(lines::add);
    }
    return lines;
  }

  private List<GameSpikeDomain> selectedSwings(ReplayReportInput input) {
    return spikeRepo.findByMatchId(input.matchId()).stream()
        .filter(s -> !SpikeCause.OTHER.name().equals(s.getPrimaryCause()))
        .sorted(Comparator.comparingLong(GameSpikeDomain::getDeltaGold).reversed())
        .limit(input.maxFindings())
        .sorted(Comparator.comparingInt(GameSpikeDomain::getStartSeconds))
        .toList();
  }

  private Map<Integer, SwingExplanation> lostFights(long matchId) {
    return explanationReader
        .read(matchId, properties.getMaxReasonsPerFight(), properties.getMaxLostFightsPerSwing())
        .swings()
        .stream()
        .collect(Collectors.toMap(s -> s.swing().getSpikeIndex(), s -> s, (a, b) -> a));
  }

  private Optional<String> lostLine(
      LostFight lost, Map<Long, String> heroes, boolean noImportantFight) {
    TeamSide loser = Enums.find(TeamSide.class, lost.explanation().getLosingTeam());
    Double start = lost.fight().getStartSeconds();
    if (loser == null || start == null) {
      return Optional.empty();
    }
    String reasons = wording.ranked(lost.reasons(), heroes, Integer.MAX_VALUE);
    if (reasons.isEmpty()) {
      return Optional.empty();
    }
    Long gold = lost.explanation().getGoldLost();
    boolean worthSaying = gold != null && Math.round(gold / 100.0) > 0;
    return Optional.of(
        String.format(
            Locale.ROOT,
            "%s lost %sthe fight at %s%s: %s",
            Prettify.team(loser.name()),
            worthSaying ? String.format(Locale.ROOT, "%.1fk from ", gold / 1000.0) : "",
            GameTime.clock(start),
            noImportantFight ? " (no single important fight)" : "",
            reasons));
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

  private static String line(
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
