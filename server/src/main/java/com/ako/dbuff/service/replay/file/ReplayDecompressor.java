package com.ako.dbuff.service.replay.file;

import java.io.IOException;
import java.io.InputStream;

/** One replay compression format. A new format is a new bean. */
public interface ReplayDecompressor {
  /** Whether the file starting with these bytes is in this format. */
  boolean supports(byte[] header);

  InputStream open(InputStream compressed) throws IOException;
}
