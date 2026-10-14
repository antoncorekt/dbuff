package com.ako.dbuff.service.discord.icon;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IconImagesTest {

  @Test
  void anyImage_becomesASquarePngOfTheAskedSize() throws IOException {
    ByteArrayOutputStream wide = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(256, 144, BufferedImage.TYPE_INT_RGB), "png", wide);

    byte[] png = IconImages.toSquarePng(wide.toByteArray(), 128);

    BufferedImage out = ImageIO.read(new ByteArrayInputStream(png));
    assertThat(out.getWidth()).isEqualTo(128);
    assertThat(out.getHeight()).isEqualTo(128);
  }

  @Test
  void garbage_isRejected() {
    assertThatThrownBy(() -> IconImages.toSquarePng(new byte[] {1, 2, 3}, 128))
        .isInstanceOf(IOException.class);
  }

  @Test
  void hash_isStableHex() {
    assertThat(IconImages.sha256(new byte[] {1}))
        .hasSize(64)
        .isEqualTo(IconImages.sha256(new byte[] {1}));
  }
}
