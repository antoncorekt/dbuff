package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.GameSpikeFactorDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.GameSpikeFactorRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.SwingExplanation;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.spike.SpikeCause;
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

  private static final Map<String, String> SOURCES =
      Map.of(
          "HERO_KILLS", "kills",
          "FARM", "farming",
          "BUILDINGS", "buildings",
          "ROSHAN", "Roshan",
          "BOUNTY", "bounties",
          "COMEBACK", "comeback gold",
          "BUYBACKS", "buybacks");

  private static final Map<SpikeCause, String> CAUSES =
      Map.of(
          SpikeCause.FIGHT_WIN, "a won fight",
          SpikeCause.PICKOFF, "picked-off heroes",
          SpikeCause.OBJECTIVE, "buildings",
          SpikeCause.ROSHAN_AEGIS, "Roshan",
          SpikeCause.BUYBACK_SWING, "buybacks",
          SpikeCause.CARRY_FARM, "farming",
          SpikeCause.COMEBACK_GOLD, "comeback gold");

  private final GameSpikeRepo spikeRepo;
  private final GameSpikeFactorRepo factorRepo;
  private final FightExplanationReader explanationReader;
  private final ReportHeroNames heroNames;
  private final ReplayReportProperties properties;

  @Override
  public String name() {
    return "swing";
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
    List<GameSpikeDomain> spikes =
        spikeRepo.findByMatchId(input.matchId()).stream()
            .filter(s -> !SpikeCause.OTHER.name().equals(s.getPrimaryCause()))
            .sorted(Comparator.comparingLong(GameSpikeDomain::getDeltaGold).reversed())
            .limit(input.maxFindings())
            .sorted(Comparator.comparingInt(GameSpikeDomain::getStartSeconds))
            .toList();
    if (spikes.isEmpty()) {
      return List.of();
    }
    Map<Integer, List<GameSpikeFactorDomain>> factors =
        factorRepo.findByMatchId(input.matchId()).stream()
            .collect(Collectors.groupingBy(GameSpikeFactorDomain::getSpikeIndex));
    Map<Integer, SwingExplanation> lostBySwing = lostFights(input.matchId());
    Map<Long, String> heroes = heroNames.bySlot(input.matchId());
    List<String> lines = new ArrayList<>();
    lines.add("**Swings**");
    for (GameSpikeDomain spike : spikes) {
      lines.add(line(spike, factors.getOrDefault(spike.getSpikeIndex(), List.of())));
      SwingExplanation told = lostBySwing.get(spike.getSpikeIndex());
      (told == null ? List.<LostFight>of() : told.fights())
          .stream()
              .map(lost -> lostLine(lost, heroes, told.noImportantFight()))
              .flatMap(Optional::stream)
              .forEach(lines::add);
    }
    return lines;
  }

  private Map<Integer, SwingExplanation> lostFights(long matchId) {
    return explanationReader
        .read(matchId, properties.getMaxReasonsPerFight(), properties.getMaxLostFightsPerSwing())
        .swings()
        .stream()
        .collect(Collectors.toMap(s -> s.swing().getSpikeIndex(), s -> s, (a, b) -> a));
  }

  private static Optional<String> lostLine(
      LostFight lost, Map<Long, String> heroes, boolean noImportantFight) {
    TeamSide loser = Enums.find(TeamSide.class, lost.explanation().getLosingTeam());
    Double start = lost.fight().getStartSeconds();
    if (loser == null || start == null) {
      return Optional.empty();
    }
    String reasons =
        lost.reasons().stream()
            .filter(FightExplanationReader.RankedReason::ranked)
            .map(r -> r.reason())
            .map(r -> phrase(r, heroes))
            .flatMap(Optional::stream)
            .collect(Collectors.joining(", "));
    if (reasons.isEmpty()) {
      return Optional.empty();
    }
    Long gold = lost.explanation().getGoldLost();
    boolean worthSaying = gold != null && Math.round(gold / 100.0) > 0;
    return Optional.of(
        String.format(
            Locale.ROOT,
            "%s lost %sthe fight at %s%s: %s",
            teamName(loser.name()),
            worthSaying ? String.format(Locale.ROOT, "%.1fk from ", gold / 1000.0) : "",
            GameTime.clock(start),
            noImportantFight ? " (no single important fight)" : "",
            reasons));
  }

  private static Optional<String> phrase(FightReasonDomain reason, Map<Long, String> heroes) {
    String hero = reason.getSubjectSlot() == null ? null : heroes.get(reason.getSubjectSlot());
    if (hero == null && !"TEAM".equals(reason.getScope())) {
      return Optional.empty();
    }
    return Optional.of(ReasonWording.phrase(reason, hero));
  }

  private static String teamName(String team) {
    return "RADIANT".equals(team) ? "Radiant" : "Dire";
  }

  private static String line(GameSpikeDomain spike, List<GameSpikeFactorDomain> factors) {
    String team = teamName(spike.getBenefitingTeam());
    String because =
        factors.stream()
            .filter(f -> f.getFactorType().equals("SOURCE") && f.getSharePercent() >= MIN_SHARE)
            .sorted(Comparator.comparingDouble(GameSpikeFactorDomain::getSharePercent).reversed())
            .limit(MAX_CAUSES)
            .map(
                f ->
                    SOURCES.getOrDefault(f.getRef(), f.getRef())
                        + " "
                        + Math.round(f.getSharePercent())
                        + "%")
            .collect(Collectors.joining(", "));
    String cause = causeText(spike.getPrimaryCause());
    return String.format(
        Locale.ROOT,
        "%s-%s: %s gained %.1fk net worth%s%s%s",
        GameTime.clock(spike.getStartSeconds()),
        GameTime.clock(spike.getEndSeconds()),
        team,
        spike.getDeltaGold() / 1000.0,
        Boolean.TRUE.equals(spike.getDirectionChange()) ? " and took the lead" : "",
        cause.isEmpty() ? "" : ", mostly from " + cause,
        because.isEmpty() ? "" : " (" + because + ")");
  }

  private static String causeText(String stored) {
    try {
      return CAUSES.getOrDefault(SpikeCause.valueOf(stored), "");
    } catch (IllegalArgumentException | NullPointerException e) {
      return "";
    }
  }
}
