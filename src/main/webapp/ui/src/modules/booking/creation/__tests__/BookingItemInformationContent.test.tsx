import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import { ALL_ISO_WEEKDAYS } from "@/modules/booking/domain/bookingOpeningHours";
import booking from "@/modules/common/i18n/locales/en-US/booking.json";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import { BookingItemInformationContent } from "../BookingItemInformationContent";
import { confocalTarget, confocalTargetWith } from "./creationTestFixtures";

const item = confocalTargetWith({
  slotGranularityMinutes: 90,
  incrementUnit: "HOURS",
  openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "18:00" })),
});

const fact = (label: string) => screen.getByText(label).nextElementSibling;

describe("BookingItemInformationContent", () => {
  it("groups consecutive weekdays with identical hours", async () => {
    const wrapper = await createRealI18nWrapper({ resources: { booking, common }, defaultNS: "booking" });
    render(
      <BookingItemInformationContent
        item={{ ...item, openingHours: item.openingHours.filter((day) => day.dayOfWeek <= 5) }}
        displayTimezone="Europe/Berlin"
        date="2026-10-07"
      />,
      { wrapper },
    );
    expect(screen.getByText(/Mon–Fri:/)).toBeInTheDocument();
    expect(screen.getByText("Sat–Sun: closed")).toBeInTheDocument();
  });

  it("shows the increment in its stored unit without clock alignment", () => {
    render(<BookingItemInformationContent item={item} displayTimezone="Europe/Berlin" date="2026-10-07" />);

    expect(fact("booking:bookableItemDetails.fields.granularity")).toHaveTextContent(
      "booking:bookableItemDetails.hours",
    );
  });

  it("shows a day increment in its stored unit", () => {
    render(
      <BookingItemInformationContent
        item={{
          ...item,
          slotGranularityMinutes: 1440,
          incrementUnit: "DAYS",
          openingHours: confocalTarget.openingHours,
        }}
        displayTimezone="America/New_York"
        date="2026-10-07"
      />,
    );

    expect(fact("booking:bookableItemDetails.fields.granularity")).toHaveTextContent(
      "booking:bookableItemDetails.days",
    );
    expect(fact("booking:bookings.itemInformation.open")).toHaveTextContent(
      "booking:bookings.itemInformation.everyDay",
    );
  });
});
