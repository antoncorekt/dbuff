package com.ako.dbuff.service.replay.read;

/**
 * One section of the analysis document, read from the stored tables only. A new section is a new
 * bean; its position in the document follows {@code @Order}.
 */
public interface AnalysisSectionReader {
  /** The name used in the {@code sections} query parameter and as the key in the document. */
  String name();

  /** The section as a response record or a list of them; empty lists, never null. */
  Object read(long matchId);
}
