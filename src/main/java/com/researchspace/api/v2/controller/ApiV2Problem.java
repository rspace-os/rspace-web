package com.researchspace.api.v2.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * RFC 9457 problem details using the default {@code about:blank} type.
 *
 * <p>The optional members after {@code invalidParams} are RFC 9457 extension members. Each is
 * present only on the problems that document it, so clients can rely on its absence elsewhere.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiV2Problem(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(nullable = true) String detail,
    @Schema(nullable = true) List<InvalidParam> invalidParams,
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

  public ApiV2Problem {
    Objects.requireNonNull(title, "Problem title");
    Objects.requireNonNull(code, "Problem code");
  }

  public ApiV2Problem(
      String title, int status, String code, String detail, List<InvalidParam> invalidParams) {
    this(title, status, code, detail, invalidParams, Extensions.NONE);
  }

  private ApiV2Problem(
      String title,
      int status,
      String code,
      String detail,
      List<InvalidParam> invalidParams,
      Extensions extensions) {
    this(
        title,
        status,
        code,
        detail,
        invalidParams,
        extensions.conflict(),
        extensions.bufferBeforeMinutes(),
        extensions.bufferAfterMinutes(),
        extensions.maximumDurationMinutes());
  }

  public record InvalidParam(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason) {}

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

  /** The optional extension members of one problem. */
  public record Extensions(
      BookingConflict conflict,
      Long bufferBeforeMinutes,
      Long bufferAfterMinutes,
      Long maximumDurationMinutes) {

    public static final Extensions NONE = new Extensions(null, null, null, null);

    public static Extensions bookingConflict(BookingConflict conflict) {
      return new Extensions(Objects.requireNonNull(conflict, "Conflict"), null, null, null);
    }

    public static Extensions bookingBuffer(
        BookingConflict conflict, long bufferBeforeMinutes, long bufferAfterMinutes) {
      return new Extensions(
          Objects.requireNonNull(conflict, "Conflict"),
          bufferBeforeMinutes,
          bufferAfterMinutes,
          null);
    }

    public static Extensions maximumDuration(long maximumDurationMinutes) {
      return new Extensions(null, null, null, maximumDurationMinutes);
    }
  }

  public static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

  public static ResponseEntity<ApiV2Problem> response(
      HttpStatus status, String title, String code, String detail) {
    return response(status, title, code, detail, null, Extensions.NONE);
  }

  public static ResponseEntity<ApiV2Problem> response(
      HttpStatus status,
      String title,
      String code,
      String detail,
      List<InvalidParam> invalidParams) {
    return response(status, title, code, detail, invalidParams, Extensions.NONE);
  }

  public static ResponseEntity<ApiV2Problem> response(
      HttpStatus status, String title, String code, String detail, Extensions extensions) {
    return response(status, title, code, detail, null, extensions);
  }

  private static ResponseEntity<ApiV2Problem> response(
      HttpStatus status,
      String title,
      String code,
      String detail,
      List<InvalidParam> invalidParams,
      Extensions extensions) {
    return ResponseEntity.status(status)
        .contentType(PROBLEM_JSON)
        .body(
            new ApiV2Problem(
                title,
                status.value(),
                code,
                detail,
                invalidParams,
                Objects.requireNonNull(extensions, "Problem extensions")));
  }
}
