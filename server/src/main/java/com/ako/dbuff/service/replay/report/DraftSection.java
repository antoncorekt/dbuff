package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What each tracked player drafted in an Ability Draft and where in the order he took it. */
@Component
@RequiredArgsConstructor
public class DraftSection implements ReplayReportSection {
  private final AbilityDraftPickRepo pickRepo;

  @Override
  public int order() {
    return 10;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<AbilityDraftPickDomain> picks = pickRepo.findByMatchId(input.matchId());
    List<String> lines = new ArrayList<>();
    for (Map.Entry<Long, String> player : input.namesBySlot().entrySet()) {
      String chosen =
          picks.stream()
              .filter(p -> player.getKey().equals(p.getPlayerSlot()))
              .sorted(Comparator.comparingInt(AbilityDraftPickDomain::getPickOrder))
              .map(
                  p ->
                      Prettify.ability(p.getAbilityName()) + " (" + ordinal(p.getPickOrder()) + ")")
              .collect(Collectors.joining(", "));
      if (!chosen.isEmpty()) {
        lines.add(player.getValue() + " drafted " + chosen);
      }
    }
    if (lines.isEmpty()) {
      return List.of();
    }
    lines.addFirst("**Draft**");
    return lines;
  }

  private static String ordinal(int n) {
    int lastTwo = n % 100;
    if (lastTwo >= 11 && lastTwo <= 13) {
      return n + "th";
    }
    return n
        + switch (n % 10) {
          case 1 -> "st";
          case 2 -> "nd";
          case 3 -> "rd";
          default -> "th";
        };
  }
}
