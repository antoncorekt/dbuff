package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Per focus player the most useful lines about the skills he drafted: how the hero, skills and
 * pairs did in the last months, whether the build fit his role, and a better skill the enemy took
 * later. Everything comes from stored rows; the words are those of the analyzers' own wording
 * classes. Ability Draft only, and nothing is posted when no line can be said.
 */
@Component
@RequiredArgsConstructor
public class SkillPicksSection implements ReplayReportSection, SectionFindings {
  private final SkillPickFindings findings;

  @Override
  public String name() {
    return "skill-picks";
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return mode.gameModeId() == AbilityDraftProfile.MODE_ID;
  }

  @Override
  public int order() {
    return 13;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<Finding> findings(ReplayReportInput input) {
    return findings.find(input);
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    return findings(input).stream().map(Finding::text).toList();
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    List<String> lines = lines(input);
    if (lines.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new SectionView("Skill picks", null, Accent.INFO, lines, null));
  }
}
