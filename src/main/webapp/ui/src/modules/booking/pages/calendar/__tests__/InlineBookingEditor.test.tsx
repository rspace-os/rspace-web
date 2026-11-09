import { getGetMyBookingPreferencesQueryKey } from "@/modules/common/api/v2/generated/users/users";
import "@/__tests__/__mocks__/matchMedia";
import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRootRoute, createRouter } from "@tanstack/react-router";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { EditableBooking } from "@/modules/booking/creation/BookingForm";
import type { BookingListDocument } from "@/modules/booking/domain/booking";

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
  it("does not apply booking buffers between maintenance windows", async () => {
    const user = userEvent.setup();
    const event = editableEvent("MAINTENANCE");
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(
          collectionPage([{ ...bookableItemFixtures[0], bufferBeforeMinutes: 30, bufferAfterMinutes: 30 }], 2),
        ),
      ),
      http.get("/api/v2/bookings", () =>
        HttpResponse.json(
          collectionResponse([
            event,
            { ...overlappingEvent("MAINTENANCE"), start: "2030-10-07T11:30:00Z", end: "2030-10-07T12:30:00Z" },
          ]),
        ),
      ),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    queryClient.setQueryData(getGetMyBookingPreferencesQueryKey(), inheritedBrowserBookingPreferences);
    const root = createRootRoute({
      component: () => <InlineBookingEditor event={event} timezone="UTC" onClose={() => undefined} />,
    });
    const router = createRouter({ routeTree: root, history: createMemoryHistory({ initialEntries: ["/"] }) });
    await renderBookingTestPage(router, { queryClient, realI18n: false });

    const endTime = await screen.findByLabelText("booking:bookings.form.endTime");
    await user.clear(endTime);
    await user.type(endTime, "11:15");

    await waitFor(() => expect(screen.getByRole("button", { name: "booking:bookings.form.save" })).toBeEnabled());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

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
    queryClient.setQueryData(getGetMyBookingPreferencesQueryKey(), inheritedBrowserBookingPreferences);
    const root = createRootRoute({
      component: () => <InlineBookingEditor event={event} timezone="UTC" onClose={() => undefined} />,
    });
    const router = createRouter({ routeTree: root, history: createMemoryHistory({ initialEntries: ["/"] }) });
    await renderBookingTestPage(router, { queryClient, realI18n: false });

    const endTime = await screen.findByLabelText("booking:bookings.form.endTime");
    await user.clear(endTime);
    await user.type(endTime, "11:15");

    const submit = screen.getByRole("button", { name: "booking:bookings.form.save" });
    await waitFor(() => (blocked ? expect(submit).toBeDisabled() : expect(submit).toBeEnabled()));
    const feedback = blocked ? await screen.findByRole("alert") : await screen.findByRole("status");
    expect(feedback).toMatchTextContent("booking:bookings.errors.overlapSummary");
    expect(feedback).not.toMatchTextContent("Self event details");
    expect(feedback).not.toMatchTextContent("Private purpose");
    expect(feedback).not.toMatchTextContent("Private requester");
  });
});

it.each(["committed", "changed", "network", "failed-fetch", "uneditable"])(
  "checks an uncertain %s save against the server",
  async (outcome) => {
    const user = userEvent.setup();
    const event = editableEvent("BOOKING");
    const close = vi.fn();
    let reads = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([bookableItemFixtures[0]], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([event]))),
      http.patch("/api/v2/bookings/41", ({ request }) => {
        expect(request.headers.get("If-Match")).toBe(`"${event.version}"`);
        return outcome === "network" ? HttpResponse.error() : new HttpResponse(null, { status: 503 });
      }),
      http.get("/api/v2/bookings/41", () => {
        reads += 1;
        if (outcome === "failed-fetch") return new HttpResponse(null, { status: 503 });
        return HttpResponse.json({
          ...event,
          canEdit: outcome !== "uneditable",
          version: event.version + 1,
          purpose: outcome === "committed" ? "Attempted purpose" : "Other editor",
        });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    queryClient.setQueryData(getGetMyBookingPreferencesQueryKey(), inheritedBrowserBookingPreferences);
    const root = createRootRoute({
      component: () => <InlineBookingEditor event={event} timezone="UTC" onClose={close} />,
    });
    const router = createRouter({ routeTree: root, history: createMemoryHistory({ initialEntries: ["/"] }) });
    await renderBookingTestPage(router, { queryClient, realI18n: false });
    const purpose = await screen.findByRole("textbox", { name: "booking:bookings.form.purpose" });
    await user.clear(purpose);
    await user.type(purpose, "Attempted purpose");
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.save" }));
    if (outcome === "committed") {
      await waitFor(() => expect(close).toHaveBeenCalledOnce());
    } else if (outcome === "failed-fetch" || outcome === "uneditable") {
      const retry = await screen.findByRole("button", { name: "common:actions.retry" });
      expect(screen.getByRole("button", { name: "booking:bookings.form.save" })).toBeDisabled();
      await user.click(retry);
      await waitFor(() => expect(reads).toBe(2));
      await user.click(screen.getByRole("button", { name: "booking:calendar.inlineEditor.discardStaleEdit" }));
      expect(close).toHaveBeenCalledOnce();
    } else {
      await screen.findByText("booking:calendar.inlineEditor.serverInterval");
      expect(screen.getByRole("button", { name: "booking:bookings.form.save" })).toBeDisabled();
      expect(close).not.toHaveBeenCalled();
    }
    expect(reads).toBe(outcome === "failed-fetch" || outcome === "uneditable" ? 2 : 1);
  },
);
