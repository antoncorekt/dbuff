package com.ako.dbuff.service.replay.report.chart;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.discord.icon.IconImageSource;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SkillIconImagesTest {
  @TempDir Path dir;

  private final List<String> fetched = new ArrayList<>();
  private boolean failing;
  private SkillIconImages images;

  private static byte[] png(int size) throws IOException {
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  @BeforeEach
  void setUp() throws IOException {
    IconImageSource source =
        url -> {
          fetched.add(url);
          if (failing) {
            throw new IOException("down");
          }
          return png(64);
        };
    Map<String, AbilityConstant> abilities =
        Map.of(
            "axe_culling_blade", AbilityConstant.builder().img("/img/culling.png").build(),
            "axe_counter_helix", AbilityConstant.builder().img("/img/helix.png").build());
    HeroConstant phoenix = new HeroConstant("110", "npc_dota_hero_phoenix", "Phoenix");
    phoenix.setImg("/img/phoenix.png");
    HeroKits kits =
        new HeroKits() {
          @Override
          public Optional<HeroConstant> hero(long heroId) {
            return heroId == 110 ? Optional.of(phoenix) : Optional.empty();
          }

          @Override
          public Optional<AbilityConstant> ability(String name) {
            return Optional.ofNullable(abilities.get(name));
          }
        };
    images = new SkillIconImages(dir, "https://cdn.test", source, kits);
  }

  @Test
  void theSpringConstructorNeedsNoDiscordBeans() {
    HeroKits none =
        new HeroKits() {
          @Override
          public Optional<HeroConstant> hero(long heroId) {
            return Optional.empty();
          }

          @Override
          public Optional<AbilityConstant> ability(String name) {
            return Optional.empty();
          }
        };
    try (var context =
        new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
      context.registerBean(HeroKits.class, () -> none);
      context.register(SkillIconImages.class);
      context.refresh();

      assertThat(context.getBean(SkillIconImages.class).ability("x")).isEmpty();
    }
  }

  private static int widthOf(byte[] bytes) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(bytes)).getWidth();
  }

  @Test
  void theSyncedFileComesFirstAndIsScaledDown() throws IOException {
    Files.createDirectories(dir.resolve("ability"));
    Files.write(dir.resolve("ability").resolve("a_axe_culling_blade.png"), png(128));

    byte[] icon = images.ability("axe_culling_blade").orElseThrow();

    assertThat(widthOf(icon)).isEqualTo(SkillIconImages.SIZE_PX);
    assertThat(fetched).isEmpty();
  }

  @Test
  void theCdnIsUsedWhenThereIsNoFile() throws IOException {
    byte[] icon = images.ability("axe_culling_blade").orElseThrow();

    assertThat(widthOf(icon)).isEqualTo(SkillIconImages.SIZE_PX);
    assertThat(fetched).containsExactly("https://cdn.test/img/culling.png");
  }

  @Test
  void aHeroComesFromTheCdnByHeroId() {
    assertThat(images.hero(110)).isPresent();
    assertThat(fetched).containsExactly("https://cdn.test/img/phoenix.png");
  }

  @Test
  void aSecondAskIsServedFromTheCache() {
    images.ability("axe_culling_blade");
    images.ability("axe_culling_blade");

    assertThat(fetched).hasSize(1);
  }

  @Test
  void anAdNameFallsBackToTheBaseName() {
    assertThat(images.ability("axe_counter_helix_ad")).isPresent();
    assertThat(fetched).contains("https://cdn.test/img/helix.png");
  }

  @Test
  void aDownloadFailureIsEmptyAndRetriedLater() {
    failing = true;

    assertThat(images.ability("axe_culling_blade")).isEmpty();
    assertThat(images.ability("axe_culling_blade")).isEmpty();
    assertThat(fetched).hasSize(2);

    failing = false;
    assertThat(images.ability("axe_culling_blade")).isPresent();
  }

  @Test
  void unknownNamesAndIdsAreEmpty() {
    assertThat(images.ability("nope")).isEmpty();
    assertThat(images.ability(null)).isEmpty();
    assertThat(images.hero(1)).isEmpty();
    assertThat(images.hero(1)).isEmpty();
    assertThat(fetched).isEmpty();
  }
}
