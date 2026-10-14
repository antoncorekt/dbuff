package com.ako.dbuff.service.replay.read;

import java.util.List;

public class UnknownSectionException extends InvalidSectionsException {
  public UnknownSectionException(List<String> unknown, List<String> valid) {
    super("Unknown section " + unknown + ", valid sections: " + valid);
  }
}
