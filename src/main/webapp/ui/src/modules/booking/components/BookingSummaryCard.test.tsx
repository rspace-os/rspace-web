import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { bookingListDocument } from "@/modules/booking/domain/bookingAgendaFixtures";
import { BookingSummaryCard } from "./BookingSummaryCard";

const privatePurpose = "PRIVATE_PURPOSE_SHOULD_NEVER_RENDER";

const busyBooking = bookingListDocument({
  id: 81,
  target: {
    relationTo: "booking-instruments",
    value: {
      id: 42,
      name: "Confocal microscope",
      deleted: false,
      parentContainerName: null,
      parentContainerGlobalId: null,
    },
    globalId: "IN42",
  },
  start: "2026-09-24T08:00:00.000Z",
  end: "2026-09-24T09:00:00.000Z",
  privacy: "busy",
  purpose: privatePurpose,
  canViewConfiguration: true,
  createdAt: "2026-09-01T08:00:00.000Z",
  updatedAt: "2026-09-01T08:00:00.000Z",
});

describe("BookingSummaryCard", () => {
  it("omits busy purpose and actions even when private data and configuration access are supplied", () => {
    render(<BookingSummaryCard booking={busyBooking} timeZone="Europe/Berlin" />);

    expect(screen.getByRole("article")).toHaveAccessibleName(/Confocal microscope/);
    expect(screen.queryByText(privatePurpose)).not.toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
