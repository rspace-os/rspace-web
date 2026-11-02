import "@/modules/common/styles/index.css";
import { cleanup, render } from "@testing-library/react";
import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { withViewport } from "@/__tests__/pageObjects/viewport";
import { ALWAYS_OPEN } from "@/modules/booking/domain/bookingOpeningHours";
import { ZonedBookingWindowFields } from "./ZonedBookingWindowFields";

afterEach(cleanup);

test.each([360, 480])("keeps both typed times readable at %s px", async (width) => {
  await withViewport(width, async () => {
    render(
      <div style={{ width }}>
        <ZonedBookingWindowFields
          displayTimezone="America/New_York"
          schedulingTimezone="Europe/London"
          slotGranularityMinutes={30}
          incrementUnit="MINUTES"
          maxBookingDurationMinutes={0}
          {...ALWAYS_OPEN}
          value={{ startDate: "2026-11-09", startTime: "08:00", endDate: "2026-11-09", endTime: "09:00" }}
          onChange={() => undefined}
          onTimezoneChange={() => undefined}
        />
      </div>,
    );
    const start = page.getByLabelText("Start time", { exact: true });
    const end = page.getByLabelText("End time", { exact: true });
    await expect.element(start).toHaveValue("08:00");
    await expect.element(end).toHaveValue("09:00");
    for (const field of [start, end]) expect(field.element().getBoundingClientRect().width).toBeGreaterThanOrEqual(128);
    const zone = page.getByRole("button", { name: "Time zone: America/New_York" });
    expect(zone.element().getBoundingClientRect().top).toBeGreaterThanOrEqual(
      start.element().getBoundingClientRect().bottom,
    );
  });
});
