import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { ALWAYS_OPEN } from "@/modules/booking/domain/bookingOpeningHours";
import { CalendarResourceSchedule } from "../CalendarResourceSchedule";
import type { BookingCalendarResource } from "../calendarLayoutUtils";

const resource = {
  relationTo: "instruments",
  globalId: "IN123",
  value: {
    id: 123,
    name: "Confocal microscope",
    deleted: false,
    parentContainer: null,
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
  openingHours: [...ALWAYS_OPEN.openingHours],
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

    await user.click(screen.getByRole("button", { name: "booking:calendar.actions.addForItem" }));

    expect(onResourceRangeSelect).toHaveBeenCalledWith(
      twoDayConfiguration,
      { startMinute: 600, endMinute: 600 + 2880 },
      expect.any(HTMLElement),
    );
  });
});
