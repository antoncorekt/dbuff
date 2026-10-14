package com.ako.dbuff.service.discord.icon;

import com.ako.dbuff.dao.model.DiscordIconDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.DiscordIconRepo;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
class IconSyncServiceTest {
  private static final String CDN = "https://cdn.test";

  @Autowired private DiscordIconRepo repo;
  @TempDir Path dir;

  private final ConstantsManagers constants = mock(ConstantsManagers.class);
  private final AbilityDraftPickRepo draftRepo = mock(AbilityDraftPickRepo.class);
  private final FightAbilityUseRepo useRepo = mock(FightAbilityUseRepo.class);
  private final FakeImages images = new FakeImages();
  private final FakeGateway gateway = new FakeGateway();
  private final IconProperties properties = new IconProperties();
  private final Map<String, ItemConstant> items = new HashMap<>();
  private IconService iconService;
  private IconSyncService sync;

  @BeforeEach
  void setUp() {
    properties.setEnabled(true);
    properties.setDirectory(dir);
    properties.setCdnUrl(CDN);
    properties.setCreateDelayMillis(0);
    when(constants.getHeroConstantMap())
        .thenReturn(Map.of("1", hero("1", "antimage", "/heroes/antimage.png?t=1")));
    when(constants.getItemConstantMap()).thenReturn(items);
    items.put("blink", item("/items/blink.png?t=1"));
    items.put("recipe_blink", item("/items/recipe.png"));
    items.put("no_image", item(null));
    Map<String, AbilityConstant> abilities = new HashMap<>();
    abilities.put("antimage_blink", ability("Blink", "/abilities/blink.png"));
    abilities.put("pudge_rot", ability("Rot", "/abilities/rot.png"));
    abilities.put("special_bonus_x", ability("+5", "/abilities/x.png"));
    abilities.put("no_img", ability("No Img", null));
    when(constants.getAllAbilityConstants()).thenReturn(abilities);
    when(draftRepo.findDistinctAbilityNames()).thenReturn(List.of("antimage_blink"));
    when(useRepo.findDistinctAbilityNames()).thenReturn(List.of("item_blink"));
    images.put("/heroes/antimage.png?t=1", 1);
    images.put("/items/blink.png?t=1", 2);
    images.put("/abilities/blink.png", 3);
    images.put("/abilities/rot.png", 4);
    iconService =
        new IconService(
            properties,
            repo,
            constants,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    sync = newSync();
  }

  private IconSyncService newSync() {
    return new IconSyncService(
        properties,
        repo,
        new IconCandidates(constants, draftRepo, useRepo),
        images,
        gateway,
        iconService);
  }

  @Test
  void heroesAndItems_areCreatedAndRecipesAndImagelessSkipped() {
    IconSyncService.Report report = sync.run(EnumSet.of(IconKind.HERO, IconKind.ITEM), false);

    assertThat(report).isEqualTo(new IconSyncService.Report(2, 0, 0, 0, 0));
    assertThat(gateway.live.values()).containsExactlyInAnyOrder("h_antimage", "i_blink");
    DiscordIconDomain hero = repo.findByKindAndIconKey(IconKind.HERO, "antimage").orElseThrow();
    assertThat(hero.getStatus()).isEqualTo(IconStatus.SYNCED);
    assertThat(hero.getEmojiName()).isEqualTo("h_antimage");
    assertThat(hero.getImageHash()).hasSize(64);
    assertThat(dir.resolve("hero/h_antimage.png")).exists();
    assertThat(iconService.hero(1)).isEqualTo("<:h_antimage:" + hero.getEmojiId() + "> ");
  }

  @Test
  void secondRun_isIdempotentAndDownloadsNothing() {
    sync.run(EnumSet.of(IconKind.HERO, IconKind.ITEM), false);
    int downloads = images.fetches.size();

    IconSyncService.Report report = sync.run(EnumSet.of(IconKind.HERO, IconKind.ITEM), false);

    assertThat(report).isEqualTo(new IconSyncService.Report(0, 0, 2, 0, 0));
    assertThat(images.fetches).hasSize(downloads);
    assertThat(gateway.created).hasSize(2);
  }

  @Test
  void changedImage_deletesTheOldEmojiAndCreatesANewOne() {
    sync.run(EnumSet.of(IconKind.HERO), false);
    long oldId = repo.findByKindAndIconKey(IconKind.HERO, "antimage").orElseThrow().getEmojiId();
    when(constants.getHeroConstantMap())
        .thenReturn(Map.of("1", hero("1", "antimage", "/heroes/antimage.png?t=2")));
    images.put("/heroes/antimage.png?t=2", 9);

    IconSyncService.Report report = sync.run(EnumSet.of(IconKind.HERO), false);

    assertThat(report.replaced()).isEqualTo(1);
    assertThat(gateway.deleted).containsExactly(oldId);
    DiscordIconDomain row = repo.findByKindAndIconKey(IconKind.HERO, "antimage").orElseThrow();
    assertThat(row.getEmojiId()).isNotEqualTo(oldId);
    assertThat(gateway.live).containsOnlyKeys(row.getEmojiId());
  }

  @Test
  void newPathWithSameImage_keepsTheEmoji() {
    sync.run(EnumSet.of(IconKind.HERO), false);
    when(constants.getHeroConstantMap())
        .thenReturn(Map.of("1", hero("1", "antimage", "/heroes/antimage.png?t=2")));
    images.put("/heroes/antimage.png?t=2", 1);

    IconSyncService.Report report = sync.run(EnumSet.of(IconKind.HERO), false);

    assertThat(report.unchanged()).isEqualTo(1);
    assertThat(gateway.created).hasSize(1);
    assertThat(gateway.deleted).isEmpty();
  }

  @Test
  void abilities_areLimitedToSeenOnesUnlessAllIsAsked() {
    IconSyncService.Report seen = sync.run(EnumSet.of(IconKind.ABILITY), false);

    assertThat(seen.created()).isEqualTo(1);
    assertThat(repo.findByKindAndIconKey(IconKind.ABILITY, "antimage_blink")).isPresent();
    assertThat(repo.findByKindAndIconKey(IconKind.ABILITY, "pudge_rot")).isEmpty();

    IconSyncService.Report all = sync.run(EnumSet.of(IconKind.ABILITY), true);

    assertThat(all.created()).isEqualTo(1);
    assertThat(repo.findByKindAndIconKey(IconKind.ABILITY, "pudge_rot")).isPresent();
    assertThat(repo.findByKindAndIconKey(IconKind.ABILITY, "special_bonus_x")).isEmpty();
    assertThat(repo.findByKindAndIconKey(IconKind.ABILITY, "no_img")).isEmpty();
  }

  @Test
  void failedDownload_isRecordedAndTheRunGoesOn() {
    images.failing.add("/heroes/antimage.png?t=1");

    IconSyncService.Report report = sync.run(EnumSet.of(IconKind.HERO, IconKind.ITEM), false);

    assertThat(report).isEqualTo(new IconSyncService.Report(1, 0, 0, 1, 0));
    DiscordIconDomain failed = repo.findByKindAndIconKey(IconKind.HERO, "antimage").orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(IconStatus.FAILED);
    assertThat(failed.getError()).contains("boom");
    assertThat(repo.findByKindAndIconKey(IconKind.ITEM, "blink").orElseThrow().getStatus())
        .isEqualTo(IconStatus.SYNCED);
  }

  @Test
  void failedUpload_isRecordedAndRetriedNextRun() {
    gateway.failNames.add("h_antimage");

    IconSyncService.Report report = sync.run(EnumSet.of(IconKind.HERO), false);

    assertThat(report.failed()).isEqualTo(1);
    assertThat(repo.findByKindAndIconKey(IconKind.HERO, "antimage").orElseThrow().getStatus())
        .isEqualTo(IconStatus.FAILED);

    gateway.failNames.clear();
    assertThat(sync.run(EnumSet.of(IconKind.HERO), false).created()).isEqualTo(1);
  }

  @Test
  void failedRefreshOfASyncedIcon_keepsTheWorkingEmoji() {
    sync.run(EnumSet.of(IconKind.HERO), false);
    when(constants.getHeroConstantMap())
        .thenReturn(Map.of("1", hero("1", "antimage", "/heroes/antimage.png?t=2")));
    images.failing.add("/heroes/antimage.png?t=2");

    sync.run(EnumSet.of(IconKind.HERO), false);

    DiscordIconDomain row = repo.findByKindAndIconKey(IconKind.HERO, "antimage").orElseThrow();
    assertThat(row.getStatus()).isEqualTo(IconStatus.SYNCED);
    assertThat(row.getEmojiId()).isNotNull();
    assertThat(row.getError()).contains("boom");
  }

  @Test
  void hardLimit_stopsCreatingAndRecordsTheRest() {
    properties.setMaxEmoji(1);

    IconSyncService.Report report = sync.run(EnumSet.of(IconKind.HERO, IconKind.ITEM), false);

    assertThat(report).isEqualTo(new IconSyncService.Report(1, 0, 0, 0, 1));
    assertThat(gateway.created).hasSize(1);
    DiscordIconDomain over = repo.findByKindAndIconKey(IconKind.ITEM, "blink").orElseThrow();
    assertThat(over.getStatus()).isEqualTo(IconStatus.FAILED);
    assertThat(over.getError()).contains("limit");
  }

  @Test
  void takenEmojiName_getsAHashSuffix() {
    repo.save(
        DiscordIconDomain.builder()
            .kind(IconKind.ITEM)
            .iconKey("other")
            .emojiId(500L)
            .emojiName("h_antimage")
            .status(IconStatus.SYNCED)
            .build());

    sync.run(EnumSet.of(IconKind.HERO), false);

    String name = repo.findByKindAndIconKey(IconKind.HERO, "antimage").orElseThrow().getEmojiName();
    assertThat(name).startsWith("h_antimage_").hasSize("h_antimage_".length() + 6);
  }

  @Test
  void missingIcon_isCreatedAgainWithoutDeletingTheGoneEmoji() {
    sync.run(EnumSet.of(IconKind.HERO), false);
    DiscordIconDomain row = repo.findByKindAndIconKey(IconKind.HERO, "antimage").orElseThrow();
    row.setStatus(IconStatus.MISSING);
    repo.save(row);

    IconSyncService.Report report = sync.run(EnumSet.of(IconKind.HERO), false);

    assertThat(report.created()).isEqualTo(1);
    assertThat(gateway.deleted).isEmpty();
    assertThat(repo.findByKindAndIconKey(IconKind.HERO, "antimage").orElseThrow().getStatus())
        .isEqualTo(IconStatus.SYNCED);
  }

  @Test
  void startupCheck_marksEmojiMissingOnDiscordAndIconServiceStopsUsingThem() {
    sync.run(EnumSet.of(IconKind.HERO, IconKind.ITEM), false);
    long heroId = repo.findByKindAndIconKey(IconKind.HERO, "antimage").orElseThrow().getEmojiId();
    gateway.live.remove(heroId);

    int missing = new IconRegistryCheck(repo, gateway, iconService).verify();

    assertThat(missing).isEqualTo(1);
    assertThat(repo.findByKindAndIconKey(IconKind.HERO, "antimage").orElseThrow().getStatus())
        .isEqualTo(IconStatus.MISSING);
    assertThat(repo.findByKindAndIconKey(IconKind.ITEM, "blink").orElseThrow().getStatus())
        .isEqualTo(IconStatus.SYNCED);
    assertThat(iconService.hero(1)).isEqualTo(IconKind.HERO.getPlaceholder() + " ");
    assertThat(iconService.item("blink")).startsWith("<:i_blink:");
  }

  @Test
  void startupCheck_withEverythingPresent_changesNothing() {
    sync.run(EnumSet.of(IconKind.HERO), false);

    assertThat(new IconRegistryCheck(repo, gateway, iconService).verify()).isZero();
    assertThat(repo.countByStatus(IconStatus.SYNCED)).isEqualTo(1);
  }

  private static HeroConstant hero(String id, String key, String img) {
    HeroConstant h = new HeroConstant(id, "npc_dota_hero_" + key, key);
    h.setImg(img);
    return h;
  }

  private static ItemConstant item(String img) {
    ItemConstant i = new ItemConstant();
    i.setImg(img);
    i.setDname("x");
    return i;
  }

  private static AbilityConstant ability(String dname, String img) {
    AbilityConstant a = new AbilityConstant();
    a.setDname(dname);
    a.setImg(img);
    return a;
  }

  private static byte[] png(int shade) throws IOException {
    BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < 64; x++) {
      for (int y = 0; y < 32; y++) {
        image.setRGB(x, y, (shade * 20 << 16) | (x * 3));
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  private static final class FakeImages implements IconImageSource {
    final Map<String, byte[]> byUrl = new HashMap<>();
    final List<String> fetches = new ArrayList<>();
    final Set<String> failing = new java.util.HashSet<>();

    void put(String path, int shade) {
      try {
        byUrl.put(CDN + path, png(shade));
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public byte[] fetch(String url) throws IOException {
      fetches.add(url);
      if (failing.contains(url.substring(CDN.length()))) {
        throw new IOException("boom");
      }
      byte[] data = byUrl.get(url);
      if (data == null) {
        throw new IOException("404 " + url);
      }
      return data;
    }
  }

  private static final class FakeGateway implements EmojiGateway {
    final Map<Long, String> live = new HashMap<>();
    final List<Long> created = new ArrayList<>();
    final List<Long> deleted = new ArrayList<>();
    final Set<String> failNames = new java.util.HashSet<>();
    long next = 1000;

    @Override
    public List<RemoteEmoji> list() {
      return live.entrySet().stream().map(e -> new RemoteEmoji(e.getKey(), e.getValue())).toList();
    }

    @Override
    public RemoteEmoji create(String name, byte[] png) {
      if (failNames.contains(name)) {
        throw new IllegalStateException("quota");
      }
      long id = next++;
      live.put(id, name);
      created.add(id);
      return new RemoteEmoji(id, name);
    }

    @Override
    public void delete(long emojiId) {
      live.remove(emojiId);
      deleted.add(emojiId);
    }
  }
}
