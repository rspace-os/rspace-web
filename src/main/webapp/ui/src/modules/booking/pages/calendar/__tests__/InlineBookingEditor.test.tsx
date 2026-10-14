import "@/__tests__/__mocks__/matchMedia";
import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRootRoute, createRouter } from "@tanstack/react-router";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { EditableBooking } from "@/modules/booking/creation/BookingForm";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { bookingTarget, collectionResponse, ownBooking } from "@/modules/booking/pages/mocks/bookingCalendarFixtures";
import { inheritedBrowserBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { bookableItemFixtures, collectionPage } from "../../bookable-items/mocks/bookableItemsMocks";
import { InlineBookingEditor } from "../InlineBookingEditor";
import { renderBookingTestPage } from "./calendarTestHarness";

function editableEvent(kind: "BOOKING" | "MAINTENANCE"): BookingListDocument & EditableBooking {
  return {
    ...ownBooking,
    id: 41,
    target: bookingTarget(123, "Confocal microscope"),
    kind,
    start: "2030-10-07T10:00:00Z",
    end: "2030-10-07T11:00:00Z",
    purpose: "Self event details",
    cancellationReason: null,
    privacy: "full",
    state: "CONFIRMED",
    canEdit: true,
  };
}

function overlappingEvent(kind: "BOOKING" | "MAINTENANCE") {
  return {
    ...ownBooking,
    id: 59,
    kind,
    start: "2030-10-07T10:30:00Z",
    end: "2030-10-07T11:30:00Z",
    privacy: "busy",
    purpose: null,
    cancellationReason: null,
    bookedBy: null,
    canEdit: false,
    canCancel: false,
  };
}

describe("InlineBookingEditor", () => {
  it.each([
    ["maintenance", "BOOKING", true],
    ["booking", "MAINTENANCE", true],
    ["booking", "BOOKING", false],
  ] as const)("applies the double-booking policy to a %s edit over %s", async (label, conflictKind, blocked) => {
    const user = userEvent.setup();
    const event = editableEvent(label === "maintenance" ? "MAINTENANCE" : "BOOKING");
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(collectionPage([{ ...bookableItemFixtures[0], allowDoubleBooking: true }], 2)),
      ),
      http.get("/api/v2/bookings", () =>
        HttpResponse.json(collectionResponse([event, overlappingEvent(conflictKind)])),
      ),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
    const root = createRootRoute({
      component: () => (
        <InlineBookingEditor event={event} timezone="UTC" token="test-token" onClose={() => undefined} />
      ),
    });
    const router = createRouter({ routeTree: root, history: createMemoryHistory({ initialEntries: ["/"] }) });
    await renderBookingTestPage(router, { queryClient, realI18n: false });

    const endTime = await screen.findByLabelText("booking:bookings.form.endTime");
    await user.clear(endTime);
    await user.type(endTime, "11:15");

    const submit = screen.getByRole("button", { name: "booking:bookings.form.save" });
    await waitFor(() => (blocked ? expect(submit).toBeDisabled() : expect(submit).toBeEnabled()));
    const feedback = blocked ? await screen.findByRole("alert") : await screen.findByRole("status");
    expect(feedback).toHaveTextContent("booking:bookings.errors.overlapSummary");
    expect(feedback).not.toHaveTextContent("Self event details");
    expect(feedback).not.toHaveTextContent("Private purpose");
    expect(feedback).not.toHaveTextContent("Private requester");
  });
});
