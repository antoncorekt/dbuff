package com.ako.dbuff.service.ask.sql;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * The read-only pool of the ask tool. A second DataSource bean makes Spring Boot drop its own, so
 * the primary one is declared here too, from the same {@code spring.datasource.*} settings.
 */
@Configuration
@ConditionalOnProperty(name = "ask.sql.enabled", havingValue = "true")
public class AskSqlDataSourceConfig {

  @Bean
  @Primary
  @ConfigurationProperties("spring.datasource")
  public DataSourceProperties primaryDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  @Primary
  @ConfigurationProperties("spring.datasource.hikari")
  public HikariDataSource dataSource(DataSourceProperties primaryDataSourceProperties) {
    return primaryDataSourceProperties
        .initializeDataSourceBuilder()
        .type(HikariDataSource.class)
        .build();
  }

  @Bean(destroyMethod = "close")
  public DataSource askSqlDataSource(AskSqlProperties properties) {
    HikariDataSource ds = new HikariDataSource();
    ds.setPoolName("askSqlDataSource");
    ds.setJdbcUrl(properties.getUrl());
    ds.setUsername(properties.getUsername());
    ds.setPassword(properties.getPassword());
    ds.setMaximumPoolSize(2);
    ds.setMinimumIdle(0);
    ds.setReadOnly(true);
    return ds;
  }
}
