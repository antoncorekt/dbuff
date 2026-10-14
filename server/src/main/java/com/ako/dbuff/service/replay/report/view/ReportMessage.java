package com.ako.dbuff.service.replay.report.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

/** One Discord message of a report, plain text or Components V2. */
public sealed interface ReportMessage {

  /** The message with the note added, empty when it does not fit the limits. */
  Optional<ReportMessage> withNote(String note);

  record Plain(String text) implements ReportMessage {
    @Override
    public Optional<ReportMessage> withNote(String note) {
      return text.length() + note.length() <= MessageLimits.PLAIN_TEXT
          ? Optional.of(new Plain(text + note))
          : Optional.empty();
    }
  }

  /**
   * Cards of a report; {@code fallback} is their plain text, posted instead when Discord rejects
   * the components.
   */
  record Components(
      List<MessageTopLevelComponent> components,
      int textLength,
      int componentCount,
      String fallback)
      implements ReportMessage {

    public Components {
      components = List.copyOf(components);
    }

    @Override
    public Optional<ReportMessage> withNote(String note) {
      String text = note.strip();
      if (textLength + text.length() > MessageLimits.COMPONENTS_TEXT
          || componentCount + 1 > MessageLimits.COMPONENTS_COUNT) {
        return Optional.empty();
      }
      List<MessageTopLevelComponent> all = new ArrayList<>(components);
      all.add(TextDisplay.of(text));
      return Optional.of(
          new Components(
              all, textLength + text.length(), componentCount + 1, fallback + "\n" + text));
    }

    Components plus(Components next) {
      List<MessageTopLevelComponent> all = new ArrayList<>(components);
      all.addAll(next.components);
      return new Components(
          all,
          textLength + next.textLength,
          componentCount + next.componentCount,
          fallback + "\n" + next.fallback);
    }

    boolean fitsWith(Components next) {
      return textLength + next.textLength
              <= MessageLimits.COMPONENTS_TEXT - MessageLimits.NOTE_TEXT_RESERVE
          && componentCount + next.componentCount
              <= MessageLimits.COMPONENTS_COUNT - MessageLimits.NOTE_COMPONENT_RESERVE;
    }
  }
}
