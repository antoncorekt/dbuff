package com.ako.dbuff.service.discord.icon;

import java.io.IOException;

/** Downloads an image; the only place the icon code touches the CDN. */
public interface IconImageSource {
  byte[] fetch(String url) throws IOException;
}
