package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import com.ako.dbuff.service.replay.report.ReplayReportSection;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The top reasons the tracked team won or lost, written by a model from the match facts and shown
 * as text our code renders from the validated answer. Off unless the AI switch and the section's
 * own switch are on; says nothing when there is no answer.
 */
@Component
@RequiredArgsConstructor
public class AiReasonsSection implements ReplayReportSection {
  public static final String NAME = "ai-reasons";

  private final AiReasonsService service;
  private final PlainViewRenderer plain;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public int order() {
    return 80;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    return view(input).map(v -> plain.lines(v, true)).orElse(List.of());
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    ReasonsResult result = service.reasons(input);
    if (!result.hasReasons()) {
      return Optional.empty();
    }
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    List<String> bullets = new ArrayList<>();
    for (Reason reason : result.reasons()) {
      bullets.add(bullet(reason));
    }
    int count = result.reasons().size();
    String footnote =
        (count < ReplayReportAiProperties.MAX_REASONS
                ? "Only " + count + " reasons had enough supporting data. "
                : "")
            + "Written by a language model from the match facts; it can be wrong.";
    return Optional.of(
        new SectionView(
            "Top " + ReplayReportAiProperties.MAX_REASONS + " reasons",
            verdict(input.match(), yours),
            accent(input.match(), yours),
            bullets,
            footnote));
  }

  private static String bullet(Reason reason) {
    String basis =
        reason.evidence().stream()
            .map(FactKind::ofId)
            .flatMap(Optional::stream)
            .map(FactKind::label)
            .distinct()
            .collect(Collectors.joining(", "));
    return reason.rank()
        + ". "
        + reason.title()
        + ": "
        + reason.explanation()
        + (basis.isEmpty() ? "" : " (based on: " + basis + ")");
  }

  private static TeamSide winner(MatchDomain match) {
    if (match == null || match.getRadiantWin() == null) {
      return null;
    }
    return match.getRadiantWin() ? TeamSide.RADIANT : TeamSide.DIRE;
  }

  private static String verdict(MatchDomain match, TeamSide yours) {
    TeamSide winner = winner(match);
    return winner == null
        ? null
        : TeamLabels.name(winner) + " won; your team " + (winner == yours ? "won" : "lost");
  }

  private static Accent accent(MatchDomain match, TeamSide yours) {
    TeamSide winner = winner(match);
    if (winner == null) {
      return Accent.NEUTRAL;
    }
    return winner == yours ? Accent.WIN : Accent.LOSS;
  }
}
