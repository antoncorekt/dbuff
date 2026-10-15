package com.ako.dbuff.service.discord.command;

import java.util.List;

/**
 * The option names of {@code /skills}, defined once: the slash definition, the text parser and the
 * autocomplete providers all read these, so a spelling cannot drift between the surfaces.
 */
public final class SkillsOptions {
  public static final String HERO = "hero";
  public static final String SKILL = "skill";
  public static final String WITH = "with";

  /** Text typed after the alias with no option name; only the text surface produces it. */
  public static final String UNLABELLED = "text";

  public static final List<String> ALL = List.of(HERO, SKILL, WITH);

  private SkillsOptions() {}
}
