package com.ako.dbuff.config;

import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.constant.ConstantPrecacheStarter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against a real embedded server, because the health exemption must survive path tricks and only
 * the container's own path normalisation can show that: a mock request does not normalise.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.api.key=test-key", "app.api.required=true"})
@ActiveProfiles("test")
class ApiKeyFilterWebTest {

  @MockBean private JDA jda;
  @MockBean private ConstantPrecacheStarter constantPrecacheStarter;

  @Autowired private ReplayParseRunRepo runRepo;

  private static final long MATCH = 9_000_000_001L;

  @LocalServerPort private int port;

  private final HttpClient client = HttpClient.newHttpClient();

  private int status(String rawPath, String key) throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath));
    if (key != null) {
      request.header(ApiKeyFilter.HEADER, key);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  /** Sends the request line verbatim; HttpClient would tidy a "/../" away before it left. */
  private int rawStatus(String rawPath) throws IOException {
    try (java.net.Socket socket = new java.net.Socket("localhost", port)) {
      socket
          .getOutputStream()
          .write(
              ("GET " + rawPath + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                  .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      socket.getOutputStream().flush();
      String statusLine =
          new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()))
              .readLine();
      return Integer.parseInt(statusLine.split(" ")[1]);
    }
  }

  @Test
  void theApiRefusesACallerWithNoKey() throws Exception {
    assertThat(status("/api/v1/register", null)).isEqualTo(401);
  }

  @Test
  void theApiRefusesAWrongKey() throws Exception {
    assertThat(status("/api/v1/register", "wrong")).isEqualTo(401);
  }

  @Test
  void theApiAnswersACallerWithTheKey() throws Exception {
    assertThat(status("/api/v1/register", "test-key")).isNotEqualTo(401);
  }

  @Test
  void theAnalysisEndpointNeedsTheKey() throws Exception {
    runRepo.save(
        ReplayParseRunDomain.builder()
            .matchId(MATCH)
            .parserVersion(1)
            .status(ReplayStatus.DONE)
            .attempts(1)
            .build());
    try {
      assertThat(status("/api/v1/replay/" + MATCH + "/analysis", null)).isEqualTo(401);
      assertThat(status("/api/v1/replay/" + MATCH + "/analysis", "test-key")).isEqualTo(200);
    } finally {
      runRepo.deleteAll();
    }
  }

  @Test
  void theHealthCheckNeedsNoKey() throws Exception {
    assertThat(status("/actuator/health", null)).isNotEqualTo(401);
  }

  @Test
  void aPathThatClimbsOutOfTheHealthCheck_doesNotBorrowItsExemption() throws Exception {
    // Each of these would be exempt if the check read the raw request URI, and would then fall
    // through to the application (404 here) instead of being stopped at the filter.
    assertThat(rawStatus("/actuator/health/../../api/v1/register")).isEqualTo(401);
    assertThat(rawStatus("/actuator/health/%2e%2e/%2e%2e/api/v1/register")).isEqualTo(401);
    assertThat(rawStatus("/actuator/health/..;/..;/api/v1/register")).isEqualTo(401);
  }

  @Test
  void theRawRequestLineStillReachesHealthAndTheApiWhenWellFormed() throws Exception {
    assertThat(rawStatus("/actuator/health")).isEqualTo(200);
    assertThat(rawStatus("/api/v1/register")).isEqualTo(401);
  }
}
