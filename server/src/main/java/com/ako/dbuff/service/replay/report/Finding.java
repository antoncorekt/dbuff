package com.ako.dbuff.service.replay.report;

/** One statement of a section with what it is about. */
public record Finding(FindingKind kind, String text) {}
