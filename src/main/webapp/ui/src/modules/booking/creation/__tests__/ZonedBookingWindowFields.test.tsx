import { fireEvent, render, screen, within } from "@testing-library/react";
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

const ALL_DAY = {
  openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
} satisfies OpeningSchedule;

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
            openingHours={ALL_DAY.openingHours}
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
          displayTimezone="Europe/Berlin"
          schedulingTimezone="Europe/Berlin"
          slotGranularityMinutes={5}
          maxBookingDurationMinutes={0}
          openingHours={ALL_DAY.openingHours}
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
          openingHours={ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "17:00" }))}
          value={{ startDate: "2026-08-17", startTime, endDate: "2026-08-17", endTime }}
          onChange={vi.fn()}
        />,
      );

      expect(
        validateBookingWindow({ startDate: "2026-08-17", startTime, endDate: "2026-08-17", endTime }, displayTimezone, {
          schedulingTimezone,
          slotGranularityMinutes: 5,
          incrementUnit: "MINUTES" as const,
          alignToIncrement: true,
          maxBookingDurationMinutes: 0,
          openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "17:00" })),
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
        openingHours={ALL_DAY.openingHours}
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
          displayTimezone="UTC"
          schedulingTimezone="UTC"
          slotGranularityMinutes={15}
          maxBookingDurationMinutes={0}
          openingHours={ALL_DAY.openingHours}
          density={density}
          initial={{ startDate: "2026-08-17", startTime: "15:22", endDate: "2026-08-17", endTime: "16:00" }}
        />,
      );
      const startTime = screen.getByLabelText("booking:bookings.form.startTime");
      expect(screen.queryByRole("status")).not.toBeInTheDocument();

      await user.click(startTime);
      await user.tab();

      expect(startTime).toHaveValue("15:15");
      expect(screen.getByRole("status")).toHaveTextContent("booking:bookings.form.startSnapped");
      expect(screen.getByRole("status").parentElement).toHaveAttribute("aria-live", "polite");

      await user.clear(startTime);
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    },
  );

  it("does not announce a time that is already on the increment", async () => {
    const user = userEvent.setup();
    render(
      <StatefulFields
        displayTimezone="UTC"
        schedulingTimezone="UTC"
        slotGranularityMinutes={15}
        maxBookingDurationMinutes={0}
        openingHours={ALL_DAY.openingHours}
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
        displayTimezone="UTC"
        schedulingTimezone="UTC"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingHours={ALL_DAY.openingHours}
        initial={{ startDate: "2026-08-17", startTime: "15:28", endDate: "2026-08-17", endTime: "16:00" }}
      />,
      {
        resources: {
          booking: {
            bookableItemDetails: { minutes: bookingEnglish.bookableItemDetails.minutes },
            bookings: {
              form: { startSnapped: bookingEnglish.bookings.form.startSnapped },
              increment: bookingEnglish.bookings.increment,
            },
          },
        },
        defaultNS: "booking",
      },
    );
    const start = screen.getByRole("group", { name: "bookings.form.start" });

    await user.click(within(start).getByLabelText("bookings.form.startTime"));
    await user.tab();

    expect(within(start).getByRole("status")).toHaveTextContent(
      "Start adjusted to 03:30 PM to match the clock intervals of 5 minutes.",
    );
  });

  it("snaps the end to whole increments after the start and announces it", async () => {
    const user = userEvent.setup();
    render(
      <StatefulFields
        displayTimezone="UTC"
        schedulingTimezone="UTC"
        slotGranularityMinutes={15}
        alignToIncrement={false}
        maxBookingDurationMinutes={0}
        {...ALL_DAY}
        initial={{ startDate: "2026-08-17", startTime: "09:07", endDate: "2026-08-17", endTime: "09:30" }}
      />,
    );
    const start = screen.getByLabelText("booking:bookings.form.startTime");
    const end = screen.getByLabelText("booking:bookings.form.endTime");
    expect(start).toHaveAttribute("step", "60");
    expect(screen.getByText("booking:bookings.errors.durationMultiple")).toBeVisible();

    await user.click(start);
    await user.tab();
    expect(start).toHaveValue("09:07");
    await user.click(end);
    await user.tab();

    expect(end).toHaveValue("09:37");
    expect(screen.getByRole("status")).toHaveTextContent("booking:bookings.form.endSnapped");
    expect(screen.queryByText("booking:bookings.errors.durationMultiple")).not.toBeInTheDocument();
  });

  it("reports an aligned start off the scheduling-zone grid until it snaps", async () => {
    const user = userEvent.setup();
    render(
      <StatefulFields
        displayTimezone="UTC"
        schedulingTimezone="UTC"
        slotGranularityMinutes={15}
        maxBookingDurationMinutes={0}
        {...ALL_DAY}
        initial={{ startDate: "2026-08-17", startTime: "09:07", endDate: "2026-08-17", endTime: "09:22" }}
      />,
    );
    const start = screen.getByLabelText("booking:bookings.form.startTime");
    expect(start).toHaveAttribute("step", "900");
    expect(screen.getByLabelText("booking:bookings.form.endTime")).toHaveAttribute("step", "60");
    expect(screen.getByText("booking:bookings.errors.alignment")).toBeVisible();

    await user.click(start);
    await user.tab();

    expect(start).toHaveValue("09:00");
    expect(screen.getByText("booking:bookings.errors.durationMultiple")).toBeVisible();
  });

  it("keeps the start's local time for a Day end and picks the nearest whole increment", async () => {
    const user = userEvent.setup();
    render(
      <StatefulFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={2880}
        incrementUnit="DAYS"
        alignToIncrement={false}
        maxBookingDurationMinutes={0}
        {...ALL_DAY}
        initial={{ startDate: "2026-03-30", startTime: "12:00", endDate: "2026-04-02", endTime: "13:00" }}
      />,
    );
    expect(screen.getByText("booking:bookings.errors.dayIncrement")).toBeVisible();

    await user.click(screen.getByLabelText("booking:bookings.form.endTime"));
    await user.tab();

    expect(screen.getByLabelText("booking:bookings.form.endDate")).toHaveValue("2026-04-03");
    expect(screen.getByLabelText("booking:bookings.form.endTime")).toHaveValue("12:00");
    expect(screen.queryByText("booking:bookings.errors.dayIncrement")).not.toBeInTheDocument();
  });

  it("names the missing local end time instead of moving a Day booking across the spring gap", async () => {
    const user = userEvent.setup();
    await renderWithRealI18n(
      <StatefulFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={1440}
        incrementUnit="DAYS"
        alignToIncrement={false}
        maxBookingDurationMinutes={0}
        {...ALL_DAY}
        initial={{ startDate: "2026-03-28", startTime: "02:30", endDate: "2026-03-29", endTime: "05:00" }}
      />,
      {
        resources: { booking: { bookings: { errors: bookingEnglish.bookings.errors } } },
        defaultNS: "booking",
      },
    );
    const end = screen.getByLabelText("bookings.form.endTime");

    await user.click(end);
    await user.tab();

    expect(end).toHaveValue("05:00");
    expect(
      screen.getByText(
        /This booking would end at 02:30 AM on .*, but that local time does not exist in Europe\/Berlin\./,
      ),
    ).toBeVisible();
  });

  it("does not choose an occurrence for a repeated time when snapping", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={15}
        maxBookingDurationMinutes={0}
        {...ALL_DAY}
        value={{ startDate: "2026-10-25", startTime: "02:30", endDate: "2026-10-25", endTime: "04:00" }}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByLabelText("booking:bookings.form.startTime"));
    await user.tab();
    await user.click(screen.getByLabelText("booking:bookings.form.endTime"));
    await user.tab();

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByText("booking:bookings.errors.occurrenceRequired")).toBeVisible();
  });

  it("leaves the occurrence open when a snap lands on a repeated time", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={30}
        maxBookingDurationMinutes={0}
        {...ALL_DAY}
        value={{ startDate: "2026-10-25", startTime: "01:00", endDate: "2026-10-25", endTime: "01:50" }}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByLabelText("booking:bookings.form.endTime"));
    await user.tab();

    expect(onChange).toHaveBeenLastCalledWith(
      // One hour after 01:00 is 02:00 summer time, the first of the two 02:00s; the form asks which one.
      expect.objectContaining({ endDate: "2026-10-25", endTime: "02:00", endOccurrence: undefined }),
    );
  });

  it("names a missing Day end that the snap chose on another date", async () => {
    const user = userEvent.setup();
    render(
      <StatefulFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={2880}
        incrementUnit="DAYS"
        alignToIncrement={false}
        maxBookingDurationMinutes={0}
        {...ALL_DAY}
        initial={{ startDate: "2027-03-26", startTime: "02:30", endDate: "2027-03-27", endTime: "20:00" }}
      />,
    );
    expect(screen.getByText("booking:bookings.errors.dayIncrement")).toBeVisible();

    await user.click(screen.getByLabelText("booking:bookings.form.endTime"));
    await user.tab();

    // Two days on is 28 March at 02:30, which the spring change skips.
    expect(screen.getByLabelText("booking:bookings.form.endDate")).toHaveValue("2027-03-27");
    expect(screen.getByText("booking:bookings.errors.dayEndMissing")).toBeVisible();
    expect(screen.queryByText("booking:bookings.errors.dayIncrement")).not.toBeInTheDocument();
  });

  it("asks which occurrence of a repeated Day end to use when the form shows another zone", async () => {
    const user = userEvent.setup();
    render(
      <StatefulFields
        displayTimezone="UTC"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={1440}
        incrementUnit="DAYS"
        alignToIncrement={false}
        maxBookingDurationMinutes={0}
        {...ALL_DAY}
        initial={{ startDate: "2026-10-24", startTime: "00:30", endDate: "2026-10-25", endTime: "03:00" }}
      />,
    );

    await user.click(screen.getByLabelText("booking:bookings.form.endTime"));
    await user.tab();
    const choice = screen.getByRole("group", { name: "booking:bookings.form.repeatedEnd" });
    expect(screen.getByLabelText("booking:bookings.form.endTime")).toHaveValue("03:00");

    await user.click(within(choice).getByRole("radio", { name: "booking:bookings.form.laterOccurrence" }));

    // 02:30 winter time in Berlin is 01:30 UTC.
    expect(screen.getByLabelText("booking:bookings.form.endTime")).toHaveValue("01:30");
    expect(screen.queryByRole("group", { name: "booking:bookings.form.repeatedEnd" })).not.toBeInTheDocument();
    expect(screen.queryByText("booking:bookings.errors.dayIncrement")).not.toBeInTheDocument();
  });

  it("keeps the chosen occurrence when the display zone repeats the same time", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    function Fields() {
      const [value, setValue] = useState<BookingWindowDraft>({
        startDate: "2026-10-24",
        startTime: "02:30",
        endDate: "2026-10-25",
        endTime: "03:00",
      });
      return (
        <ZonedBookingWindowFields
          displayTimezone="Europe/Paris"
          schedulingTimezone="Europe/Berlin"
          slotGranularityMinutes={1440}
          incrementUnit="DAYS"
          alignToIncrement={false}
          maxBookingDurationMinutes={0}
          {...ALL_DAY}
          value={value}
          onChange={(next) => {
            onChange(next);
            setValue(next);
          }}
        />
      );
    }
    render(<Fields />);

    await user.click(screen.getByLabelText("booking:bookings.form.endTime"));
    await user.tab();
    const choice = screen.getByRole("group", { name: "booking:bookings.form.repeatedEnd" });
    await user.click(within(choice).getByRole("radio", { name: "booking:bookings.form.laterOccurrence" }));

    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ endDate: "2026-10-25", endTime: "02:30", endOccurrence: "later" }),
    );
    expect(screen.queryByText("booking:bookings.errors.occurrenceRequired")).not.toBeInTheDocument();
  });

  it("snaps an aligned start once its repeated time's occurrence is chosen", async () => {
    const user = userEvent.setup();
    render(
      <StatefulFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={60}
        maxBookingDurationMinutes={0}
        {...ALL_DAY}
        initial={{ startDate: "2026-10-25", startTime: "02:30", endDate: "2026-10-25", endTime: "05:00" }}
      />,
    );
    const start = screen.getByRole("group", { name: "booking:bookings.form.start" });

    await user.click(within(start).getByRole("radio", { name: "booking:bookings.form.laterOccurrence" }));

    // 02:30 winter time is equidistant from 02:00 and 03:00 winter time; the earlier wins and stays "later".
    expect(within(start).getByLabelText("booking:bookings.form.startTime")).toHaveValue("02:00");
    expect(within(start).getByRole("radio", { name: "booking:bookings.form.laterOccurrence" })).toBeChecked();
    expect(screen.queryByText("booking:bookings.errors.alignment")).not.toBeInTheDocument();
  });

  it("does not carry an occurrence chosen for the typed end over to a different snapped end", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={60}
        maxBookingDurationMinutes={0}
        {...ALL_DAY}
        value={{
          startDate: "2026-10-25",
          startTime: "01:00",
          endDate: "2026-10-25",
          endTime: "02:40",
          endOccurrence: "earlier",
        }}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByLabelText("booking:bookings.form.endTime"));
    await user.tab();

    // The nearest whole hour is 02:00 winter time; the earlier choice was for 02:40, so the form asks again.
    expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ endTime: "02:00", endOccurrence: undefined }));
  });

  it("retires a missing Day end message when the item's rule changes", async () => {
    const user = userEvent.setup();
    const props = {
      displayTimezone: "Europe/Berlin",
      schedulingTimezone: "Europe/Berlin",
      maxBookingDurationMinutes: 0,
      ...ALL_DAY,
      value: { startDate: "2027-03-26", startTime: "02:30", endDate: "2027-03-27", endTime: "20:00" },
      onChange: vi.fn(),
    };
    const { rerender } = render(
      <ZonedBookingWindowFields
        {...props}
        slotGranularityMinutes={2880}
        incrementUnit="DAYS"
        alignToIncrement={false}
      />,
    );
    await user.click(screen.getByLabelText("booking:bookings.form.endTime"));
    await user.tab();
    expect(screen.getByText("booking:bookings.errors.dayEndMissing")).toBeVisible();

    rerender(
      <ZonedBookingWindowFields
        {...props}
        slotGranularityMinutes={15}
        incrementUnit="MINUTES"
        alignToIncrement={false}
      />,
    );

    expect(screen.queryByText("booking:bookings.errors.dayEndMissing")).not.toBeInTheDocument();
  });

  it("shows both dates in compact density for a Day increment", () => {
    render(
      <StatefulFields
        displayTimezone="UTC"
        schedulingTimezone="UTC"
        slotGranularityMinutes={1440}
        incrementUnit="DAYS"
        alignToIncrement={false}
        maxBookingDurationMinutes={0}
        {...ALL_DAY}
        density="compact"
        initial={{ startDate: "2026-08-17", startTime: "23:00", endDate: "2026-08-17", endTime: "23:00" }}
      />,
    );

    expect(screen.getByLabelText("booking:bookings.form.startDate")).toHaveValue("2026-08-17");
    expect(screen.getByLabelText("booking:bookings.form.endDate")).toHaveValue("2026-08-17");
  });

  it("allows an always-open interval across the scheduling timezone's local date boundary", () => {
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="America/New_York"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingHours={ALL_DAY.openingHours}
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
          incrementUnit: "MINUTES" as const,
          alignToIncrement: true,
          maxBookingDurationMinutes: 0,
          openingHours: ALL_DAY.openingHours,
        },
      ).window,
    ).toEqual({ start: "2026-08-18T03:00:00Z", end: "2026-08-18T05:00:00Z" });
  });

  it.each([
    ["00:00", "24:00", 0, true],
    ["09:00", "17:00", 0, false],
    ["00:00", "24:00", 60, false],
  ])("validates multiday windows against %s–%s and duration %i", (start, end, maxBookingDurationMinutes, valid) => {
    render(
      <ZonedBookingWindowFields
        displayTimezone="UTC"
        schedulingTimezone="UTC"
        slotGranularityMinutes={5}
        openingHours={ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start, end }))}
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
          incrementUnit: "MINUTES" as const,
          alignToIncrement: true,
          openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start, end })),

          maxBookingDurationMinutes,
        },
      ).window,
    ).toEqual(valid ? { start: "2026-10-19T10:00:00Z", end: "2026-10-21T11:00:00Z" } : undefined);
  });

  it("blocks a nonexistent spring-forward time", () => {
    render(
      <ZonedBookingWindowFields
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingHours={ALL_DAY.openingHours}
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
        openingHours={ALL_DAY.openingHours}
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
        displayTimezone="UTC"
        schedulingTimezone="UTC"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={0}
        openingHours={ALL_DAY.openingHours}
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
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={1}
        maxBookingDurationMinutes={60}
        openingHours={ALL_DAY.openingHours}
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
          incrementUnit: "MINUTES" as const,
          alignToIncrement: true,
          maxBookingDurationMinutes: 60,
          openingHours: ALL_DAY.openingHours,
        },
      ).window,
    ).toBeUndefined();
  });

  it("names the maximum duration in words", async () => {
    await renderWithRealI18n(
      <ZonedBookingWindowFields
        displayTimezone="UTC"
        schedulingTimezone="UTC"
        slotGranularityMinutes={5}
        maxBookingDurationMinutes={120}
        openingHours={ALL_DAY.openingHours}
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
        displayTimezone="Europe/Berlin"
        schedulingTimezone="Europe/Berlin"
        slotGranularityMinutes={1}
        maxBookingDurationMinutes={60}
        openingHours={ALL_DAY.openingHours}
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
          incrementUnit: "MINUTES" as const,
          alignToIncrement: true,
          maxBookingDurationMinutes: 60,
          openingHours: ALL_DAY.openingHours,
        },
      ).window,
    ).toEqual({
      start: "2026-10-25T00:30:00Z",
      end: "2026-10-25T01:30:00Z",
    });
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
      incrementUnit: "MINUTES" as const,
      alignToIncrement: true,
      maxBookingDurationMinutes: 0,
      openingHours: ALL_DAY.openingHours,

      ...schedule,
      ...policy,
    });
  const officeHours = {
    openingHours: [1, 2, 3, 4, 5, 6, 7]
      .map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "17:00" }))
      .map(
        (entry) =>
          [{ dayOfWeek: 2, start: "10:00", end: "16:00" }].find((day) => day.dayOfWeek === entry.dayOfWeek) ?? entry,
      ),
  };

  it("uses a weekday exception instead of the shared hours", () => {
    expect(validate(window("2026-06-01", "09:30", "2026-06-01", "10:00"), officeHours).openingInvalid).toBe(false);
    const tuesday = validate(window("2026-06-02", "09:30", "2026-06-02", "10:00"), officeHours);
    expect(tuesday.openingInvalid).toBe(true);
    expect(tuesday.window).toBeUndefined();
  });

  it("finds the weekday in the scheduling timezone, not the display timezone", () => {
    // Sunday 10:00 in Los Angeles is Monday 02:00 in Tokyo.
    const draft = window("2026-05-31", "10:00", "2026-05-31", "11:00");
    const zones = { displayTimezone: "America/Los_Angeles", schedulingTimezone: "Asia/Tokyo" };
    expect(
      validate(
        draft,
        {
          openingHours: [{ dayOfWeek: 1, start: "00:00", end: "24:00" }],
        },
        zones,
      ).openingInvalid,
    ).toBe(false);
    expect(
      validate(
        draft,
        {
          openingHours: [{ dayOfWeek: 7, start: "00:00", end: "24:00" }],
        },
        zones,
      ).openingInvalid,
    ).toBe(true);
  });

  it("rejects durations over the absolute limit before checking opening hours", () => {
    const exact = validate(window("2026-06-01", "00:00", "2027-06-02", "00:00"));
    expect(exact.maximumDurationInvalid).toBe(false);
    expect(exact.window).toBeDefined();

    const oneMinuteOver = validate(window("2026-06-01", "00:00", "2027-06-02", "00:01"));
    expect(oneMinuteOver.maximumDurationInvalid).toBe(true);
    expect(oneMinuteOver.maximumDurationLimitMinutes).toBe(MAX_BOOKING_DURATION_MINUTES);

    // A multi-century draft would otherwise walk tens of thousands of dates, or throw from the enumerator.
    const centuries = validate(window("2026-06-01", "10:00", "2426-06-01", "10:00"), {
      openingHours: [{ dayOfWeek: 1, start: "00:00", end: "24:00" }],
    });
    expect(centuries.maximumDurationInvalid).toBe(true);
    expect(centuries.openingInvalid).toBe(false);
    expect(centuries.window).toBeUndefined();
  });

  it("caps an item maximum above the absolute limit before checking opening hours", () => {
    const value = window("2026-06-01", "00:00", "2027-06-02", "00:01");
    const openingHours = [{ dayOfWeek: 1, start: "09:00", end: "10:00" }];
    const result = validate(value, { openingHours }, { maxBookingDurationMinutes: MAX_BOOKING_DURATION_MINUTES + 1 });
    expect(result.maximumDurationLimitMinutes).toBe(MAX_BOOKING_DURATION_MINUTES);
    expect(result.maximumDurationInvalid).toBe(true);
    expect(result.openingInvalid).toBe(false);
    expect(result.window).toBeUndefined();

    render(
      <ZonedBookingWindowFields
        displayTimezone="UTC"
        schedulingTimezone="UTC"
        slotGranularityMinutes={1}
        maxBookingDurationMinutes={MAX_BOOKING_DURATION_MINUTES + 1}
        openingHours={openingHours}
        value={value}
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByText("booking:bookings.errors.duration")).toBeVisible();
  });

  it("checks a smaller item limit first", () => {
    const result = validate(
      window("2026-06-06", "10:00", "2026-06-06", "12:00"),
      {
        openingHours: [{ dayOfWeek: 1, start: "00:00", end: "24:00" }],
      },
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
      {
        openingHours: [{ dayOfWeek: 1, start: "00:00", end: "24:00" }],
      },
      maintenance,
    );
    expect(closedSaturday.openingInvalid).toBe(false);
    expect(closedSaturday.window).toEqual({ start: "2026-06-06T10:00:00Z", end: "2026-06-06T12:00:00Z" });
    expect(
      validate(
        window("2026-06-01", "00:00", "2027-06-02", "00:01"),
        {
          openingHours: [{ dayOfWeek: 1, start: "00:00", end: "24:00" }],
        },
        maintenance,
      ).maximumDurationInvalid,
    ).toBe(true);
  });

  it("keeps an unchanged booking outside the current schedule through the policy-mismatch flow", () => {
    const result = validate(
      window("2026-06-06", "10:00", "2026-06-06", "11:00"),
      {
        openingHours: [{ dayOfWeek: 1, start: "00:00", end: "24:00" }],
      },
      {
        allowPolicyMismatch: true,
      },
    );
    expect(result.openingInvalid).toBe(true);
    expect(result.window).toEqual({ start: "2026-06-06T10:00:00Z", end: "2026-06-06T11:00:00Z" });
  });
});
