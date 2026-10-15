package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.report.SectionViews.Verdict;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What each tracked player drafted in an Ability Draft and in which overall pick. */
@Component
@RequiredArgsConstructor
public class DraftSection implements ReplayReportSection {
  private final AbilityDraftPickRepo pickRepo;
  private final AbilityDraftProfile profile;
  private final AbilityLabels labels;

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
              .sorted(
                  Comparator.comparing(
                      AbilityDraftPickDomain::getPickOrder,
                      Comparator.nullsLast(Comparator.naturalOrder())))
              .map(p -> labels.label(p.getAbilityName()) + position(p))
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

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    List<String> lines = lines(input);
    long players = Math.max(0, lines.size() - 1);
    return SectionViews.titled(
        lines,
        new Verdict(
            "Ability picks of " + SectionViews.count(players, "tracked player"), Accent.NEUTRAL));
  }

  private static String position(AbilityDraftPickDomain pick) {
    if (pick.getPickOrder() != null) {
      return " (pick " + pick.getPickOrder() + ")";
    }
    return pick.getTeamPickIndex() == null ? "" : " (team pick " + pick.getTeamPickIndex() + ")";
  }
}
