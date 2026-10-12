package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.PlayerPhaseSummaryDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.PlayerPhaseSummaryRepo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Where each tracked player's time went in the laning, mid and late game. */
@Component
@RequiredArgsConstructor
public class MovementSection implements ReplayReportSection {
  private static final int TOP_ACTIVITIES = 2;
  private static final String[] PHASES = {"LANE", "MID", "LATE"};

  private final PlayerPhaseSummaryRepo summaryRepo;

  @Override
  public int order() {
    return 30;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.FULL;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<PlayerPhaseSummaryDomain> rows = summaryRepo.findByMatchId(input.matchId());
    List<String> lines = new ArrayList<>();
    for (Map.Entry<Long, String> player : input.namesBySlot().entrySet()) {
      for (String phase : PHASES) {
        rows.stream()
            .filter(r -> r.getPlayerSlot().equals(player.getKey()) && r.getPhase().equals(phase))
            .findFirst()
            .map(r -> line(player.getValue(), r))
            .ifPresent(lines::add);
      }
    }
    if (lines.isEmpty()) {
      return List.of();
    }
    lines.addFirst("**Movement**");
    return lines;
  }

  private static String line(String name, PlayerPhaseSummaryDomain r) {
    int length = r.getEndSeconds() - r.getStartSeconds();
    Map<String, Integer> seconds = new LinkedHashMap<>();
    seconds.put("dead", r.getDeadSeconds());
    seconds.put("teleporting", r.getTeleportingSeconds());
    seconds.put("in the base", r.getInBaseSeconds());
    seconds.put("farming the jungle", r.getJungleFarmSeconds());
    seconds.put("farming a lane", r.getLaneFarmSeconds());
    seconds.put("pushing", r.getPushingSeconds());
    seconds.put("travelling", r.getTravelingSeconds());
    String where =
        seconds.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(TOP_ACTIVITIES)
            .map(e -> Math.round(100.0 * e.getValue() / length) + "% " + e.getKey())
            .collect(Collectors.joining(", "));
    String fights =
        r.getFightsAvailable() == 0
            ? ""
            : ", joined %d of %d fights".formatted(r.getFightsJoined(), r.getFightsAvailable());
    String income =
        r.getGoldPerMinute() == null
            ? ""
            : String.format(Locale.ROOT, ", %.0f gold a minute", r.getGoldPerMinute());
    return "%s %s-%s min: %s%s%s"
        .formatted(
            name,
            r.getStartSeconds() / 60,
            r.getEndSeconds() / 60,
            where.isEmpty() ? "no data" : where,
            fights,
            income);
  }
}
