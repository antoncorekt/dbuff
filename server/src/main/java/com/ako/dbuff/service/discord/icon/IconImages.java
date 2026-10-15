package com.ako.dbuff.service.discord.icon;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** Turns a downloaded image into the square PNG that is uploaded, and hashes it. */
public final class IconImages {
  private IconImages() {}

  public static byte[] toSquarePng(byte[] source, int sizePx) throws IOException {
    BufferedImage in = ImageIO.read(new ByteArrayInputStream(source));
    if (in == null) {
      throw new IOException("Not a readable image");
    }
    BufferedImage out = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB);
    double scale = Math.min((double) sizePx / in.getWidth(), (double) sizePx / in.getHeight());
    int w = Math.max(1, (int) Math.round(in.getWidth() * scale));
    int h = Math.max(1, (int) Math.round(in.getHeight() * scale));
    var g = out.createGraphics();
    try {
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(in, (sizePx - w) / 2, (sizePx - h) / 2, w, h, null);
    } finally {
      g.dispose();
    }
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    if (!ImageIO.write(out, "png", png)) {
      throw new IOException("No PNG writer");
    }
    return png.toByteArray();
  }

  public static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
