package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyFile;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * {@code /match report <section|all|everything>}: posts one section, every section the channel
 * posts automatically, or every section there is, of the match of this thread. Everything that can
 * be refused is checked before the command is acknowledged, each with its own one-line reply.
 */
@Slf4j
@Component
public class ReportCommandHandler {
  public static final String SECTION_OPTION = "section";
  public static final String ITEMS_OPTION = "items";
  public static final String ALL = "all";
  public static final String EVERYTHING = "everything";
  private static final String ITEMS_SECTION = "items";

  private final ReplayReportService reports;
  private final SectionSwitch sectionSwitch;
  private final OnDemandCooldown cooldown;
  private final ItemEfficiencyFile itemCatalog;

  public ReportCommandHandler(
      @Lazy ReplayReportService reports,
      @Lazy SectionSwitch sectionSwitch,
      OnDemandCooldown cooldown,
      ItemEfficiencyFile itemCatalog) {
    this.itemCatalog = itemCatalog;
    this.reports = reports;
    this.sectionSwitch = sectionSwitch;
    this.cooldown = cooldown;
  }

  public void handle(CommandContext context, long matchId, DbufInstanceConfigDomain config) {
    String requested = context.getOption(SECTION_OPTION);
    if (requested == null || requested.isBlank()) {
      context.replyEphemeral("❌ Name a section: " + choices() + ".");
      return;
    }
    boolean all = ALL.equalsIgnoreCase(requested.strip());
    boolean everything = EVERYTHING.equalsIgnoreCase(requested.strip());
    boolean whole = all || everything;
    Optional<ReplayReportSection> section =
        whole ? Optional.empty() : sectionSwitch.find(requested.strip());
    if (!whole && section.isEmpty()) {
      context.replyEphemeral(
          "❌ Unknown section `" + requested.strip() + "`. Choose from: " + choices() + ".");
      return;
    }
    if (section.isPresent() && sectionSwitch.isHidden(section.get())) {
      context.replyEphemeral(
          "❌ Section `" + section.get().name() + "` is switched off on the server.");
      return;
    }
    List<String> items;
    try {
      items = itemFilter(context, section);
    } catch (IllegalArgumentException e) {
      context.replyEphemeral("❌ " + e.getMessage());
      return;
    }
    if (!reports.hasStoredAnalysis(matchId)) {
      context.replyEphemeral(
          "❌ Match "
              + matchId
              + " has no stored replay analysis yet (the replay run must be done or partial).");
      return;
    }
    OnDemandCooldown.Permit permit = cooldown.acquire(context.getChannelId());
    if (!permit.granted()) {
      context.replyEphemeral(
          "⏳ This thread just had a report or AI card. Try again in "
              + permit.waitSeconds()
              + " s.");
      return;
    }
    boolean posted = false;
    try {
      String label =
          everything
              ? "every section"
              : all ? "the report" : "section `" + section.get().name() + "`";
      AsyncReply reply =
          context.acknowledge(
              "🔄 Preparing " + label + " of match " + matchId + "…", "Match " + matchId);
      posted = deliver(reply, matchId, config, requested, all, everything, section, items);
    } finally {
      if (!posted) {
        cooldown.release(permit);
      }
    }
  }

  private boolean deliver(
      AsyncReply reply,
      long matchId,
      DbufInstanceConfigDomain config,
      String requested,
      boolean all,
      boolean everything,
      Optional<ReplayReportSection> section,
      List<String> items) {
    try {
      ReportDelivery delivery =
          everything
              ? reports.deliverEverything(matchId, config.getId())
              : all
                  ? reports.redeliver(matchId, config.getId())
                  : items == null
                      ? reports.deliverSection(matchId, config.getId(), section.get().name())
                      : reports.deliverSection(
                          matchId, config.getId(), section.get().name(), null, items);
      if (delivery == ReportDelivery.POSTED) {
        return true;
      }
      reply.post(notPosted(delivery, all, everything, section, matchId));
    } catch (RuntimeException e) {
      log.warn("Report {} of match {} failed: {}", requested, matchId, e.getMessage(), e);
      reply.fail("❌ The report of match " + matchId + " failed, see the log.");
    }
    return false;
  }

  /**
   * The items a person named for the items section; null when none, so the channel's apply; empty
   * for all.
   */
  private List<String> itemFilter(CommandContext context, Optional<ReplayReportSection> section) {
    String raw = context.getOption(ITEMS_OPTION);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    if (section.isEmpty() || !ITEMS_SECTION.equals(section.get().name())) {
      throw new IllegalArgumentException("`items:` only applies to `section:items`.");
    }
    return itemCatalog.parseSelection(raw).orElse(List.of());
  }

  private static String notPosted(
      ReportDelivery delivery,
      boolean all,
      boolean everything,
      Optional<ReplayReportSection> section,
      long matchId) {
    if (everything && delivery == ReportDelivery.SECTION_EMPTY) {
      return "No section has anything to show for match " + matchId + ".";
    }
    if (all && delivery == ReportDelivery.NO_SECTIONS) {
      return "Nothing is set to post automatically in this channel; name a section instead"
          + " (see `/dbuff settings sections:`).";
    }
    if (!all && delivery == ReportDelivery.SECTION_EMPTY) {
      return "Section `"
          + section.get().name()
          + "` has nothing to show for match "
          + matchId
          + ": its data does not exist for this match.";
    }
    return "Not posted: " + delivery.reason() + ".";
  }

  private String choices() {
    return ALL + ", " + EVERYTHING + ", " + String.join(", ", sectionSwitch.visibleNames());
  }
}
