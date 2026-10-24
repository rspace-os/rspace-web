package com.researchspace.model.booking;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/** Stores a complete weekly opening schedule as canonical JSON. */
@Converter
public class BookingOpeningHoursConverter
    implements AttributeConverter<List<BookingOpeningHoursEntry>, String> {

  @Override
  public String convertToDatabaseColumn(List<BookingOpeningHoursEntry> openingHours) {
    return openingHours == null ? null : BookingOpeningHoursCodec.openingHoursJson(openingHours);
  }

  @Override
  public List<BookingOpeningHoursEntry> convertToEntityAttribute(String json) {
    return json == null ? null : BookingOpeningHoursCodec.openingHours(json);
  }
}
