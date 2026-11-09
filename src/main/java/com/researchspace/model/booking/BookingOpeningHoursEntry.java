package com.researchspace.model.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** One ISO weekday's opening interval in the booking configuration's scheduling timezone. */
public record BookingOpeningHoursEntry(
    @Min(1) @Max(7) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int dayOfWeek,
    @NotNull
        @Pattern(regexp = BookingOpeningHoursCodec.WALL_TIME_PATTERN)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String start,
    @NotNull
        @Pattern(regexp = BookingOpeningHoursCodec.CLOSING_TIME_PATTERN)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String end) {}
