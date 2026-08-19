package com.ako.dbuff.config;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import java.io.FileInputStream;
import java.io.InputStream;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Configuration for the Google Cloud Vision (OCR / text detection) client.
 *
 * <p>Credentials resolution order:
 *
 * <ol>
 *   <li>If {@code google.vision.credentials-path} is set, the service-account JSON key at that path
 *       is loaded explicitly.
 *   <li>Otherwise the client falls back to Application Default Credentials, i.e. the standard
 *       {@code GOOGLE_APPLICATION_CREDENTIALS} environment variable pointing at the service-account
 *       JSON key.
 * </ol>
 */
@Slf4j
@Configuration
public class VisionConfig {

  private final VisionConfigurationProperties visionConfigurationProperties;

  public VisionConfig(VisionConfigurationProperties visionConfigurationProperties) {
    this.visionConfigurationProperties = visionConfigurationProperties;
    log.info(
        "Google Vision explicit credentials-path configured: {}",
        StringUtils.hasLength(visionConfigurationProperties.getCredentialsPath()));
  }

  /**
   * Builds the {@link ImageAnnotatorClient} used for text detection, or returns {@code null} if no
   * usable credentials are available.
   *
   * <p>Returning null rather than throwing is deliberate. A {@code @Bean} method that throws fails
   * the entire application context, so an unreadable or revoked Google key would take the whole
   * service down — including match ingestion and Discord reporting, which have nothing to do with
   * OCR. Spring registers a null return as an absent bean, which {@code ImageProcessor} injects as
   * an empty {@link java.util.Optional} and reports per-request instead.
   *
   * @return a configured Vision client, or {@code null} if credentials are missing or unusable
   */
  @Bean
  public ImageAnnotatorClient imageAnnotatorClient() {
    String credentialsPath = visionConfigurationProperties.getCredentialsPath();

    try {
      if (StringUtils.hasLength(credentialsPath)) {
        log.info(
            "Creating Google Vision client from explicit credentials path: {}", credentialsPath);
        try (InputStream credentialsStream = new FileInputStream(credentialsPath)) {
          CredentialsProvider credentialsProvider =
              FixedCredentialsProvider.create(GoogleCredentials.fromStream(credentialsStream));
          ImageAnnotatorSettings settings =
              ImageAnnotatorSettings.newBuilder()
                  .setCredentialsProvider(credentialsProvider)
                  .build();
          return ImageAnnotatorClient.create(settings);
        }
      }

      // Fall back to Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS env var).
      log.info(
          "Creating Google Vision client using Application Default Credentials "
              + "(GOOGLE_APPLICATION_CREDENTIALS)");
      return ImageAnnotatorClient.create();
    } catch (Exception e) {
      // Both branches can fail without OCR being configured at all: an unset credentials-path
      // falls through to ADC, which throws on any host with no GOOGLE_APPLICATION_CREDENTIALS and
      // no GCP metadata server - such as the EC2 instance this runs on.
      log.warn(
          "Google Vision client unavailable; OCR endpoints will fail until credentials are fixed. "
              + "Reason: {}",
          e.getMessage());
      return null;
    }
  }

  @Data
  @Configuration
  @ConfigurationProperties(prefix = "google.vision")
  public static class VisionConfigurationProperties {

    /**
     * Optional filesystem path to the Google service-account JSON key. When empty, Application
     * Default Credentials ({@code GOOGLE_APPLICATION_CREDENTIALS}) are used instead.
     */
    private String credentialsPath;
  }
}
