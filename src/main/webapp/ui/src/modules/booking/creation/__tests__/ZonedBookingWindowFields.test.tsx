import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { MAX_BOOKING_DURATION_MINUTES } from "@/modules/booking/domain/bookingOpeningHours";
import type { BookingWindowDraft } from "@/modules/booking/domain/bookingTime";
import { validateBookingWindow, ZonedBookingWindowFields } from "../ZonedBookingWindowFields";

describe("ZonedBookingWindowFields", () => {
  it("uses one date for both endpoints in compact mode", () => {
    const onChange = vi.fn();
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingHours={[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))}
        density="compact"
        value={{
          startDate: "2026-08-17",
          startTime: "09:00",
          endDate: "2026-08-17",
          endTime: "10:00",
        }}
        onChange={onChange}
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

  it("keeps separate endpoint dates in compact mode for a multiday window", () => {
    const onChange = vi.fn();
    const value: BookingWindowDraft = {
      startDate: "2026-08-17",
      startTime: "23:00",
      endDate: "2026-08-19",
      endTime: "23:00",
    };
    render(
      <ZonedBookingWindowFields
        displayTimezone="UTC"
        schedulingTimezone="UTC"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingHours={[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))}
        density="compact"
        value={value}
        onChange={onChange}
      />,
    );

    const startDate = screen.getByLabelText("booking:bookings.form.startDate");
    expect(startDate).toHaveValue("2026-08-17");
    expect(screen.getByLabelText("booking:bookings.form.endDate")).toHaveValue("2026-08-19");

    fireEvent.change(startDate, { target: { value: "2026-08-18" } });

    expect(onChange).toHaveBeenLastCalledWith({ ...value, startDate: "2026-08-18" });
  });

  it.each([
    ["Europe/Berlin", "America/New_York", "15:00", "16:00", "2026-08-17T13:00:00Z", "2026-08-17T14:00:00Z"],
    ["America/New_York", "Europe/Berlin", "03:00", "04:00", "2026-08-17T07:00:00Z", "2026-08-17T08:00:00Z"],
  ])(
    "resolves %s display input to instants and validates policy in %s",
    (displayTimezone, schedulingTimezone, startTime, endTime, start, end) => {
      render(
        <ZonedBookingWindowFields
          displayTimezone={displayTimezone}
          schedulingTimezone={schedulingTimezone}
          slotGranularityMinutes={5}
          maxBookingDurationMinutes={0}
          openingHours={[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "17:00" }))}
          value={{ startDate: "2026-08-17", startTime, endDate: "2026-08-17", endTime }}
          onChange={vi.fn()}
        />,
      );

      expect(
        validateBookingWindow({ startDate: "2026-08-17", startTime, endDate: "2026-08-17", endTime }, displayTimezone, {
          schedulingTimezone,
          slotGranularityMinutes: 5,
          maxBookingDurationMinutes: 0,
          openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "17:00" })),
        }).window,
      ).toEqual({ start, end });
      expect(screen.getByText("booking:bookings.form.timezone")).toBeVisible();
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
        openingHours={[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))}
        value={{
          startDate: "2026-08-17",
          startTime: "09:02",
          endDate: "2026-08-17",
          endTime: "10:02",
        }}
        onChange={onChange}
      />,
    );

    fireEvent.blur(screen.getByLabelText("booking:bookings.form.startTime"));

    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ startTime: "09:05", startOccurrence: undefined }),
    );
  });

  it("preserves unchanged original endpoints while snapping only the edited endpoint", async () => {
    const user = userEvent.setup();
    const originalValue: BookingWindowDraft = {
      startDate: "2026-08-17",
      startTime: "09:02",
      endDate: "2026-08-17",
      endTime: "10:02",
    };
    function Form() {
      const [value, setValue] = useState<BookingWindowDraft>(originalValue);
      return (
        <ZonedBookingWindowFields
          displayTimezone="Europe/Berlin"
          schedulingTimezone="Asia/Kathmandu"
          slotGranularityMinutes={10}
          maxBookingDurationMinutes={0}
          openingHours={[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))}
          originalValue={originalValue}
          value={value}
          onChange={setValue}
        />
      );
    }

    render(<Form />);
    const startTime = screen.getByLabelText("booking:bookings.form.startTime");
    const endTime = screen.getByLabelText("booking:bookings.form.endTime");

    await user.click(startTime);
    await user.tab();
    expect(startTime).toHaveValue("09:02");

    await user.click(endTime);
    await user.tab();
    expect(endTime).toHaveValue("10:02");

    await user.clear(startTime);
    await user.type(startTime, "09:03");
    await user.tab();
    expect(startTime).toHaveValue("09:05");

    await user.click(endTime);
    await user.tab();
    expect(endTime).toHaveValue("10:02");
  });

  it("allows an always-open interval across the scheduling timezone's local date boundary", () => {
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="America/New_York"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingHours={[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))}
        value={{
          startDate: "2026-08-18",
          startTime: "05:00",
          endDate: "2026-08-18",
          endTime: "07:00",
        }}
        onChange={vi.fn()}
      />,
    );

    expect(screen.queryByText("booking:bookings.errors.openingHours")).not.toBeInTheDocument();
    expect(
      validateBookingWindow(
        { startDate: "2026-08-18", startTime: "05:00", endDate: "2026-08-18", endTime: "07:00" },
        "Europe/Berlin",
        {
          schedulingTimezone: "America/New_York",
          slotGranularityMinutes: 5,
          maxBookingDurationMinutes: 0,
          openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
        },
      ).window,
    ).toEqual({ start: "2026-08-18T03:00:00Z", end: "2026-08-18T05:00:00Z" });
  });

  it.each([
    ["00:00", "24:00", 0, true],
    ["09:00", "17:00", 0, false],
    ["00:00", "24:00", 60, false],
  ])(
    "validates multiday windows against %s–%s and duration %i",
    (openingStart, openingEnd, maxBookingDurationMinutes, valid) => {
      render(
        <ZonedBookingWindowFields
          displayTimezone="UTC"
          schedulingTimezone="UTC"
          slotGranularityMinutes={5}
          openingHours={[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: openingStart, end: openingEnd }))}
          maxBookingDurationMinutes={maxBookingDurationMinutes}
          value={{ startDate: "2026-10-19", startTime: "10:00", endDate: "2026-10-21", endTime: "11:00" }}
          onChange={vi.fn()}
        />,
      );
      expect(
        validateBookingWindow(
          { startDate: "2026-10-19", startTime: "10:00", endDate: "2026-10-21", endTime: "11:00" },
          "UTC",
          {
            schedulingTimezone: "UTC",
            slotGranularityMinutes: 5,
            openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({
              dayOfWeek,
              start: openingStart,
              end: openingEnd,
            })),
            maxBookingDurationMinutes,
          },
        ).window,
      ).toEqual(valid ? { start: "2026-10-19T10:00:00Z", end: "2026-10-21T11:00:00Z" } : undefined);
    },
  );

  it("allows an exclusive endpoint at midnight when the day is open through 24:00", () => {
    const validate = (endTime: string) =>
      validateBookingWindow({ startDate: "2026-08-17", startTime: "23:00", endDate: "2026-08-18", endTime }, "UTC", {
        schedulingTimezone: "UTC",
        slotGranularityMinutes: 1,
        maxBookingDurationMinutes: 0,
        openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "24:00" })),
      });

    expect(validate("00:00")).toMatchObject({
      openingInvalid: false,
      window: { start: "2026-08-17T23:00:00Z", end: "2026-08-18T00:00:00Z" },
    });
    expect(validate("00:01")).toMatchObject({ openingInvalid: true, window: undefined });
  });

  it("blocks a nonexistent spring-forward time", () => {
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingHours={[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))}
        value={{
          startDate: "2026-03-29",
          startTime: "02:30",
          endDate: "2026-03-29",
          endTime: "04:00",
        }}
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByRole("alert", { name: "" })).toHaveTextContent("booking:bookings.errors.nonexistentTime");
  });

  it("requires and emits an explicit fall-back occurrence", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingHours={[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))}
        value={{
          startDate: "2026-10-25",
          startTime: "02:30",
          endDate: "2026-10-25",
          endTime: "04:00",
        }}
        onChange={onChange}
      />,
    );

    expect(screen.getByText("booking:bookings.errors.occurrenceRequired")).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "booking:bookings.form.laterOccurrence" }));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ startOccurrence: "later" }));
  });

  it("preserves an unchanged fall-back occurrence when its endpoint blurs", async () => {
    const user = userEvent.setup();
    const originalValue: BookingWindowDraft = {
      startDate: "2026-10-25",
      startTime: "02:32",
      startOccurrence: "later",
      endDate: "2026-10-25",
      endTime: "03:32",
    };
    const onChange = vi.fn();
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={10}
        maxBookingDurationMinutes={0}
        openingHours={[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))}
        originalValue={originalValue}
        value={originalValue}
        onChange={onChange}
      />,
    );

    const startTime = screen.getByLabelText("booking:bookings.form.startTime");
    expect(screen.getByRole("radio", { name: "booking:bookings.form.laterOccurrence" })).toBeChecked();

    await user.click(startTime);
    await user.tab();

    expect(onChange).not.toHaveBeenCalled();
  });

  it("uses elapsed instants for the maximum duration", () => {
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={1}
        maxBookingDurationMinutes={60}
        openingHours={[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))}
        value={{
          startDate: "2026-10-25",
          startTime: "02:30",
          startOccurrence: "earlier",
          endDate: "2026-10-25",
          endTime: "02:31",
          endOccurrence: "later",
        }}
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByText("booking:bookings.errors.maximumDuration")).toBeVisible();
    expect(
      validateBookingWindow(
        {
          startDate: "2026-10-25",
          startTime: "02:30",
          startOccurrence: "earlier",
          endDate: "2026-10-25",
          endTime: "02:31",
          endOccurrence: "later",
        },
        "Europe/Berlin",
        {
          schedulingTimezone: "Europe/Berlin",
          slotGranularityMinutes: 1,
          maxBookingDurationMinutes: 60,
          openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
        },
      ).window,
    ).toBeUndefined();
  });

  it("accepts a booking exactly at the maximum elapsed duration", () => {
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={1}
        maxBookingDurationMinutes={60}
        openingHours={[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))}
        value={{
          startDate: "2026-10-25",
          startTime: "02:30",
          startOccurrence: "earlier",
          endDate: "2026-10-25",
          endTime: "02:30",
          endOccurrence: "later",
        }}
        onChange={vi.fn()}
      />,
    );

    expect(screen.queryByText("booking:bookings.errors.maximumDuration")).not.toBeInTheDocument();
    expect(
      validateBookingWindow(
        {
          startDate: "2026-10-25",
          startTime: "02:30",
          startOccurrence: "earlier",
          endDate: "2026-10-25",
          endTime: "02:30",
          endOccurrence: "later",
        },
        "Europe/Berlin",
        {
          schedulingTimezone: "Europe/Berlin",
          slotGranularityMinutes: 1,
          maxBookingDurationMinutes: 60,
          openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
        },
      ).window,
    ).toEqual({
      start: "2026-10-25T00:30:00Z",
      end: "2026-10-25T01:30:00Z",
    });
  });
});
describe("independent weekday booking windows", () => {
  const policy = {
    schedulingTimezone: "UTC",
    slotGranularityMinutes: 5,
    maxBookingDurationMinutes: 0,
    openingHours: [
      { dayOfWeek: 1, start: "08:00", end: "24:00" },
      { dayOfWeek: 2, start: "00:00", end: "16:00" },
      { dayOfWeek: 4, start: "10:00", end: "18:00" },
    ],
  };

  it.each([
    {
      name: "touching Monday and Tuesday",
      startDate: "2026-08-17",
      startTime: "23:00",
      endDate: "2026-08-18",
      endTime: "10:00",
      valid: true,
    },
    {
      name: "Tuesday's exclusive closing boundary",
      startDate: "2026-08-18",
      startTime: "15:00",
      endDate: "2026-08-18",
      endTime: "16:00",
      valid: true,
    },
    {
      name: "time beyond Tuesday's closing boundary",
      startDate: "2026-08-18",
      startTime: "15:00",
      endDate: "2026-08-18",
      endTime: "16:05",
      valid: false,
    },
    {
      name: "a closed Wednesday",
      startDate: "2026-08-19",
      startTime: "10:00",
      endDate: "2026-08-19",
      endTime: "11:00",
      valid: false,
    },
    {
      name: "Thursday's different start",
      startDate: "2026-08-20",
      startTime: "09:00",
      endDate: "2026-08-20",
      endTime: "11:00",
      valid: false,
    },
    {
      name: "a window crossing a closed weekday",
      startDate: "2026-08-18",
      startTime: "15:00",
      endDate: "2026-08-20",
      endTime: "11:00",
      valid: false,
    },
  ])("checks $name", ({ name: _name, valid, ...draft }) => {
    const result = validateBookingWindow(draft, "UTC", policy);
    expect(result.openingInvalid).toBe(!valid);
    expect(Boolean(result.window)).toBe(valid);
  });

  it.each([
    {
      limit: "configured",
      maxBookingDurationMinutes: 60,
      value: { startDate: "2026-06-01", startTime: "09:00", endDate: "2026-06-01", endTime: "11:00" },
    },
    {
      limit: "absolute with no configured maximum",
      maxBookingDurationMinutes: 0,
      value: { startDate: "2026-06-01", startTime: "00:00", endDate: "2027-06-02", endTime: "00:01" },
    },
    {
      limit: "absolute below a larger configured maximum",
      maxBookingDurationMinutes: MAX_BOOKING_DURATION_MINUTES + 1,
      value: { startDate: "2026-06-01", startTime: "00:00", endDate: "2027-06-02", endTime: "00:01" },
    },
  ])(
    "shows ordinary duration validation for a $limit violation before checking opening hours",
    ({ maxBookingDurationMinutes, value }) => {
      const openingHours = [{ dayOfWeek: 1, start: "09:00", end: "10:00" }];
      const result = validateBookingWindow(value, "UTC", {
        schedulingTimezone: "UTC",
        slotGranularityMinutes: 1,
        maxBookingDurationMinutes,
        openingHours,
      });
      expect(result.maximumDurationInvalid).toBe(true);
      expect(result.openingInvalid).toBe(false);
      expect(result.window).toBeUndefined();

      render(
        <ZonedBookingWindowFields
          displayTimezone="UTC"
          schedulingTimezone="UTC"
          slotGranularityMinutes={1}
          maxBookingDurationMinutes={maxBookingDurationMinutes}
          openingHours={openingHours}
          value={value}
          onChange={vi.fn()}
        />,
      );

      expect(screen.getByText("booking:bookings.errors.maximumDuration")).toBeVisible();
    },
  );

  it("retains the maintenance bypass and the edit policy-mismatch allowance", () => {
    const draft = { startDate: "2026-08-19", startTime: "10:00", endDate: "2026-08-19", endTime: "11:00" };
    expect(validateBookingWindow(draft, "UTC", { ...policy, enforceOpeningHours: false })).toMatchObject({
      openingInvalid: false,
      window: { start: "2026-08-19T10:00:00Z", end: "2026-08-19T11:00:00Z" },
    });
    expect(validateBookingWindow(draft, "UTC", { ...policy, allowPolicyMismatch: true })).toMatchObject({
      openingInvalid: true,
      window: { start: "2026-08-19T10:00:00Z", end: "2026-08-19T11:00:00Z" },
    });
  });
});
