package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.replay.report.OnDemandCooldown;
import com.ako.dbuff.service.replay.report.ReplayReportService;
import com.ako.dbuff.service.replay.report.ReportDelivery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * {@code /match ai}: posts the AI top reasons card of the match of this thread. Everything that can
 * be refused is checked before the command is acknowledged, each with its own one-line reply.
 */
@Slf4j
@Component
public class AiReasonsCommandHandler {
  public static final String MODEL_OPTION = "model";

  private final AiReasonsService service;
  private final ReplayReportService reports;
  private final OnDemandCooldown cooldown;

  public AiReasonsCommandHandler(
      AiReasonsService service, @Lazy ReplayReportService reports, OnDemandCooldown cooldown) {
    this.service = service;
    this.reports = reports;
    this.cooldown = cooldown;
  }

  public void handle(CommandContext context, long matchId, DbufInstanceConfigDomain config) {
    String model = context.getOption(MODEL_OPTION);
    AiReasonsAvailability availability = service.availability();
    if (availability != AiReasonsAvailability.AVAILABLE) {
      context.replyEphemeral("❌ Not available: " + availability.reason() + ".");
      return;
    }
    if (!service.isModelAllowed(model)) {
      context.replyEphemeral(
          "❌ Model `"
              + model.strip()
              + "` is not allowed. Allowed: "
              + String.join(", ", service.allowedModels())
              + ".");
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
    AsyncReply reply =
        context.acknowledge(
            "🔄 Working out the top reasons for match " + matchId + "…", "Match " + matchId);
    try {
      ReportDelivery delivery =
          reports.deliverSection(matchId, config.getId(), AiReasonsSection.NAME, model);
      if (delivery != ReportDelivery.POSTED) {
        reply.post("The top reasons were not posted: " + delivery.reason() + ".");
      }
    } catch (RuntimeException e) {
      log.warn("AI reasons of match {} failed: {}", matchId, e.getMessage(), e);
      reply.fail("❌ The top reasons of match " + matchId + " failed, see the log.");
    }
  }
}
