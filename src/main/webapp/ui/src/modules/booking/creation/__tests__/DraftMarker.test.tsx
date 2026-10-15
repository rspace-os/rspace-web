import { Temporal } from "@js-temporal/polyfill";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import type { BookingWindowDraft } from "@/modules/booking/domain/bookingTime";
import { DraftMarker } from "../DraftMarker";
import type { TimelineMovePolicy } from "../timelineMove";

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
    movementPolicy,
    onChange,
  }: {
    timeZone?: string;
    schedulingTimezone?: string;
    snapIncrementMinutes?: number;
    movementPolicy?: TimelineMovePolicy;
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
      schedulingTimezone={schedulingTimezone}
      snapIncrementMinutes={snapIncrementMinutes}
      movementPolicy={movementPolicy}
      draft={draft}
      onChange={onChange}
    />,
  );
  mockTimelineBounds();
  return screen.getByRole("button", { name: "booking:calendar.windowEditor.move" });
}

function expectKathmanduGrid(draft: BookingWindowDraft | undefined) {
  expect(draft).toBeDefined();
  if (!draft) return;
  for (const edge of ["start", "end"] as const) {
    const instant = Temporal.PlainDateTime.from(`${draft[`${edge}Date`]}T${draft[`${edge}Time`]}`)
      .toZonedDateTime("UTC")
      .toInstant();
    expect(instant.toZonedDateTimeISO("Asia/Kathmandu").minute % 10).toBe(0);
  }
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
    startTime: "21:55",
    endDate: "2026-08-18",
    endTime: "01:55",
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
    startTime: "08:05",
    endDate: "2026-08-19",
    endTime: "08:05",
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

it("keeps a hidden start and chooses the earlier Kathmandu grid slot on a left-edge tie", async () => {
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
    startDate: "2026-08-16",
    startTime: "23:55",
    endDate: "2026-08-17",
    endTime: "00:55",
  });
  expectKathmanduGrid(onChange.mock.lastCall?.[0]);
});

it("keeps the full interval on the Kathmandu grid when moved at the right edge", async () => {
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
    startTime: "23:55",
    endDate: "2026-08-18",
    endTime: "00:55",
  });
  expectKathmanduGrid(onChange.mock.lastCall?.[0]);
});

it("does not emit a keyboard move when no valid next interval remains on the displayed day", async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  const move = renderDraftMarker(
    { startDate: "2026-08-17", startTime: "23:55", endDate: "2026-08-18", endTime: "00:55" },
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
      movementPolicy: {
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
