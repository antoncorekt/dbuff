package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

final class ComboPlayers {

  private ComboPlayers() {}

  static ResolvedPlayer single(AskToolArgs args, JsonNode input, AskContext ctx) {
    List<ResolvedPlayer> players = args.players(input, "players", ctx);
    if (players.size() > 1) {
      throw new AskToolException("Combo statistics take one player; call the tool once per player");
    }
    return players.get(0);
  }
}
