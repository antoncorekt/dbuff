package com.ako.dbuff.service.replay.report.view;

import net.dv8tion.jda.api.utils.FileUpload;

/** A PNG posted with a report message; a card refers to it as {@code attachment://name}. */
public record ImageAttachment(String name, byte[] bytes) {

  /** A new upload each time, as a Discord upload can be read once. */
  public FileUpload upload() {
    return FileUpload.fromData(bytes, name);
  }

  String attachmentUrl() {
    return "attachment://" + name;
  }
}
