package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.GameSpikeFactorDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.GameSpikeFactorRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.service.replay.analysis.spike.SpikeCause;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    List<String> lines = new ArrayList<>();
    lines.add("**Swings**");
    for (GameSpikeDomain spike : spikes) {
      lines.add(line(spike, factors.getOrDefault(spike.getSpikeIndex(), List.of())));
    }
    return lines;
  }

  private static String line(GameSpikeDomain spike, List<GameSpikeFactorDomain> factors) {
    String team = spike.getBenefitingTeam().equals("RADIANT") ? "Radiant" : "Dire";
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
