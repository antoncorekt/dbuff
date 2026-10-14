package com.ako.dbuff.config;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorExposureTest {

  @Test
  void production_exposesOnlyHealthAndMetrics() throws IOException {
    Properties prod =
        PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));

    assertThat(prod.getProperty("management.endpoints.web.exposure.include"))
        .isEqualTo("health,metrics");
    assertThat(prod.getProperty("management.endpoints.web.exposure.exclude")).isNull();
  }
}
