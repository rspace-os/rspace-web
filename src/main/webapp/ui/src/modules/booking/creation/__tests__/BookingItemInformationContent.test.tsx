import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { BookingItemInformationContent } from "../BookingItemInformationContent";

const item: BookableItemOption = {
  configurationId: 7,
  targetId: 123,
  globalId: "IN123",
  name: "Confocal microscope",
  timezone: "Europe/Berlin",
  slotGranularityMinutes: 90,
  incrementUnit: "HOURS",
  alignToIncrement: false,
  openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "18:00" })),

  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};

const fact = (label: string) => screen.getByText(label).nextElementSibling;

describe("BookingItemInformationContent", () => {
  it("shows the increment in its stored unit without clock alignment", () => {
    render(<BookingItemInformationContent item={item} displayTimezone="Europe/Berlin" date="2026-10-07" />);

    expect(fact("booking:bookableItemDetails.fields.granularity")).toHaveTextContent(
      "booking:bookableItemDetails.hours",
    );
    expect(screen.queryByText("booking:bookableItemDetails.fields.alignToIncrement")).not.toBeInTheDocument();
  });

  it("shows a day increment in its stored unit", () => {
    render(
      <BookingItemInformationContent
        item={{
          ...item,
          slotGranularityMinutes: 1440,
          incrementUnit: "DAYS",
          alignToIncrement: true,
          openingHours: item.openingHours.map((entry) => ({ ...entry, start: "00:00", end: "24:00" })),
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
