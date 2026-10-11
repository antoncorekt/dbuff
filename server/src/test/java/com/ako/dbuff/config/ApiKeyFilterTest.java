package com.ako.dbuff.config;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyFilterTest {

  private static final String KEY = "correct-horse-battery-staple";

  private static ApiKeyFilter filter(String key, boolean required) {
    ApiSecurityProperties properties = new ApiSecurityProperties();
    properties.setKey(key);
    properties.setRequired(required);
    return new ApiKeyFilter(properties);
  }

  private static MockHttpServletRequest request(String servletPath, String pathInfo, String key) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", servletPath);
    request.setServletPath(servletPath);
    request.setPathInfo(pathInfo);
    if (key != null) {
      request.addHeader(ApiKeyFilter.HEADER, key);
    }
    return request;
  }

  /** Runs the filter and reports whether the request reached the application. */
  private static Result run(ApiKeyFilter filter, MockHttpServletRequest request)
      throws ServletException, IOException {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    return new Result(chain.getRequest() != null, response);
  }

  private record Result(boolean reachedApp, MockHttpServletResponse response) {}

  @Test
  void correctKey_reachesTheApplication() throws Exception {
    Result result = run(filter(KEY, true), request("/api/v1/register", null, KEY));

    assertThat(result.reachedApp()).isTrue();
    assertThat(result.response().getStatus()).isEqualTo(200);
  }

  @Test
  void wrongKey_isRefused() throws Exception {
    Result result = run(filter(KEY, true), request("/api/v1/register", null, "nope"));

    assertThat(result.reachedApp()).isFalse();
    assertThat(result.response().getStatus()).isEqualTo(401);
    assertThat(result.response().getContentAsString()).contains("Unauthorized");
    assertThat(result.response().getContentType()).startsWith("application/json");
  }

  @Test
  void missingKey_isRefused() throws Exception {
    Result result = run(filter(KEY, true), request("/api/v1/register", null, null));

    assertThat(result.reachedApp()).isFalse();
    assertThat(result.response().getStatus()).isEqualTo(401);
  }

  @Test
  void aKeyThatOnlySharesAPrefix_isRefused() throws Exception {
    assertThat(run(filter(KEY, true), request("/api/v1/x", null, KEY.substring(0, 5))).reachedApp())
        .isFalse();
    assertThat(run(filter(KEY, true), request("/api/v1/x", null, KEY + "x")).reachedApp())
        .isFalse();
  }

  @Test
  void healthCheck_needsNoKey() throws Exception {
    assertThat(run(filter(KEY, true), request("/actuator/health", null, null)).reachedApp())
        .isTrue();
    assertThat(run(filter(KEY, true), request("/actuator", "/health/liveness", null)).reachedApp())
        .isTrue();
  }

  @Test
  void otherActuatorEndpoints_stillNeedTheKey() throws Exception {
    assertThat(run(filter(KEY, true), request("/actuator/env", null, null)).reachedApp()).isFalse();
    assertThat(run(filter(KEY, true), request("/actuator/healthz", null, null)).reachedApp())
        .isFalse();
  }

  @Test
  void aPathThatMerelyStartsLikeHealth_isNotExempt() throws Exception {
    assertThat(run(filter(KEY, true), request("/api/actuator/health", null, null)).reachedApp())
        .isFalse();
  }

  @Test
  void noKeyConfigured_andRequired_closesTheApiEvenForACallerWithAKey() throws Exception {
    assertThat(run(filter("", true), request("/api/v1/register", null, "anything")).reachedApp())
        .isFalse();
    assertThat(run(filter(null, true), request("/api/v1/register", null, null)).reachedApp())
        .isFalse();
    assertThat(run(filter("   ", true), request("/api/v1/register", null, "   ")).reachedApp())
        .isFalse();
  }

  @Test
  void noKeyConfigured_andNotRequired_isOpenForLocalDevelopment() throws Exception {
    assertThat(run(filter("", false), request("/api/v1/register", null, null)).reachedApp())
        .isTrue();
  }

  @Test
  void keyConfigured_isEnforcedEvenWhenNotRequired() throws Exception {
    assertThat(run(filter(KEY, false), request("/api/v1/register", null, null)).reachedApp())
        .isFalse();
    assertThat(run(filter(KEY, false), request("/api/v1/register", null, KEY)).reachedApp())
        .isTrue();
  }

  @Test
  void healthCheck_isExemptEvenWhenTheApiIsClosed() throws Exception {
    assertThat(run(filter("", true), request("/actuator/health", null, null)).reachedApp())
        .isTrue();
  }
}
