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

  /** Text, with an image posted as a file of the message when there is one. */
  record Plain(String text, ImageAttachment image) implements ReportMessage {

    public Plain(String text) {
      this(text, null);
    }

    @Override
    public Optional<ReportMessage> withNote(String note) {
      return text.length() + note.length() <= MessageLimits.PLAIN_TEXT
          ? Optional.of(new Plain(text + note, image))
          : Optional.empty();
    }
  }

  /**
   * Cards of a report; {@code fallback} is their plain text, posted instead when Discord rejects
   * the components. {@code images} are the files the components refer to.
   */
  record Components(
      List<MessageTopLevelComponent> components,
      int textLength,
      int componentCount,
      String fallback,
      List<ImageAttachment> images)
      implements ReportMessage {

    public Components {
      components = List.copyOf(components);
      images = List.copyOf(images);
    }

    public Components(
        List<MessageTopLevelComponent> components,
        int textLength,
        int componentCount,
        String fallback) {
      this(components, textLength, componentCount, fallback, List.of());
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
              all, textLength + text.length(), componentCount + 1, fallback + "\n" + text, images));
    }

    Components plus(Components next) {
      List<MessageTopLevelComponent> all = new ArrayList<>(components);
      all.addAll(next.components);
      List<ImageAttachment> allImages = new ArrayList<>(images);
      allImages.addAll(next.images);
      return new Components(
          all,
          textLength + next.textLength,
          componentCount + next.componentCount,
          fallback + "\n" + next.fallback,
          allImages);
    }

    /** Two cards with a picture each never share a message, so each picture keeps its own post. */
    boolean fitsWith(Components next) {
      return (images.isEmpty() || next.images.isEmpty())
          && textLength + next.textLength <= MessageLimits.CARD_TEXT
          && componentCount + next.componentCount <= MessageLimits.CARD_COMPONENTS;
    }
  }
}
