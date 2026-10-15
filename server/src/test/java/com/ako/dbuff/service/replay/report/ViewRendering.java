package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.view.ComponentsViewRenderer;
import com.ako.dbuff.service.replay.report.view.MessageLimits;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.ReportMessage;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders a section's view as Components V2 and checks every message stays within the limits. */
final class ViewRendering {
  private static final ComponentsViewRenderer RENDERER =
      new ComponentsViewRenderer(new PlainViewRenderer());

  private ViewRendering() {}

  static void assertWithinLimits(SectionView view) {
    List<ReportMessage> messages = RENDERER.render(view);
    assertThat(messages).isNotEmpty();
    for (ReportMessage message : messages) {
      ReportMessage.Components components = (ReportMessage.Components) message;
      assertThat(components.textLength()).isLessThanOrEqualTo(MessageLimits.COMPONENTS_TEXT);
      assertThat(components.componentCount()).isLessThanOrEqualTo(MessageLimits.COMPONENTS_COUNT);
    }
  }
}
