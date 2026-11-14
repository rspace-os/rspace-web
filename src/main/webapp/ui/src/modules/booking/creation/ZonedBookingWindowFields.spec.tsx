import "@/modules/common/styles/index.css";
import { cleanup, render } from "@testing-library/react";
import { afterEach, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import { withViewport } from "@/__tests__/pageObjects/viewport";
import { ALWAYS_OPEN } from "@/modules/booking/domain/bookingOpeningHours";
import { ZonedBookingWindowFields } from "./ZonedBookingWindowFields";

afterEach(cleanup);

test.each([
  [320, "compact"],
  [480, "compact"],
  [640, "compact"],
  [320, "comfortable"],
  [480, "comfortable"],
] as const)("keeps the timezone button beside the start time at %s px in %s mode", async (width, density) => {
  await withViewport(width, async () => {
    render(
      <div style={{ width }}>
        <ZonedBookingWindowFields
          density={density}
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
    for (const field of [start, end])
      expect(field.element().getBoundingClientRect().width).toBeGreaterThanOrEqual(density === "compact" ? 100 : 128);
    const label = "Timezone: America/New_York (click to edit)";
    const zone = page.getByRole("button", { name: label, exact: true });
    const startRect = start.element().getBoundingClientRect();
    const zoneRect = zone.element().getBoundingClientRect();
    expect(zoneRect.top).toBe(startRect.top);
    expect(zoneRect.left).toBeGreaterThanOrEqual(startRect.right);
    expect(start.element().nextElementSibling).toBe(zone.element());
    await expect.element(zone).toHaveTextContent("");
    expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(width);

    await zone.hover();
    await expect.element(page.getByText(label, { exact: true })).toBeVisible();
    await userEvent.keyboard("{Escape}");
    await end.hover();
    start.element().focus();
    // Native time inputs expose separate keyboard stops for their clock segments.
    for (let tabs = 0; tabs < 5 && document.activeElement !== zone.element(); tabs++) await userEvent.tab();
    await expect.element(zone).toHaveFocus();
    await expect.element(page.getByText(label, { exact: true })).toBeVisible();
    await userEvent.keyboard("{Enter}");
    await expect.element(zone).toHaveAttribute("aria-expanded", "true");
    await expect.element(page.getByRole("combobox", { name: "Start time zone", exact: true })).toBeVisible();
    await expect.element(page.getByRole("combobox", { name: "End time zone", exact: true })).toBeVisible();
    expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(width);
  });
});
