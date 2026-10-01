import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { BookingSummaryCard } from "./BookingSummaryCard";

const privatePurpose = "PRIVATE_PURPOSE_SHOULD_NEVER_RENDER";

const busyBooking = {
  id: 81,
  version: 1,
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
} satisfies BookingListDocument;

describe("BookingSummaryCard", () => {
  it("omits busy purpose and actions even when private data and configuration access are supplied", () => {
    render(<BookingSummaryCard booking={busyBooking} timeZone="Europe/Berlin" />);

    expect(screen.getByRole("article")).toHaveAccessibleName(/Confocal microscope/);
    expect(screen.queryByText(privatePurpose)).not.toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
