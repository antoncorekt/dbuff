package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.report.SectionViews.Verdict;
import com.ako.dbuff.service.replay.report.chart.ChartPalette;
import com.ako.dbuff.service.replay.report.chart.SkillIconImages;
import com.ako.dbuff.service.replay.report.chart.TableRenderer;
import com.ako.dbuff.service.replay.report.chart.TableSpec;
import com.ako.dbuff.service.replay.report.chart.TableSpec.Cell;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What each tracked player bought and when: the assembled or costly items, in purchase order. */
@Component
@RequiredArgsConstructor
public class ItemBuildSection implements ReplayReportSection {
  private static final int ITEMS_SHOWN = 8;
  private static final long COSTLY = 2000;
  private static final String TITLE = "Item builds";

  private final ItemRepository itemRepo;
  private final PlayerGameStatisticRepo statRepo;
  private final ConstantsManagers constants;
  private final HeroKits heroKits;
  private final SkillIconImages icons;
  private final TableRenderer tables;
  private final PlainViewRenderer plain = new PlainViewRenderer();
  private final PictureViews pictures = new PictureViews();

  @Override
  public boolean supports(GameModeProfile mode) {
    return true;
  }

  @Override
  public String name() {
    return "item-build";
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

  @Override
  public int order() {
    return 60;
  }

  @Override
  public String summary() {
    return "what each tracked player bought and when";
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    return view(input).map(plain::lines).orElse(List.of());
  }

  @Override
  public Optional<ImageAttachment> image(ReplayReportInput input) {
    return view(input).map(SectionView::image);
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    return pictures.view(input, this::build);
  }

  private record Bought(String key, String label, long cost, Long seconds) {}

  private record Buyer(long slot, String name, Long heroId, List<Bought> items) {}

  private Optional<SectionView> build(ReplayReportInput input) {
    List<Buyer> buyers = buyers(input);
    if (buyers.isEmpty()) {
      return Optional.empty();
    }
    List<String> bullets = new ArrayList<>();
    Optional<ImageAttachment> image = picture(input.matchId(), buyers);
    if (image.isEmpty()) {
      for (Buyer buyer : buyers) {
        List<String> parts = new ArrayList<>();
        buyer.items().forEach(i -> parts.add(i.label() + " " + when(i.seconds())));
        bullets.add(player(buyer) + ": " + String.join(", ", parts));
      }
      bullets.add(PictureViews.NOT_DRAWN);
    }
    Verdict verdict =
        new Verdict(
            "Item builds of " + SectionViews.count(buyers.size(), "tracked player"),
            Accent.NEUTRAL);
    return Optional.of(
        new SectionView(
            TITLE, verdict.text(), verdict.accent(), bullets, null, image.orElse(null)));
  }

  private List<Buyer> buyers(ReplayReportInput input) {
    Map<String, ItemConstant> catalog = constants.getItemConstantMap();
    List<ItemDomain> rows = itemRepo.findAllByMatchId(input.matchId());
    Map<Long, Long> heroBySlot = new HashMap<>();
    statRepo
        .findAllByMatchId(input.matchId())
        .forEach(
            s -> {
              if (s.getPlayerSlot() != null) {
                heroBySlot.put(s.getPlayerSlot(), s.getHeroId());
              }
            });
    List<Buyer> buyers = new ArrayList<>();
    for (Map.Entry<Long, String> player : input.namesBySlot().entrySet()) {
      List<Bought> shown = new ArrayList<>();
      for (ItemDomain row : rows) {
        if (!player.getKey().equals(row.getPlayerSlot()) || row.isNeutral()) {
          continue;
        }
        ItemConstant item = catalog.get(row.getItemName());
        if (item == null || !shown(item)) {
          continue;
        }
        String label =
            item.getDname() != null
                ? item.getDname()
                : row.getItemPrettyName() != null ? row.getItemPrettyName() : row.getItemName();
        shown.add(
            new Bought(
                row.getItemName(),
                label,
                item.getCost() == null ? 0 : item.getCost(),
                row.getItemPurchaseTime()));
      }
      if (shown.isEmpty()) {
        continue;
      }
      List<Bought> kept =
          shown.stream()
              .sorted(Comparator.comparingLong(Bought::cost).reversed())
              .limit(ITEMS_SHOWN)
              .sorted(
                  Comparator.comparing(
                      Bought::seconds, Comparator.nullsLast(Comparator.naturalOrder())))
              .toList();
      buyers.add(
          new Buyer(player.getKey(), player.getValue(), heroBySlot.get(player.getKey()), kept));
    }
    return buyers;
  }

  private static boolean shown(ItemConstant item) {
    boolean assembled = item.getComponents() != null && !item.getComponents().isEmpty();
    return assembled || (item.getCost() != null && item.getCost() >= COSTLY);
  }

  private static String when(Long seconds) {
    if (seconds == null) {
      return "?";
    }
    return seconds < 0 ? "start" : GameTime.clock(seconds);
  }

  private String player(Buyer buyer) {
    return buyer.heroId() == null
        ? buyer.name()
        : heroKits
            .hero(buyer.heroId())
            .map(HeroConstant::getLocalized_name)
            .map(h -> ReportHeroNames.labelOnce(buyer.name(), h))
            .orElse(buyer.name());
  }

  private Optional<ImageAttachment> picture(long matchId, List<Buyer> buyers) {
    return PictureViews.draw(
        "Item build",
        matchId,
        () -> {
          int most = buyers.stream().mapToInt(b -> b.items().size()).max().orElse(0);
          List<String> headers = new ArrayList<>(List.of("Player"));
          for (int i = 1; i <= most; i++) {
            headers.add("Item " + i);
          }
          List<List<Cell>> rows = new ArrayList<>();
          for (Buyer buyer : buyers) {
            Color band =
                TeamSide.ofSlot(buyer.slot()) == TeamSide.RADIANT
                    ? ChartPalette.RADIANT
                    : ChartPalette.DIRE;
            byte[] heroIcon =
                buyer.heroId() == null ? null : icons.hero(buyer.heroId()).orElse(null);
            List<Cell> row = new ArrayList<>();
            row.add(new Cell(heroIcon, player(buyer), null, band));
            for (Bought item : buyer.items()) {
              byte[] icon = icons.item(item.key()).orElse(null);
              row.add(
                  new Cell(
                      icon, when(item.seconds()), null, null, icon == null ? item.label() : null));
            }
            rows.add(row);
          }
          return tables
              .render(new TableSpec(TITLE, headers, rows))
              .map(png -> new ImageAttachment("item-build-" + matchId + ".png", png));
        });
  }
}
