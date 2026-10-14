package com.ako.dbuff.service.replay.report.view;

import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/** When a failed Components V2 send is worth retrying as plain text. */
public final class ComponentsFallback {

  private ComponentsFallback() {}

  /** Discord, or JDA before sending, found the components payload itself invalid. */
  public static boolean payloadRejected(RuntimeException e) {
    return e instanceof IllegalArgumentException
        || e instanceof IllegalStateException
        || (e instanceof ErrorResponseException r
            && r.getErrorResponse() == ErrorResponse.INVALID_FORM_BODY);
  }
}
