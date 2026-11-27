import "@/__tests__/__mocks__/matchMedia";
import { screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";

vi.unmock("react-i18next");

import { BookingDateControls } from "./BookingToolbar";

test("includes the displayed date in the jump button's accessible name", async () => {
  await renderWithRealI18n(
    <BookingDateControls
      date="2026-11-02"
      today="2026-11-02"
      timeZone="Europe/London"
      controlsLabel="Date controls"
      navigationLabel="Date navigation"
      previousLabel="Previous"
      todayLabel="Today"
      nextLabel="Next"
      jumpToDateLabel="Jump to date"
      onPrevious={vi.fn()}
      onNext={vi.fn()}
      onDateChange={vi.fn()}
    />,
    { resources: { booking: bookingEnglish }, defaultNS: "booking" },
  );
  const button = screen.getByRole("button", { name: /^Jump to date, / });
  expect(button).toHaveAccessibleName(`Jump to date, ${button.textContent}`);
});
