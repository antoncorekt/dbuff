package com.ako.dbuff.service.ask.tool;

/** A problem with a tool call that the model can fix; the message goes back to it as is. */
public class AskToolException extends RuntimeException {

  public AskToolException(String message) {
    super(message);
  }
}
