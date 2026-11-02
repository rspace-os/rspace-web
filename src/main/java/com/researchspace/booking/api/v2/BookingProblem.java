package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.controller.ApiV2Problem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Scheduling problem extensions; the standard members are defined by Problem. */
@Schema(name = "BookingProblem", allOf = ApiV2Problem.class)
public record BookingProblem(
    @Schema(
            nullable = true,
            description =
                "The existing event that blocks the requested booking interval. Present on"
                    + " errors.api.v2.booking.overlap and errors.api.v2.booking.buffer.")
        BookingConflict conflict,
    @Schema(
            nullable = true,
            description =
                "Minutes that must stay free before another event. Present on"
                    + " errors.api.v2.booking.buffer.")
        Long bufferBeforeMinutes,
    @Schema(
            nullable = true,
            description =
                "Minutes that must stay free after another event. Present on"
                    + " errors.api.v2.booking.buffer.")
        Long bufferAfterMinutes,
    @Schema(
            nullable = true,
            description =
                "The bookable item's maximum booking duration. Present on"
                    + " errors.api.v2.booking.maximumDuration.")
        Long maximumDurationMinutes) {
  /**
   * Public, non-private summary of the event that blocks a booking interval. It deliberately has no
   * purpose, requester, or other detail that the caller might not be allowed to read.
   */
  public record BookingConflict(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              allowableValues = {"BOOKING", "MAINTENANCE"})
          String kind,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              format = "date-time",
              example = "2026-09-28T08:00:00Z")
          String start,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              format = "date-time",
              example = "2026-09-28T10:00:00Z")
          String end) {

    public BookingConflict {
      Objects.requireNonNull(kind, "Conflict kind");
      Objects.requireNonNull(start, "Conflict start");
      Objects.requireNonNull(end, "Conflict end");
    }
  }

  /** Non-null RFC 9457 members, flattened into the standard problem body. */
  public Map<String, Object> extensions() {
    Map<String, Object> values = new LinkedHashMap<>();
    if (conflict != null) values.put("conflict", conflict);
    if (bufferBeforeMinutes != null) values.put("bufferBeforeMinutes", bufferBeforeMinutes);
    if (bufferAfterMinutes != null) values.put("bufferAfterMinutes", bufferAfterMinutes);
    if (maximumDurationMinutes != null)
      values.put("maximumDurationMinutes", maximumDurationMinutes);
    return Map.copyOf(values);
  }

  public static Map<String, Object> bookingConflict(BookingConflict conflict) {
    return new BookingProblem(Objects.requireNonNull(conflict), null, null, null).extensions();
  }

  public static Map<String, Object> bookingBuffer(
      BookingConflict conflict, long before, long after) {
    return new BookingProblem(Objects.requireNonNull(conflict), before, after, null).extensions();
  }

  public static Map<String, Object> maximumDuration(long minutes) {
    return new BookingProblem(null, null, null, minutes).extensions();
  }
}
