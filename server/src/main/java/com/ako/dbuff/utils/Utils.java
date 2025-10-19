package com.ako.dbuff.utils;

public class Utils {

  public static <T> T nvl(T value, T defaultValue) {
    return value == null ? defaultValue : value;
  }
}
