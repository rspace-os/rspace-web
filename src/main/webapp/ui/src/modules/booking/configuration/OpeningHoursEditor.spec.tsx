import "@/modules/common/styles/index.css";
import { useForm } from "@formisch/react";
import { cleanup, render } from "@testing-library/react";
import { afterEach, expect, test } from "vitest";
import { page, server, userEvent } from "vitest/browser";
import { BookingTimeFormatProvider } from "@/modules/booking/domain/bookingDisplayPreferences";
import { OpeningHoursEditor } from "./OpeningHoursEditor";
import { SchedulingSettingsSchema } from "./schedulingSettingsSchema";

function Editor() {
  const form = useForm({
    schema: SchedulingSettingsSchema,
    initialInput: {
      slotGranularityMinutes: 15,
      incrementUnit: "MINUTES",
      openingHours: [{ dayOfWeek: 1, start: "01:00", end: "24:00" }],
      bufferBeforeMinutes: 0,
      bufferAfterMinutes: 0,
      maxBookingDurationMinutes: 0,
      allowDoubleBooking: false,
    },
  });
  return <OpeningHoursEditor form={form} disabled={false} layout="stacked" />;
}

afterEach(cleanup);

test.each(["H12", "H24"] as const)("keeps midnight segment edits mounted with %s formatting", async (timeFormat) => {
  render(
    <BookingTimeFormatProvider timeFormat={timeFormat}>
      <Editor />
    </BookingTimeFormatProvider>,
  );
  const start = page.getByLabelText("Opening start");
  const allDay = page.getByRole("checkbox", { name: "Open all day" });
  // Tab reaches the first native time segment without relying on engine-specific pointer hit areas.
  await page.getByRole("checkbox", { name: "Monday", exact: true }).click();
  await page.getByRole("checkbox", { name: "Monday", exact: true }).click();
  await userEvent.tab();
  await expect.element(start).toHaveFocus();
  // Linux WebKit has no native time segments; use its complete-value editor.
  const segmented = server.browser !== "webkit" || CSS.supports("font", "-apple-system-body");
  if (segmented) await userEvent.keyboard("00");
  else await start.fill("00:00");
  await expect.element(allDay).toBeChecked();
  await expect.element(start).toHaveFocus();
  await expect.element(start).toBeEnabled();
  if (segmented) await userEvent.keyboard("30");
  else await start.fill("00:30");
  await expect.element(start).toHaveValue("00:30");
  await expect.element(allDay).not.toBeChecked();
  await allDay.click();
  await expect.element(start).not.toBeInTheDocument();
  await allDay.click();
  await expect.element(start).toHaveValue("00:30");
});
