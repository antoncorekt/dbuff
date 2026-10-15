package com.ako.dbuff.service.discord.icon;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IconKind {
  HERO("h_", "⚔️"),
  ABILITY("a_", "📖"),
  ITEM("i_", "🎒");

  private final String namePrefix;
  private final String placeholder;
}
