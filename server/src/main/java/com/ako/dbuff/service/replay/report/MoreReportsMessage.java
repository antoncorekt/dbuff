package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.ai.reasons.AiReasonsSection;
import com.ako.dbuff.service.ai.reasons.ReplayReportAiProperties;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.report.view.MessageLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The closing message of an automatic report: what else can be asked for, one command per line. It
 * does not check which sections have data, as that would render them all.
 */
@Component
@RequiredArgsConstructor
public class MoreReportsMessage {
  private static final String SECTION_LINE = "`/match report section:%s` %s";
  private static final String EVERYTHING_LINE =
      "`/match report section:" + ReportCommandHandler.EVERYTHING + "` every report";
  private static final String AI_LINE = "`/match ai` top 5 reasons";

  private final SectionSwitch sectionSwitch;
  private final ObjectProvider<ReplayReportAiProperties> ai;

  private boolean aiOn() {
    ReplayReportAiProperties properties = ai.getIfAvailable();
    return properties != null && properties.isEnabled();
  }

  /** The message, or empty when the post selected nothing. */
  public Optional<String> text(
      List<ReplayReportSection> all, GameModeProfile mode, List<ReplayReportSection> selected) {
    if (selected.isEmpty()) {
      return Optional.empty();
    }
    String tail = aiOn() ? EVERYTHING_LINE + "\n" + AI_LINE : EVERYTHING_LINE;
    int room = MessageLimits.PLAIN_TEXT - tail.length();
    List<String> lines = new ArrayList<>();
    int used = 0;
    List<ReplayReportSection> offered =
        all.stream()
            .filter(s -> !AiReasonsSection.NAME.equals(s.name()))
            .filter(s -> !selected.contains(s))
            .filter(s -> !sectionSwitch.isHidden(s))
            .filter(s -> mode == null || s.supports(mode))
            .sorted(Comparator.comparingInt(ReplayReportSection::order))
            .toList();
    for (ReplayReportSection section : offered) {
      String line = SECTION_LINE.formatted(section.name(), section.summary());
      if (used + line.length() + 1 > room) {
        break;
      }
      lines.add(line);
      used += line.length() + 1;
    }
    lines.add(tail);
    return Optional.of(String.join("\n", lines));
  }
}
