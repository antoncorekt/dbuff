package com.ako.dbuff.service.replay.read;

import java.util.List;

public class UnknownSectionException extends RuntimeException {
  public UnknownSectionException(List<String> unknown, List<String> valid) {
    super("Unknown section " + unknown + ", valid sections: " + valid);
  }
}
