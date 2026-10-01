import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { BookingAgenda } from "./BookingAgenda";

const privatePurpose = "PRIVATE_PURPOSE_SHOULD_NEVER_RENDER";

function busyBooking(id: number): BookingListDocument {
  return {
    id,
    version: 1,
    target: {
      relationTo: "booking-instruments",
      value: {
        id: 41,
        name: "Confocal microscope",
        deleted: false,
        parentContainerName: "Imaging suite with an unusually long location name",
        parentContainerGlobalId: "IC23",
      },
      globalId: "IN41",
    },
    timezone: "Europe/Berlin",
    start: "2026-09-24T08:00:00.000Z",
    end: "2026-09-24T09:00:00.000Z",
    state: "CONFIRMED",
    kind: "BOOKING",
    privacy: "busy",
    purpose: privatePurpose,
    bookedBy: null,
    createdBy: null,
    canViewConfiguration: true,
    requesterId: 7,
    canEdit: false,
    canCancel: false,
    createdAt: "2026-09-01T08:00:00.000Z",
    updatedAt: "2026-09-01T08:00:00.000Z",
  };
}

describe("BookingAgenda", () => {
  it("keeps busy booking purpose out of collapsed and expanded content and actions", async () => {
    const user = userEvent.setup();
    render(
      <BookingAgenda
        bookings={[busyBooking(41)]}
        timeZone="Europe/Berlin"
        now={Date.parse("2026-09-24T07:00:00.000Z")}
      />,
    );

    const summaryText = screen.getAllByText("Confocal microscope").find((element) => element.closest("summary"));
    const summary = summaryText?.closest("summary");
    if (!summary) throw new Error("Expected a booking row summary");
    expect(screen.queryByText(privatePurpose)).not.toBeInTheDocument();
    expect(summary).not.toHaveAccessibleName(new RegExp(privatePurpose));
    expect(summary).not.toHaveAttribute("title", privatePurpose);

    await user.click(summary);

    expect(screen.queryByText(privatePurpose)).not.toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("gives each agenda instance unique day-heading IDs", () => {
    const row = busyBooking(41);
    render(
      <>
        <BookingAgenda bookings={[row]} timeZone="Europe/Berlin" now={Date.parse("2026-09-24T07:00:00.000Z")} />
        <BookingAgenda bookings={[row]} timeZone="Europe/Berlin" now={Date.parse("2026-09-24T07:00:00.000Z")} />
      </>,
    );

    const headingIds = screen.getAllByRole("heading").map((heading) => heading.id);
    expect(headingIds).toHaveLength(2);
    expect(new Set(headingIds).size).toBe(2);
  });
});
