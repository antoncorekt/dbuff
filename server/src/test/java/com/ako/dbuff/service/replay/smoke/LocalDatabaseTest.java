package com.ako.dbuff.service.replay.smoke;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDatabaseTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:postgresql://localhost:5544/dbuff",
        "jdbc:postgresql://127.0.0.1/dbuff?ssl=false",
        "jdbc:postgresql://[::1]:5432/dbuff"
      })
  void aLocalHostIsAccepted(String url) {
    assertThat(LocalDatabase.isLocal(url)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:postgresql://db.example.com/dbuff?host=localhost",
        "jdbc:postgresql://localhost@db.example.com/dbuff",
        "jdbc:postgresql://localhost:5432@db.example.com/dbuff",
        "jdbc:postgresql://db.example.com/localhost",
        "jdbc:postgresql://localhost.example.com/dbuff",
        "jdbc:postgresql://localhost,db.example.com/dbuff",
        "jdbc:postgresql:///dbuff",
        "postgresql://localhost/dbuff",
        "not a url"
      })
  void anyOtherHostOrAnUnreadableUrlIsRefused(String url) {
    assertThat(LocalDatabase.isLocal(url)).isFalse();
  }

  @Test
  void noUrlIsRefused() {
    assertThat(LocalDatabase.isLocal(null)).isFalse();
  }
}
