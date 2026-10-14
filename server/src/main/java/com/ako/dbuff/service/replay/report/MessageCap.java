package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.view.ReportMessage;
import java.util.ArrayList;
import java.util.List;

/** Keeps a report to its message cap and says how many messages were left out. */
final class MessageCap {
  private static final String NOTE = "\n_(%d more left out)_";

  private MessageCap() {}

  static List<ReportMessage> apply(List<ReportMessage> messages, int cap) {
    if (messages.size() <= cap) {
      return messages;
    }
    List<ReportMessage> kept = new ArrayList<>(messages.subList(0, cap));
    kept.set(cap - 1, kept.get(cap - 1).withNote(NOTE.formatted(messages.size() - cap)));
    return kept;
  }
}
