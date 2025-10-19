package com.ako.dbuff.dao.model.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashMap;
import java.util.Map;

@Converter
public class ConstantConverter implements AttributeConverter<Map<String, Object>, String> {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(Map<String, Object> stringObjectMap) {
    try {
      return objectMapper.writeValueAsString(stringObjectMap);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Map<String, Object> convertToEntityAttribute(String s) {
    TypeReference<HashMap<String, Object>> typeRef =
        new TypeReference<HashMap<String, Object>>() {};
    try {
      return objectMapper.readValue(s, typeRef);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
