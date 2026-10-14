package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.view.MessageLimits;
import com.ako.dbuff.service.replay.report.view.ReportMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageCapTest {

  @Test
  void aPlainNoteIsAddedWhenItFits() {
    List<ReportMessage> kept =
        MessageCap.apply(List.of(new ReportMessage.Plain("a"), new ReportMessage.Plain("b")), 1);

    assertThat(kept).containsExactly(new ReportMessage.Plain("a\n_(1 more left out)_"));
  }

  @Test
  void aPlainNoteThatDoesNotFitIsDroppedAndTheCountStaysWithinTheCap() {
    ReportMessage.Plain full = new ReportMessage.Plain("a".repeat(MessageLimits.PLAIN_TEXT - 3));

    List<ReportMessage> kept = MessageCap.apply(List.of(full, new ReportMessage.Plain("b")), 1);

    assertThat(kept).containsExactly(full);
  }

  @Test
  void aComponentsNoteThatCannotFitGoesInASmallMessageOfItsOwn() {
    ReportMessage.Components full =
        new ReportMessage.Components(List.of(), MessageLimits.COMPONENTS_TEXT, 1, "");

    List<ReportMessage> kept = MessageCap.apply(List.of(full, full), 1);

    assertThat(kept).containsExactly(full, new ReportMessage.Plain("_(1 more left out)_"));
  }
}
