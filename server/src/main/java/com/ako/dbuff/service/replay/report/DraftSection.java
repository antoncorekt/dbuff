package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What each tracked player drafted in an Ability Draft and where in his team's order. */
@Component
@RequiredArgsConstructor
public class DraftSection implements ReplayReportSection {
  private final AbilityDraftPickRepo pickRepo;
  private final AbilityDraftProfile profile;

  @Override
  public boolean supports(GameModeProfile mode) {
    return mode.gameModeId() == profile.gameModeId();
  }

  @Override
  public String name() {
    return "draft";
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

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
              .filter(p -> player.getKey().equals(p.getPlayerSlot()) && p.getAbilityName() != null)
              .sorted(Comparator.comparingInt(AbilityDraftPickDomain::getPickOrder))
              .map(
                  p ->
                      Prettify.ability(p.getAbilityName())
                          + " ("
                          + ordinal(
                              p.getTeamPickIndex() == null
                                  ? p.getPickOrder()
                                  : p.getTeamPickIndex())
                          + ")")
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
