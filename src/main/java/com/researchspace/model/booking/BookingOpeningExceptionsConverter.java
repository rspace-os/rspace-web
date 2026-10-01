package com.researchspace.model.booking;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/** Stores per-weekday opening exceptions as canonical JSON; null only in Envers deletions. */
@Converter
public class BookingOpeningExceptionsConverter
    implements AttributeConverter<List<BookingOpeningException>, String> {

  @Override
  public String convertToDatabaseColumn(List<BookingOpeningException> exceptions) {
    return exceptions == null ? null : BookingOpeningHoursCodec.openingExceptionsJson(exceptions);
  }

  @Override
  public List<BookingOpeningException> convertToEntityAttribute(String json) {
    return json == null ? null : BookingOpeningHoursCodec.openingExceptions(json);
  }
}
