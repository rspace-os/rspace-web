import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ZonedBookingWindowFields } from "../ZonedBookingWindowFields";

describe("ZonedBookingWindowFields", () => {
  it("uses one date for both endpoints in compact mode", () => {
    const onChange = vi.fn();
    render(
      <ZonedBookingWindowFields
        timezone="Europe/Berlin"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingStart="00:00"
        openingEnd="24:00"
        density="compact"
        value={{
          startDate: "2026-08-17",
          startTime: "09:00",
          endDate: "2026-08-17",
          endTime: "10:00",
        }}
        onChange={onChange}
        onResolved={vi.fn()}
      />,
    );

    const date = screen.getByLabelText("booking:bookings.form.date");
    expect(screen.getAllByDisplayValue("2026-08-17")).toHaveLength(1);
    expect(screen.getByLabelText("booking:bookings.form.startTime")).toBeVisible();
    expect(screen.getByLabelText("booking:bookings.form.endTime")).toBeVisible();

    fireEvent.change(date, { target: { value: "2026-08-18" } });

    expect(onChange).toHaveBeenLastCalledWith({
      startDate: "2026-08-18",
      startTime: "09:00",
      startOccurrence: undefined,
      endDate: "2026-08-18",
      endTime: "10:00",
      endOccurrence: undefined,
    });
  });

  it.each([
    ["Europe/Berlin", "America/New_York", "15:00", "16:00", "2026-08-17T13:00:00Z", "2026-08-17T14:00:00Z"],
    ["America/New_York", "Europe/Berlin", "03:00", "04:00", "2026-08-17T07:00:00Z", "2026-08-17T08:00:00Z"],
  ])(
    "resolves %s display input to instants and validates policy in %s",
    (displayTimezone, schedulingTimezone, startTime, endTime, start, end) => {
      const onResolved = vi.fn();
      render(
        <ZonedBookingWindowFields
          displayTimezone={displayTimezone}
          schedulingTimezone={schedulingTimezone}
          slotGranularityMinutes={5}
          maxBookingDurationMinutes={0}
          openingStart="09:00"
          openingEnd="17:00"
          value={{ startDate: "2026-08-17", startTime, endDate: "2026-08-17", endTime }}
          onChange={vi.fn()}
          onResolved={onResolved}
        />,
      );

      expect(onResolved).toHaveBeenLastCalledWith({ start, end });
      expect(screen.queryByText("booking:bookings.form.timezone")).not.toBeInTheDocument();
      expect(screen.queryByText("booking:bookings.form.schedulingTimezone")).not.toBeInTheDocument();
      expect(screen.queryByText("booking:bookings.errors.openingHours")).not.toBeInTheDocument();
    },
  );

  it("snaps display times to the instrument timezone's interval grid", () => {
    const onChange = vi.fn();
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Asia/Kathmandu"
        slotGranularityMinutes={10}
        maxBookingDurationMinutes={0}
        openingStart="00:00"
        openingEnd="24:00"
        value={{
          startDate: "2026-08-17",
          startTime: "09:02",
          endDate: "2026-08-17",
          endTime: "10:02",
        }}
        onChange={onChange}
        onResolved={vi.fn()}
      />,
    );

    fireEvent.blur(screen.getByLabelText("booking:bookings.form.startTime"));

    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ startTime: "09:05", startOccurrence: undefined }),
    );
  });

  it("rejects an interval that crosses the scheduling timezone's local date boundary", () => {
    const onResolved = vi.fn();
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="America/New_York"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingStart="00:00"
        openingEnd="24:00"
        value={{
          startDate: "2026-08-18",
          startTime: "05:00",
          endDate: "2026-08-18",
          endTime: "07:00",
        }}
        onChange={vi.fn()}
        onResolved={onResolved}
      />,
    );

    expect(screen.getByText("booking:bookings.errors.openingHours")).toBeVisible();
    expect(onResolved).toHaveBeenLastCalledWith(undefined);
  });

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
