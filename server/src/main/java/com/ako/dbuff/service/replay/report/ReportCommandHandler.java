package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * {@code /match report <section|all>}: posts one section, or every section the channel posts
 * automatically, of the match of this thread. Everything that can be refused is checked before the
 * command is acknowledged, each with its own one-line reply.
 */
@Slf4j
@Component
public class ReportCommandHandler {
  public static final String SECTION_OPTION = "section";
  public static final String ALL = "all";

  private final ReplayReportService reports;
  private final SectionSwitch sectionSwitch;
  private final OnDemandCooldown cooldown;

  public ReportCommandHandler(
      @Lazy ReplayReportService reports,
      @Lazy SectionSwitch sectionSwitch,
      OnDemandCooldown cooldown) {
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
    Optional<ReplayReportSection> section =
        all ? Optional.empty() : sectionSwitch.find(requested.strip());
    if (!all && section.isEmpty()) {
      context.replyEphemeral(
          "❌ Unknown section `" + requested.strip() + "`. Choose from: " + choices() + ".");
      return;
    }
    if (section.isPresent() && sectionSwitch.isHidden(section.get())) {
      context.replyEphemeral(
          "❌ Section `" + section.get().name() + "` is switched off on the server.");
      return;
    }
    if (!reports.hasStoredAnalysis(matchId)) {
      context.replyEphemeral(
          "❌ Match "
              + matchId
              + " has no stored replay analysis yet (the replay run must be done or partial).");
      return;
    }
    long wait = cooldown.acquire(context.getChannelId());
    if (wait > 0) {
      context.replyEphemeral(
          "⏳ This thread just had a report or AI card. Try again in " + wait + " s.");
      return;
    }
    String label = all ? "the report" : "section `" + section.get().name() + "`";
    AsyncReply reply =
        context.acknowledge(
            "🔄 Preparing " + label + " of match " + matchId + "…", "Match " + matchId);
    try {
      ReportDelivery delivery =
          all
              ? reports.redeliver(matchId, config.getId())
              : reports.deliverSection(matchId, config.getId(), section.get().name());
      if (delivery == ReportDelivery.POSTED) {
        return;
      }
      cooldown.release(context.getChannelId());
      reply.post(notPosted(delivery, all, section, matchId));
    } catch (RuntimeException e) {
      cooldown.release(context.getChannelId());
      log.warn("Report {} of match {} failed: {}", requested, matchId, e.getMessage(), e);
      reply.fail("❌ The report of match " + matchId + " failed, see the log.");
    }
  }

  private static String notPosted(
      ReportDelivery delivery,
      boolean all,
      Optional<ReplayReportSection> section,
      long matchId) {
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
    return ALL + ", " + String.join(", ", sectionSwitch.visibleNames());
  }
}
