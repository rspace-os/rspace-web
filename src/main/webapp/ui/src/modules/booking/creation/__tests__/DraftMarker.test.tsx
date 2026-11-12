import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import type { BookingWindowDraft } from "@/modules/booking/domain/bookingTime";
import { DraftMarker } from "../DraftMarker";
import { type TimelinePolicy, unrestrictedTimelinePolicy } from "../timelineRange";

function mockTimelineBounds() {
  const canvas = screen.getByTestId("day-timeline-canvas");
  vi.spyOn(canvas, "getBoundingClientRect").mockReturnValue({
    x: 0,
    y: 0,
    left: 0,
    top: 0,
    right: 1_440,
    bottom: 100,
    width: 1_440,
    height: 100,
    toJSON: () => ({}),
  });
}

function renderDraftMarker(
  draft: BookingWindowDraft,
  {
    timeZone = "UTC",
    schedulingTimezone = timeZone,
    snapIncrementMinutes = 5,
    policy,
    onChange,
  }: {
    timeZone?: string;
    schedulingTimezone?: string;
    snapIncrementMinutes?: number;
    policy?: Partial<TimelinePolicy>;
    onChange?: (draft: BookingWindowDraft) => void;
  } = {},
) {
  render(
    <section id="timeline">
      <div data-testid="day-timeline-canvas" />
    </section>,
  );
  render(
    <DraftMarker
      creation={{ ownerId: "test", triggerId: "timeline", initialDate: draft.startDate, eventKind: "BOOKING" }}
      timeZone={timeZone}
      policy={{
        ...unrestrictedTimelinePolicy(schedulingTimezone),
        slotGranularityMinutes: snapIncrementMinutes,

        ...policy,
      }}
      draft={draft}
      onChange={onChange}
    />,
  );
  mockTimelineBounds();
  return screen.getByRole("button", { name: "booking:calendar.windowEditor.move" });
}

it("retains the elapsed width of a booking across the repeated hour", () => {
  render(
    <section id="timeline">
      <div data-testid="day-timeline-canvas" />
    </section>,
  );
  render(
    <DraftMarker
      creation={{ ownerId: "test", triggerId: "timeline", initialDate: "2026-10-25", eventKind: "BOOKING" }}
      timeZone="Europe/Berlin"
      draft={{
        startDate: "2026-10-25",
        startTime: "02:45",
        startOccurrence: "earlier",
        endDate: "2026-10-25",
        endTime: "02:15",
        endOccurrence: "later",
      }}
    />,
  );
  expect(screen.getByTestId("compact-booking-draft-marker")).toHaveStyle({ left: "11%", width: "2%" });
});

it("keeps an overnight booking's true interval when moved by keyboard", async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  const move = renderDraftMarker(
    { startDate: "2026-08-17", startTime: "22:00", endDate: "2026-08-18", endTime: "02:00" },
    { onChange },
  );
  move.focus();
  await user.keyboard("{ArrowLeft}");

  expect(onChange).toHaveBeenCalledWith({
    startDate: "2026-08-17",
    startTime: "21:59",
    endDate: "2026-08-18",
    endTime: "01:59",
  });
});

it("preserves a 2,880-minute interval when moved by keyboard", async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  const move = renderDraftMarker(
    { startDate: "2026-08-17", startTime: "08:00", endDate: "2026-08-19", endTime: "08:00" },
    { onChange },
  );

  move.focus();
  await user.keyboard("{ArrowRight}");

  const moved = onChange.mock.lastCall?.[0];
  expect(moved).toEqual({
    startDate: "2026-08-17",
    startTime: "08:01",
    endDate: "2026-08-19",
    endTime: "08:01",
  });
  expect(
    Date.parse(`${moved?.endDate}T${moved?.endTime}:00Z`) - Date.parse(`${moved?.startDate}T${moved?.startTime}:00Z`),
  ).toBe(2_880 * 60_000);
});

it("preserves a 2,880-minute interval when moved by pointer", async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  const move = renderDraftMarker(
    { startDate: "2026-08-17", startTime: "08:00", endDate: "2026-08-19", endTime: "08:00" },
    { onChange },
  );

  await user.pointer([
    { target: move, coords: { clientX: 480 } },
    { keys: "[MouseLeft>]", target: move },
    { target: move, coords: { clientX: 495 } },
    "[/MouseLeft]",
  ]);

  const moved = onChange.mock.lastCall?.[0];
  expect(moved).toEqual({
    startDate: "2026-08-17",
    startTime: "08:15",
    endDate: "2026-08-19",
    endTime: "08:15",
  });
  expect(
    Date.parse(`${moved?.endDate}T${moved?.endTime}:00Z`) - Date.parse(`${moved?.startDate}T${moved?.startTime}:00Z`),
  ).toBe(2_880 * 60_000);
});

