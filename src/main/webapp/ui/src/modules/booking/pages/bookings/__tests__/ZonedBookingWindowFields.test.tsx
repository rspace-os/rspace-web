import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ZonedBookingWindowFields } from "../ZonedBookingWindowFields";

describe("ZonedBookingWindowFields", () => {
  it("blocks a nonexistent spring-forward time", () => {
    render(
      <ZonedBookingWindowFields
        timezone="Europe/Berlin"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingStart="00:00"
        openingEnd="24:00"
        value={{
          startDate: "2026-03-29",
          startTime: "02:30",
          endDate: "2026-03-29",
          endTime: "04:00",
        }}
        onChange={vi.fn()}
        onResolved={vi.fn()}
      />,
    );

    expect(screen.getByRole("alert", { name: "" })).toHaveTextContent("booking:bookings.errors.nonexistentTime");
  });

  it("requires and emits an explicit fall-back occurrence", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <ZonedBookingWindowFields
        timezone="Europe/Berlin"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingStart="00:00"
        openingEnd="24:00"
        value={{
          startDate: "2026-10-25",
          startTime: "02:30",
          endDate: "2026-10-25",
          endTime: "04:00",
        }}
        onChange={onChange}
        onResolved={vi.fn()}
      />,
    );

    expect(screen.getByText("booking:bookings.errors.occurrenceRequired")).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "booking:bookings.form.laterOccurrence" }));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ startOccurrence: "later" }));
  });

  it("uses elapsed instants for the maximum duration", () => {
    const onResolved = vi.fn();
    render(
      <ZonedBookingWindowFields
        timezone="Europe/Berlin"
        slotGranularityMinutes={1}
        maxBookingDurationMinutes={60}
        openingStart="00:00"
        openingEnd="24:00"
        value={{
          startDate: "2026-10-25",
          startTime: "02:30",
          startOccurrence: "earlier",
          endDate: "2026-10-25",
          endTime: "02:31",
          endOccurrence: "later",
        }}
        onChange={vi.fn()}
        onResolved={onResolved}
      />,
    );

    expect(screen.getByText("booking:bookings.errors.maximumDuration")).toBeVisible();
    expect(onResolved).toHaveBeenLastCalledWith(undefined);
  });

  it("accepts a booking exactly at the maximum elapsed duration", () => {
    const onResolved = vi.fn();
    render(
      <ZonedBookingWindowFields
        timezone="Europe/Berlin"
        slotGranularityMinutes={1}
        maxBookingDurationMinutes={60}
        openingStart="00:00"
        openingEnd="24:00"
        value={{
          startDate: "2026-10-25",
          startTime: "02:30",
          startOccurrence: "earlier",
          endDate: "2026-10-25",
          endTime: "02:30",
          endOccurrence: "later",
        }}
        onChange={vi.fn()}
        onResolved={onResolved}
      />,
    );

    expect(screen.queryByText("booking:bookings.errors.maximumDuration")).not.toBeInTheDocument();
    expect(onResolved).toHaveBeenLastCalledWith({
      start: "2026-10-25T00:30:00Z",
      end: "2026-10-25T01:30:00Z",
    });
  });
});
