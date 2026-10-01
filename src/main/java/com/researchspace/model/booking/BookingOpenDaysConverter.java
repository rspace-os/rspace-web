package com.researchspace.model.booking;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/** Stores ISO open weekdays as canonical JSON; null only in Envers deletion revisions. */
@Converter
public class BookingOpenDaysConverter implements AttributeConverter<List<Integer>, String> {

  @Override
  public String convertToDatabaseColumn(List<Integer> days) {
    return days == null ? null : BookingOpeningHoursCodec.openDaysJson(days);
  }

  @Override
  public List<Integer> convertToEntityAttribute(String json) {
    return json == null ? null : BookingOpeningHoursCodec.openDays(json);
  }
}
