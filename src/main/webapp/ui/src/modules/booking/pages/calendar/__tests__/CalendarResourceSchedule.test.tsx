import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { CalendarResourceSchedule } from "../CalendarResourceSchedule";
import type { BookingCalendarResource } from "../calendarLayoutUtils";

const resource = {
  relationTo: "booking-instruments",
  globalId: "IN123",
  value: {
    id: 123,
    name: "Confocal microscope",
    deleted: false,
    parentContainerName: null,
    parentContainerGlobalId: null,
  },
} satisfies BookingCalendarResource;

const twoDayConfiguration = {
  configurationId: 7,
  targetId: 123,
  globalId: "IN123",
  name: "Confocal microscope",
  timezone: "UTC",
  slotGranularityMinutes: 2880,
  incrementUnit: "DAYS",
  alignToIncrement: false,
  openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),

  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  capabilities: { canCreateBlockout: false, canCreateBooking: true },
} satisfies BookableItemOption;

describe("CalendarResourceSchedule", () => {
  it("offers a cross-day Add draft for an always-open resource with a two-day increment", async () => {
    const user = userEvent.setup();
    const onResourceRangeSelect = vi.fn();

    render(
      <CalendarResourceSchedule
        date="2026-08-17"
        view="day"
        events={[]}
        resources={[resource]}
        timezone="UTC"
        today="2026-08-17"
        availabilityStartMinute={600}
        availabilityEndMinute={1080}
        resourceConfigurations={[twoDayConfiguration]}
        creationDisabled={false}
        onResourceRangeSelect={onResourceRangeSelect}
      />,
    );

    await user.click(screen.getByRole("button", { name: /calendar\.actions\.addForItem/ }));

    expect(onResourceRangeSelect).toHaveBeenCalledWith(
      twoDayConfiguration,
      { startMinute: 600, endMinute: 600 + 2880 },
      expect.any(HTMLElement),
    );
  });
});
