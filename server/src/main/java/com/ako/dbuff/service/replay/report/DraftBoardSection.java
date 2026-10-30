package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The draft of the match, as the board of the mode that has one. */
@Component
@RequiredArgsConstructor
public class DraftBoardSection implements ReplayReportSection {
  private final List<DraftBoard> boards;
  private final PlainViewRenderer plain;

  @Override
  public String name() {
    return "draft-board";
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return boards.stream().anyMatch(b -> b.gameModeId() == mode.gameModeId());
  }

  @Override
  public int order() {
    return 11;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    return view(input).stream()
        .flatMap(v -> plain.lines(v).stream())
        .flatMap(line -> line.lines())
        .toList();
  }

  @Override
  public Optional<ImageAttachment> image(ReplayReportInput input) {
    return view(input).map(SectionView::image);
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    Long mode = input.gameModeId();
    return boards.stream()
        .filter(b -> mode != null && b.gameModeId() == mode)
        .findFirst()
        .flatMap(b -> b.view(input));
  }
}