it("keeps a hidden start and moves to the left display edge", async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  const move = renderDraftMarker(
    { startDate: "2026-08-17", startTime: "00:20", endDate: "2026-08-17", endTime: "01:20" },
    { schedulingTimezone: "Asia/Kathmandu", snapIncrementMinutes: 10, onChange },
  );

  await user.pointer([
    { target: move, coords: { clientX: 20 } },
    { keys: "[MouseLeft>]", target: move },
    { target: move, coords: { clientX: 0 } },
    "[/MouseLeft]",
  ]);

  expect(onChange).toHaveBeenCalledWith({
    startDate: "2026-08-17",
    startTime: "00:00",
    endDate: "2026-08-17",
    endTime: "01:00",
  });
});

it("keeps the full interval in a fractional-offset scheduling timezone when moved at the right edge", async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  const move = renderDraftMarker(
    { startDate: "2026-08-17", startTime: "23:45", endDate: "2026-08-18", endTime: "00:45" },
    { schedulingTimezone: "Asia/Kathmandu", snapIncrementMinutes: 10, onChange },
  );

  await user.pointer([
    { target: move, coords: { clientX: 1_425 } },
    { keys: "[MouseLeft>]", target: move },
    { target: move, coords: { clientX: 1_440 } },
    "[/MouseLeft]",
  ]);

  expect(onChange).toHaveBeenCalledWith({
    startDate: "2026-08-17",
    startTime: "23:59",
    endDate: "2026-08-18",
    endTime: "00:59",
  });
});

it("does not emit a keyboard move when no valid next interval remains on the displayed day", async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  const move = renderDraftMarker(
    { startDate: "2026-08-17", startTime: "23:59", endDate: "2026-08-18", endTime: "00:59" },
    { schedulingTimezone: "Asia/Kathmandu", snapIncrementMinutes: 10, onChange },
  );

  move.focus();
  await user.keyboard("{ArrowRight}");

  expect(onChange).not.toHaveBeenCalled();
});

it("does not emit a keyboard move when the complete interval exceeds the item duration limit", async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  const move = renderDraftMarker(
    { startDate: "2026-08-17", startTime: "09:00", endDate: "2026-08-17", endTime: "09:30" },
    {
      policy: {
        maxBookingDurationMinutes: 15,
        openingHours: [],
        enforceOpeningHours: false,
      },
      onChange,
    },
  );

  move.focus();
  await user.keyboard("{ArrowRight}");

  expect(onChange).not.toHaveBeenCalled();
});

it("moves an overnight booking by one minute without pulling its end into the day", async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  render(
    <section id="timeline">
      <div data-testid="day-timeline-canvas" />
    </section>,
  );
  render(
    <DraftMarker
      creation={{ ownerId: "test", triggerId: "timeline", initialDate: "2026-08-17", eventKind: "BOOKING" }}
      timeZone="UTC"
      draft={{
        startDate: "2026-08-17",
        startTime: "22:00",
        endDate: "2026-08-18",
        endTime: "02:00",
      }}
      onChange={onChange}
    />,
  );

  screen.getByRole("button", { name: "booking:calendar.windowEditor.move" }).focus();
  await user.keyboard("{ArrowLeft}");

  expect(onChange).toHaveBeenCalledWith({
    startDate: "2026-08-17",
    startTime: "21:59",
    endDate: "2026-08-18",
    endTime: "01:59",
  });
});

it("ends an overnight booking at midnight on double-click or Enter, but not on a single click", async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  render(
    <section id="timeline">
      <div data-testid="day-timeline-canvas" />
    </section>,
  );
  render(
    <DraftMarker
      creation={{ ownerId: "test", triggerId: "timeline", initialDate: "2026-08-17", eventKind: "BOOKING" }}
      timeZone="UTC"
      draft={{ startDate: "2026-08-17", startTime: "22:00", endDate: "2026-08-18", endTime: "02:00" }}
      onChange={onChange}
    />,
  );
  const continuation = screen.getByRole("button", { name: "booking:timelineEdit.endAtMidnight" });
  expect(screen.queryByRole("button", { name: "booking:calendar.windowEditor.end" })).not.toBeInTheDocument();

  await user.click(continuation);
  expect(onChange).not.toHaveBeenCalled();
  expect(continuation).toHaveFocus();

  await user.keyboard("{Enter}");
  await user.dblClick(continuation);
  const midnight = { startDate: "2026-08-17", startTime: "22:00", endDate: "2026-08-18", endTime: "00:00" };
  expect(onChange).toHaveBeenNthCalledWith(1, midnight);
  expect(onChange).toHaveBeenNthCalledWith(2, midnight);
  expect(onChange).toHaveBeenCalledTimes(2);
});
