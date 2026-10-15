package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MidActivityDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.MidActivityRepo;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What the mid hero of each team did after level 6 and in the mid game, one sentence per team with
 * the verdict first: active or passive, with the numbers behind it and the team's result. The
 * tracked team's mid hero comes first. It reads {@code mid_activity} only, so it is independent of
 * other sections, and works for every game mode.
 */
@Component
@RequiredArgsConstructor
public class MidActivitySection implements ReplayReportSection {
  private final MidActivityRepo midRepo;
  private final ReportHeroNames heroNames;
  private final ReplayReportProperties properties;

  @Override
  public String name() {
    return "mid-activity";
  }

  @Override
  public int order() {
    return 73;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    return sentences(input);
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    List<String> sentences = sentences(input);
    if (sentences.isEmpty()) {
      return Optional.empty();
    }
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    return Optional.of(
        new SectionView(
            "Mid hero in the mid game",
            sentences.get(0),
            SectionViews.forTeam(winner(input.match()), yours),
            sentences.subList(1, sentences.size()),
            null));
  }

  private List<String> sentences(ReplayReportInput input) {
    List<MidActivityDomain> rows = rows(input.matchId());
    if (rows.isEmpty()) {
      return List.of();
    }
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    TeamSide winner = winner(input.match());
    Map<Long, String> heroes = heroNames.iconBySlot(input.matchId());
    int max = Math.max(LineText.ELLIPSIS.length() + 1, properties.getMaxBehaviourLineLength());
    return rows.stream()
        .sorted(Comparator.comparing((MidActivityDomain r) -> side(r) != yours))
        .map(r -> LineText.cut(line(r, heroes, yours, winner), max))
        .toList();
  }

  private List<MidActivityDomain> rows(long matchId) {
    List<MidActivityDomain> stored = midRepo.findByMatchId(matchId);
    return stored == null
        ? List.of()
        : stored.stream()
            .filter(Objects::nonNull)
            .filter(r -> r.getPlayerSlot() != null && side(r) != null)
            .sorted(Comparator.comparing(MidActivityDomain::getPlayerSlot))
            .toList();
  }

  private static String line(
      MidActivityDomain row, Map<Long, String> heroes, TeamSide yours, TeamSide winner) {
    TeamSide side = side(row);
    String who =
        "%s (%s)"
            .formatted(
                BehaviourWording.hero(heroes, row.getPlayerSlot()),
                BehaviourWording.teamTag(side.name(), yours));
    String text = MidActivityWording.sentence(row, who) + ".";
    return winner == null
        ? text
        : "%s %s %s.".formatted(text, Prettify.team(side.name()), side == winner ? "won" : "lost");
  }

  private static TeamSide side(MidActivityDomain row) {
    return row.getPlayerSlot() == null ? null : TeamSide.ofSlot(row.getPlayerSlot());
  }

  private static TeamSide winner(MatchDomain match) {
    if (match == null || match.getRadiantWin() == null) {
      return null;
    }
    return match.getRadiantWin() ? TeamSide.RADIANT : TeamSide.DIRE;
  }
}
