import { useForm } from "@formisch/react";
import { screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { BookingTimeFormatProvider } from "@/modules/booking/domain/bookingDisplayPreferences";
import booking from "@/modules/common/i18n/locales/en-US/booking.json";
import { OpeningHoursEditor } from "../OpeningHoursEditor";
import { SchedulingSettingsSchema } from "../schedulingSettingsSchema";

function Editor() {
  const form = useForm({
    schema: SchedulingSettingsSchema,
    initialInput: {
      slotGranularityMinutes: 15,
      incrementUnit: "MINUTES",
      openingHours: [{ dayOfWeek: 1, start: "18:00", end: "09:00" }],
      bufferBeforeMinutes: 0,
      bufferAfterMinutes: 0,
      maxBookingDurationMinutes: 0,
      allowDoubleBooking: false,
    },
  });
  return <OpeningHoursEditor form={form} disabled={false} layout="stacked" />;
}

it.each([
  ["H12", "12:00 AM"],
  ["H24", "00:00"],
] as const)("names midnight in the %s clock when the end is before the start", async (timeFormat, midnight) => {
  await renderWithRealI18n(
    <BookingTimeFormatProvider timeFormat={timeFormat}>
      <Editor />
    </BookingTimeFormatProvider>,
    { resources: { booking }, defaultNS: "booking" },
  );

  expect(
    screen.getByText(`Use an opening start before the end, or enter ${midnight} as the end to close at midnight.`, {
      normalizer: (text) => text.replace(/\s/g, " "),
    }),
  ).toBeVisible();
});
