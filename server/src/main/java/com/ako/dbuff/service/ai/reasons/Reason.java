package com.ako.dbuff.service.ai.reasons;

import java.util.List;

/** One validated reason: ranked from 1, with the ids of the facts it rests on. */
public record Reason(int rank, String title, String explanation, List<String> evidence) {}
