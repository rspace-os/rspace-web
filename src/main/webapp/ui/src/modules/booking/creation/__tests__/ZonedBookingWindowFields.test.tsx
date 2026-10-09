import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { type ComponentProps, useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import {
  ALL_ISO_WEEKDAYS,
  MAX_BOOKING_DURATION_MINUTES,
  type OpeningSchedule,
} from "@/modules/booking/domain/bookingOpeningHours";
import type { BookingWindowDraft } from "@/modules/booking/domain/bookingTime";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import { validateBookingWindow, ZonedBookingWindowFields } from "../ZonedBookingWindowFields";

function StatefulFields({
  initial,
  ...props
}: { initial: BookingWindowDraft } & Omit<ComponentProps<typeof ZonedBookingWindowFields>, "value" | "onChange">) {
  const [value, setValue] = useState(initial);
  return <ZonedBookingWindowFields {...props} value={value} onChange={setValue} />;
}

describe("ZonedBookingWindowFields", () => {
  it.each(["compact", "comfortable"] as const)(
    "keeps the %s date input focused while its date is replaced",
    async (density) => {
      const user = userEvent.setup();
      function Form() {
        const [value, setValue] = useState<BookingWindowDraft>({
          startDate: "2026-08-17",
          startTime: "09:00",
          endDate: "2026-08-17",
          endTime: "10:00",
        });
        return (
          <ZonedBookingWindowFields
            displayTimezone="Europe/Berlin"
            schedulingTimezone="America/New_York"
            slotGranularityMinutes={5}
            maxBookingDurationMinutes={0}
            openingStart="00:00"
            openingEnd="24:00"
            openDays={ALL_ISO_WEEKDAYS}
            openingExceptions={[]}
            density={density}
            value={value}
            onChange={setValue}
          />
        );
      }

      render(<Form />);
      const date = screen.getByLabelText(
        density === "compact" ? "booking:bookings.form.date" : "booking:bookings.form.startDate",
      );
      await user.click(date);
      await user.clear(date);
      expect(date).toHaveFocus();
      await user.type(date, "2026-08-18");
      expect(date).toHaveFocus();
      expect(date).toHaveValue("2026-08-18");
    },
  );

  it("uses one date for both endpoints in compact mode", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    function Form() {
      const [value, setValue] = useState<BookingWindowDraft>({
        startDate: "2026-08-17",
        startTime: "09:00",
        endDate: "2026-08-17",
        endTime: "10:00",
      });
      return (
        <ZonedBookingWindowFields
          timezone="Europe/Berlin"
          slotGranularityMinutes={5}
          maxBookingDurationMinutes={0}
          openingStart="00:00"
          openingEnd="24:00"
          openDays={ALL_ISO_WEEKDAYS}
          openingExceptions={[]}
          density="compact"
          value={value}
          onChange={(next) => {
            onChange(next);
            setValue(next);
          }}
        />
      );
    }
    render(<Form />);

    const date = screen.getByLabelText("booking:bookings.form.date");
    expect(screen.getAllByDisplayValue("2026-08-17")).toHaveLength(1);
    expect(screen.getByLabelText("booking:bookings.form.startTime")).toBeVisible();
    expect(screen.getByLabelText("booking:bookings.form.endTime")).toBeVisible();

    await user.clear(date);
    await user.type(date, "2026-08-18");

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
      render(
        <ZonedBookingWindowFields
          displayTimezone={displayTimezone}
          schedulingTimezone={schedulingTimezone}
          slotGranularityMinutes={5}
          maxBookingDurationMinutes={0}
          openingStart="09:00"
          openingEnd="17:00"
          openDays={ALL_ISO_WEEKDAYS}
          openingExceptions={[]}
          value={{ startDate: "2026-08-17", startTime, endDate: "2026-08-17", endTime }}
          onChange={vi.fn()}
        />,
      );

      expect(
        validateBookingWindow({ startDate: "2026-08-17", startTime, endDate: "2026-08-17", endTime }, displayTimezone, {
          schedulingTimezone,
          slotGranularityMinutes: 5,
          maxBookingDurationMinutes: 0,
          openingStart: "09:00",
          openingEnd: "17:00",
          openDays: ALL_ISO_WEEKDAYS,
          openingExceptions: [],
        }).window,
      ).toEqual({ start, end });
      expect(screen.queryByText("booking:bookings.form.schedulingTimezone")).not.toBeInTheDocument();
      expect(screen.queryByText("booking:bookings.errors.openingHours")).not.toBeInTheDocument();
    },
  );

  it("snaps display times to the instrument timezone's interval grid", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Asia/Kathmandu"
        slotGranularityMinutes={10}
        maxBookingDurationMinutes={0}
        openingStart="00:00"
        openingEnd="24:00"
        openDays={ALL_ISO_WEEKDAYS}
        openingExceptions={[]}
        value={{
          startDate: "2026-08-17",
          startTime: "09:02",
          endDate: "2026-08-17",
          endTime: "10:02",
        }}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByLabelText("booking:bookings.form.startTime"));
    await user.tab();

    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ startTime: "09:05", startOccurrence: undefined }),
    );
  });

  it.each(["comfortable", "compact"] as const)(
    "announces a %s snap under the field until the next edit",
    async (density) => {
      const user = userEvent.setup();
      render(
        <StatefulFields
          timezone="UTC"
          slotGranularityMinutes={15}
          maxBookingDurationMinutes={0}
          openingStart="00:00"
          openingEnd="24:00"
          openDays={ALL_ISO_WEEKDAYS}
          openingExceptions={[]}
          density={density}
          initial={{ startDate: "2026-08-17", startTime: "15:22", endDate: "2026-08-17", endTime: "16:00" }}
        />,
      );
      const startTime = screen.getByLabelText("booking:bookings.form.startTime");
      expect(screen.queryByRole("status")).not.toBeInTheDocument();

      await user.click(startTime);
      await user.tab();

      expect(startTime).toHaveValue("15:15");
      expect(screen.getByRole("status")).toHaveTextContent("booking:bookings.form.timeSnapped");
      expect(screen.getByRole("status").parentElement).toHaveAttribute("aria-live", "polite");

      await user.clear(startTime);
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    },
  );

  it("does not announce a time that is already on the increment", async () => {
    const user = userEvent.setup();
    render(
      <StatefulFields
        timezone="UTC"
        slotGranularityMinutes={15}
        maxBookingDurationMinutes={0}
        openingStart="00:00"
        openingEnd="24:00"
        openDays={ALL_ISO_WEEKDAYS}
        openingExceptions={[]}
        initial={{ startDate: "2026-08-17", startTime: "15:15", endDate: "2026-08-17", endTime: "16:00" }}
      />,
    );

    await user.click(screen.getByLabelText("booking:bookings.form.startTime"));
    await user.tab();

    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("describes the snap with the adjusted time in the app locale and the increment", async () => {
    const user = userEvent.setup();
    await renderWithRealI18n(
      <StatefulFields
        timezone="UTC"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingStart="00:00"
        openingEnd="24:00"
        openDays={ALL_ISO_WEEKDAYS}
        openingExceptions={[]}
        initial={{ startDate: "2026-08-17", startTime: "15:28", endDate: "2026-08-17", endTime: "16:00" }}
      />,
      {
        resources: { booking: { bookings: { form: { timeSnapped: bookingEnglish.bookings.form.timeSnapped } } } },
        defaultNS: "booking",
      },
    );
    const start = screen.getByRole("group", { name: "bookings.form.start" });

    await user.click(within(start).getByLabelText("bookings.form.startTime"));
    await user.tab();

    expect(within(start).getByRole("status")).toHaveTextContent(
      "Adjusted to 03:30 PM to match the 5-minute time increment.",
    );
  });

  it("allows an always-open interval across the scheduling timezone's local date boundary", () => {
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="America/New_York"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingStart="00:00"
        openingEnd="24:00"
        openDays={ALL_ISO_WEEKDAYS}
        openingExceptions={[]}
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
          openingStart: "00:00",
          openingEnd: "24:00",
          openDays: ALL_ISO_WEEKDAYS,
          openingExceptions: [],
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
          timezone="UTC"
          slotGranularityMinutes={5}
          openingStart={openingStart}
          openingEnd={openingEnd}
          openDays={ALL_ISO_WEEKDAYS}
          openingExceptions={[]}
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
            openingStart,
            openingEnd,
            openDays: ALL_ISO_WEEKDAYS,
            openingExceptions: [],
            maxBookingDurationMinutes,
          },
        ).window,
      ).toEqual(valid ? { start: "2026-10-19T10:00:00Z", end: "2026-10-21T11:00:00Z" } : undefined);
    },
  );

  it("blocks a nonexistent spring-forward time", () => {
    render(
      <ZonedBookingWindowFields
        timezone="Europe/Berlin"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingStart="00:00"
        openingEnd="24:00"
        openDays={ALL_ISO_WEEKDAYS}
        openingExceptions={[]}
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
        timezone="Europe/Berlin"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingStart="00:00"
        openingEnd="24:00"
        openDays={ALL_ISO_WEEKDAYS}
        openingExceptions={[]}
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

  it("describes the system limit, not an item maximum, for an item without its own limit", () => {
    render(
      <ZonedBookingWindowFields
        timezone="UTC"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingStart="00:00"
        openingEnd="24:00"
        openDays={ALL_ISO_WEEKDAYS}
        openingExceptions={[]}
        value={{ startDate: "2026-10-01", startTime: "00:00", endDate: "2027-10-02", endTime: "00:05" }}
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByText("booking:bookings.errors.duration")).toBeVisible();
    expect(screen.queryByText("booking:bookings.errors.maximumDurationLimit")).not.toBeInTheDocument();
  });

  it("uses elapsed instants for the maximum duration", () => {
    render(
      <ZonedBookingWindowFields
        timezone="Europe/Berlin"
        slotGranularityMinutes={1}
        maxBookingDurationMinutes={60}
        openingStart="00:00"
        openingEnd="24:00"
        openDays={ALL_ISO_WEEKDAYS}
        openingExceptions={[]}
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

    expect(screen.getByText("booking:bookings.errors.maximumDurationLimit")).toBeVisible();
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
          openingStart: "00:00",
          openingEnd: "24:00",
          openDays: ALL_ISO_WEEKDAYS,
          openingExceptions: [],
        },
      ).window,
    ).toBeUndefined();
  });

  it("names the maximum duration in words", async () => {
    await renderWithRealI18n(
      <ZonedBookingWindowFields
        timezone="UTC"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={120}
        openingStart="00:00"
        openingEnd="24:00"
        openDays={ALL_ISO_WEEKDAYS}
        openingExceptions={[]}
        value={{ startDate: "2026-08-17", startTime: "09:00", endDate: "2026-08-17", endTime: "11:05" }}
        onChange={vi.fn()}
      />,
      {
        resources: {
          booking: {
            bookings: { errors: { maximumDurationLimit: bookingEnglish.bookings.errors.maximumDurationLimit } },
          },
        },
        defaultNS: "booking",
      },
    );

    expect(screen.getByText("This booking exceeds the bookable item's maximum duration of 2 hours.")).toBeVisible();
  });

  it("accepts a booking exactly at the maximum elapsed duration", () => {
    render(
      <ZonedBookingWindowFields
        timezone="Europe/Berlin"
        slotGranularityMinutes={1}
        maxBookingDurationMinutes={60}
        openingStart="00:00"
        openingEnd="24:00"
        openDays={ALL_ISO_WEEKDAYS}
        openingExceptions={[]}
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

    expect(screen.queryByText("booking:bookings.errors.maximumDurationLimit")).not.toBeInTheDocument();
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
          openingStart: "00:00",
          openingEnd: "24:00",
          openDays: ALL_ISO_WEEKDAYS,
          openingExceptions: [],
        },
      ).window,
    ).toEqual({
      start: "2026-10-25T00:30:00Z",
      end: "2026-10-25T01:30:00Z",
    });
  });
});

