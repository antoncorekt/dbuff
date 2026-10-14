package com.ako.dbuff.service.replay.report.view;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

/** One Discord message of a report, plain text or Components V2. */
public sealed interface ReportMessage {

  /** The message with the note added when it still fits the limits, else unchanged. */
  ReportMessage withNote(String note);

  record Plain(String text) implements ReportMessage {
    @Override
    public ReportMessage withNote(String note) {
      return text.length() + note.length() <= MessageLimits.PLAIN_TEXT
          ? new Plain(text + note)
          : this;
    }
  }

  record Components(List<MessageTopLevelComponent> components, int textLength, int componentCount)
      implements ReportMessage {

    public Components {
      components = List.copyOf(components);
    }

    @Override
    public ReportMessage withNote(String note) {
      String text = note.strip();
      if (textLength + text.length() > MessageLimits.COMPONENTS_TEXT
          || componentCount + 1 > MessageLimits.COMPONENTS_COUNT) {
        return this;
      }
      List<MessageTopLevelComponent> all = new ArrayList<>(components);
      all.add(TextDisplay.of(text));
      return new Components(all, textLength + text.length(), componentCount + 1);
    }
  }
}
