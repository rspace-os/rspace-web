package com.researchspace.model.booking;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum BookingConfigurationState {
  ACTIVE,
  ARCHIVED
}