describe("validateBookingWindow weekly opening hours", () => {
  // 2026-06-01 is a Monday.
  const window = (startDate: string, startTime: string, endDate: string, endTime: string) => ({
    startDate,
    startTime,
    endDate,
    endTime,
  });
  const validate = (
    draft: BookingWindowDraft,
    schedule: Partial<OpeningSchedule> = {},
    {
      displayTimezone = "UTC",
      schedulingTimezone = "UTC",
      ...policy
    }: {
      displayTimezone?: string;
      schedulingTimezone?: string;
      maxBookingDurationMinutes?: number;
      enforceOpeningHours?: boolean;
      allowPolicyMismatch?: boolean;
    } = {},
  ) =>
    validateBookingWindow(draft, displayTimezone, {
      schedulingTimezone,
      slotGranularityMinutes: 1,
      maxBookingDurationMinutes: 0,
      openingStart: "00:00",
      openingEnd: "24:00",
      openDays: ALL_ISO_WEEKDAYS,
      openingExceptions: [],
      ...schedule,
      ...policy,
    });
  const officeHours = {
    openingStart: "09:00",
    openingEnd: "17:00",
    openingExceptions: [{ dayOfWeek: 2, start: "10:00", end: "16:00" }],
  };

  it("uses a weekday exception instead of the shared hours", () => {
    expect(validate(window("2026-06-01", "09:30", "2026-06-01", "10:00"), officeHours).openingInvalid).toBe(false);
    const tuesday = validate(window("2026-06-02", "09:30", "2026-06-02", "10:00"), officeHours);
    expect(tuesday.openingInvalid).toBe(true);
    expect(tuesday.window).toBeUndefined();
  });

  it("lets a Monday-only all-day booking end at Tuesday midnight but not one minute later", () => {
    expect(validate(window("2026-06-01", "00:00", "2026-06-02", "00:00"), { openDays: [1] }).openingInvalid).toBe(
      false,
    );
    expect(validate(window("2026-06-01", "00:00", "2026-06-02", "00:01"), { openDays: [1] }).openingInvalid).toBe(true);
  });

  it("finds the weekday in the scheduling timezone, not the display timezone", () => {
    // Sunday 10:00 in Los Angeles is Monday 02:00 in Tokyo.
    const draft = window("2026-05-31", "10:00", "2026-05-31", "11:00");
    const zones = { displayTimezone: "America/Los_Angeles", schedulingTimezone: "Asia/Tokyo" };
    expect(validate(draft, { openDays: [1] }, zones).openingInvalid).toBe(false);
    expect(validate(draft, { openDays: [7] }, zones).openingInvalid).toBe(true);
  });

  it("rejects durations over the absolute limit before checking opening hours", () => {
    const exact = validate(window("2026-06-01", "00:00", "2027-06-02", "00:00"));
    expect(exact.maximumDurationInvalid).toBe(false);
    expect(exact.window).toBeDefined();

    const oneMinuteOver = validate(window("2026-06-01", "00:00", "2027-06-02", "00:01"));
    expect(oneMinuteOver.maximumDurationInvalid).toBe(true);
    expect(oneMinuteOver.maximumDurationLimitMinutes).toBe(MAX_BOOKING_DURATION_MINUTES);

    // A multi-century draft would otherwise walk tens of thousands of dates, or throw from the enumerator.
    const centuries = validate(window("2026-06-01", "10:00", "2426-06-01", "10:00"), { openDays: [1] });
    expect(centuries.maximumDurationInvalid).toBe(true);
    expect(centuries.openingInvalid).toBe(false);
    expect(centuries.window).toBeUndefined();
  });

  it("checks a smaller item limit first", () => {
    const result = validate(
      window("2026-06-06", "10:00", "2026-06-06", "12:00"),
      { openDays: [1] },
      {
        maxBookingDurationMinutes: 60,
      },
    );
    expect(result.maximumDurationInvalid).toBe(true);
    expect(result.maximumDurationLimitMinutes).toBe(60);
    expect(result.openingInvalid).toBe(false);
  });

  it("exempts maintenance from opening hours and closed weekdays but not from the absolute limit", () => {
    const maintenance = { enforceOpeningHours: false };
    const closedSaturday = validate(
      window("2026-06-06", "10:00", "2026-06-06", "12:00"),
      { openDays: [1] },
      maintenance,
    );
    expect(closedSaturday.openingInvalid).toBe(false);
    expect(closedSaturday.window).toEqual({ start: "2026-06-06T10:00:00Z", end: "2026-06-06T12:00:00Z" });
    expect(
      validate(window("2026-06-01", "00:00", "2027-06-02", "00:01"), { openDays: [1] }, maintenance)
        .maximumDurationInvalid,
    ).toBe(true);
  });

  it("keeps an unchanged booking outside the current schedule through the policy-mismatch flow", () => {
    const result = validate(
      window("2026-06-06", "10:00", "2026-06-06", "11:00"),
      { openDays: [1] },
      {
        allowPolicyMismatch: true,
      },
    );
    expect(result.openingInvalid).toBe(true);
    expect(result.window).toEqual({ start: "2026-06-06T10:00:00Z", end: "2026-06-06T11:00:00Z" });
  });
});
