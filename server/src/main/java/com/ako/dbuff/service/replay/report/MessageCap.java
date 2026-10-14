package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.view.ReportMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a report to its message cap and says how many messages were left out. A plain note that
 * does not fit the last message is dropped, as it always was; a components note, for which every
 * card keeps room, goes in a small message of its own if it still does not fit.
 */
final class MessageCap {
  private static final String NOTE = "\n_(%d more left out)_";

  private MessageCap() {}

  static List<ReportMessage> apply(List<ReportMessage> messages, int cap) {
    if (messages.size() <= cap) {
      return messages;
    }
    List<ReportMessage> kept = new ArrayList<>(messages.subList(0, cap));
    String note = NOTE.formatted(messages.size() - cap);
    switch (kept.get(cap - 1)) {
      case ReportMessage.Plain plain -> kept.set(cap - 1, plain.withNote(note).orElse(plain));
      case ReportMessage.Components cards ->
          cards
              .withNote(note)
              .ifPresentOrElse(
                  noted -> kept.set(cap - 1, noted),
                  () -> kept.add(new ReportMessage.Plain(note.strip())));
    }
    return kept;
  }
}
