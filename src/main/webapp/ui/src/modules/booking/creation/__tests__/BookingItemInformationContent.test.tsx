import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ALL_ISO_WEEKDAYS } from "@/modules/booking/domain/bookingOpeningHours";
import { BookingItemInformationContent } from "../BookingItemInformationContent";
import { confocalTarget, confocalTargetWith } from "./creationTestFixtures";

const item = confocalTargetWith({
  slotGranularityMinutes: 90,
  incrementUnit: "HOURS",
  alignToIncrement: false,
  openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "18:00" })),
});

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
