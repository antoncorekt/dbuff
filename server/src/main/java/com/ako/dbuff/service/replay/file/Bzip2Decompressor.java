package com.ako.dbuff.service.replay.file;

import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.springframework.stereotype.Component;

@Component
public class Bzip2Decompressor implements ReplayDecompressor {
  @Override
  public boolean supports(byte[] header) {
    return header.length >= 3 && header[0] == 'B' && header[1] == 'Z' && header[2] == 'h';
  }

  @Override
  public InputStream open(InputStream compressed) throws IOException {
    return new BZip2CompressorInputStream(compressed);
  }
}
