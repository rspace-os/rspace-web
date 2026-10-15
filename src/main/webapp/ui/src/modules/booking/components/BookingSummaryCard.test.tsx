import { createRootRoute, createRoute, Outlet } from "@tanstack/react-router";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { bookingListDocument } from "@/modules/booking/domain/bookingAgendaFixtures";
import { renderInRouter } from "./__tests__/renderInRouter";
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

function renderBookingSummaryCard(booking: BookingListDocument) {
  const root = createRootRoute({ component: () => <BookingSummaryCard booking={booking} timeZone="Europe/Berlin" /> });
  const details = createRoute({
    getParentRoute: () => root,
    path: "/booking/calendar/bookings/$id",
    component: Outlet,
  });
  return renderInRouter(root.addChildren([details]));
}

describe("BookingSummaryCard", () => {
  it("labels maintenance text as notes and booking text as purpose", async () => {
    const { unmount } = await renderBookingSummaryCard({
      ...busyBooking,
      kind: "MAINTENANCE",
      privacy: "full",
      purpose: "Laser alignment",
    });
    expect(await screen.findByText("booking:bookings.form.notes")).toBeVisible();
    expect(screen.queryByText("booking:myBookings.fields.purpose")).not.toBeInTheDocument();
    unmount();

    await renderBookingSummaryCard({ ...busyBooking, privacy: "full", purpose: "Imaging" });
    expect(await screen.findByText("booking:myBookings.fields.purpose")).toBeVisible();
    expect(screen.queryByText("booking:bookings.form.notes")).not.toBeInTheDocument();
  });

  it("omits busy purpose and actions even when private data and configuration access are supplied", () => {
    render(<BookingSummaryCard booking={busyBooking} timeZone="Europe/Berlin" />);

    expect(screen.getByRole("article")).toHaveAccessibleName("Confocal microscopeIN42");
    expect(screen.queryByText(privatePurpose)).not.toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
