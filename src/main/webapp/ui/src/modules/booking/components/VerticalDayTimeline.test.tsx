import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterAll, beforeAll, expect, it, vi } from "vitest";
import { timelinePolicy } from "@/modules/booking/creation/timelineRange";
import { ALWAYS_OPEN } from "@/modules/booking/domain/bookingOpeningHours";
import { VerticalDayTimeline } from "./VerticalDayTimeline";

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

beforeAll(() => vi.stubGlobal("ResizeObserver", ResizeObserverStub));
afterAll(() => vi.unstubAllGlobals());

it("keeps the midnight continuation for a short visible part of an overnight draft", async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  render(
    <VerticalDayTimeline
      date="2026-08-17"
      onDateChange={vi.fn()}
      timezone="UTC"
      events={[]}
      schedule={{ status: "success" }}
      editing={{
        draft: { startDate: "2026-08-17", startTime: "23:45", endDate: "2026-08-18", endTime: "00:15" },
        policy: timelinePolicy({
          timezone: "UTC",
          slotGranularityMinutes: 15,
          incrementUnit: "MINUTES",
          alignToIncrement: true,
          openingHours: ALWAYS_OPEN.openingHours,
          maxBookingDurationMinutes: 0,
        }),
        onChange,
      }}
    />,
  );

  // Fifteen visible minutes are too short for the move handle, but not for the continuation.
  expect(screen.queryByRole("button", { name: "booking:dayTimeline.vertical.moveDraft" })).not.toBeInTheDocument();
  const continuation = screen.getByRole("button", { name: "booking:timelineEdit.endAtMidnight" });
  continuation.focus();
  await user.keyboard("{Enter}");

  expect(onChange).toHaveBeenCalledWith({
    startDate: "2026-08-17",
    startTime: "23:45",
    endDate: "2026-08-18",
    endTime: "00:00",
  });
});
