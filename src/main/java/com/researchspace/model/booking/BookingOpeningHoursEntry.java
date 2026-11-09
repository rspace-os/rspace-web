package com.researchspace.model.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * One ISO weekday's opening interval in the booking configuration's scheduling timezone. Its
 * class-level schema publishes it as one component, which booking-configuration write schemas
 * reference for their declared opening-hours rows.
 */
@Schema(description = "One ISO weekday's opening interval in the scheduling timezone.")
public record BookingOpeningHoursEntry(
    @Min(1)
        @Max(7)
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "ISO weekday, Monday = 1.",
            example = "1")
        int dayOfWeek,
    @NotNull
        @Pattern(regexp = BookingOpeningHoursCodec.WALL_TIME_PATTERN)
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 5,
            description = "Opening time in HH:mm.",
            example = "09:00")
        String start,
    @NotNull
        @Pattern(regexp = BookingOpeningHoursCodec.CLOSING_TIME_PATTERN)
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 5,
            description = "Exclusive closing time in HH:mm; 24:00 is next midnight.",
            example = "24:00")
        String end) {}
