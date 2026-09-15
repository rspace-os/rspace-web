package com.researchspace.core.util.jsonserialisers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.LocalDate;
import org.apache.commons.lang3.StringUtils;

/**
 * Converts a UTC Date format string yyyy-MM-dd to a java.time.LocalDate if field="expiry date"
 *
 * <p>If expiryDate is set to null or empty string, returns NULL_DATE to indicate that this property
 * is explicly set to null, rather than merely omitted
 */
public class LocalDateDeserialiser extends StdDeserializer<LocalDate> {

  public static final LocalDate NULL_DATE = LocalDate.MIN;

  protected LocalDateDeserialiser(Class<LocalDate> vc) {
    super(vc);
    // TODO Auto-generated constructor stub
  }

  public LocalDateDeserialiser() {
    this(null);
  }

  /** */
  private static final long serialVersionUID = 1L;

  @Override
  public LocalDate deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException, JsonProcessingException {
    if (p.getCurrentName().equals("expiryDate")) {
      if (StringUtils.isBlank(p.getValueAsString())) {
        return NULL_DATE;
      } else {
        return LocalDate.parse(p.getValueAsString());
      }
    }
    return null;
  }

  public LocalDate getNullValue(DeserializationContext ctxt) throws JsonMappingException {
    return NULL_DATE;
  }
}
