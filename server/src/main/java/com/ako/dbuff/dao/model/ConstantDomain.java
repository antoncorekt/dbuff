package com.ako.dbuff.dao.model;

import com.ako.dbuff.dao.model.converter.ConstantConverter;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
public class ConstantDomain {

  @Id
  @Column(name = "constant_name")
  private String constantName;

  @Type(JsonBinaryType.class)
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "constant_value", columnDefinition = "jsonb")
  @Convert(converter = ConstantConverter.class)
  private Map<String, Object> constantValue;
}
