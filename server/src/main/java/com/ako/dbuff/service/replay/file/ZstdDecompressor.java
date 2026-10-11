package com.ako.dbuff.service.replay.file;

import com.github.luben.zstd.ZstdInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Component;

@Component
public class ZstdDecompressor implements ReplayDecompressor {
  @Override
  public boolean supports(byte[] header) {
    return header.length >= 4
        && (header[0] & 0xFF) == 0x28
        && (header[1] & 0xFF) == 0xB5
        && (header[2] & 0xFF) == 0x2F
        && (header[3] & 0xFF) == 0xFD;
  }

  @Override
  public InputStream open(InputStream compressed) throws IOException {
    return new ZstdInputStream(compressed);
  }
}
