package com.ako.dbuff.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Requires {@code X-Api-Key} on every request except the health check.
 *
 * <p>The bot is reachable by anyone in the servers it joins, and the REST API can register
 * instances against any channel the bot can see, so it must not stay open to the internet. The
 * health endpoint is exempt because the deploy gate and rollback poll it.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Api-Key";

  private static final String HEALTH = "/actuator/health";

  private final ApiSecurityProperties properties;

  @PostConstruct
  void announce() {
    if (properties.hasKey()) {
      log.info("REST API requires {}", HEADER);
    } else if (properties.isRequired()) {
      log.warn("REST API is closed to every caller: app.api.key (DBUFF_API_KEY) is not set");
    } else {
      log.warn("REST API is open: no app.api.key is set and app.api.required is false");
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // The container-normalised path, not getRequestURI(): the raw URI still carries "/../".
    String path =
        request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
    return path.equals(HEALTH) || path.startsWith(HEALTH + "/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!properties.hasKey() && !properties.isRequired()) {
      chain.doFilter(request, response);
      return;
    }
    if (properties.hasKey() && matches(request.getHeader(HEADER))) {
      chain.doFilter(request, response);
      return;
    }
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"Unauthorized\"}");
  }

  /** Constant-time, so response timing does not reveal how much of a guess was right. */
  private boolean matches(String presented) {
    return presented != null
        && MessageDigest.isEqual(
            presented.getBytes(StandardCharsets.UTF_8),
            properties.getKey().getBytes(StandardCharsets.UTF_8));
  }
}
