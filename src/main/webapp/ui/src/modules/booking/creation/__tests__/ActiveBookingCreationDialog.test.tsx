import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, it, vi } from "vitest";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { ApiV2ProblemError, createBooking } from "@/modules/booking/domain/booking";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { ALL_ISO_WEEKDAYS } from "@/modules/booking/domain/bookingOpeningHours";
import { collectionResponse } from "@/modules/booking/pages/mocks/bookingCalendarFixtures";
import { inheritedBrowserBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { ActiveBookingCreationDialog } from "../ActiveBookingCreationDialog";
import type { BookableItemOption } from "../bookableItemOption";
import type { BookingCreationContext } from "../bookingCreationStore";
import { BookingCreationStoreProvider } from "../bookingCreationStore";
import { confocalTargetWith } from "./creationTestFixtures";

// Delegates to the real request unless a test rejects with a problem carrying its parsed body.
vi.mock("@/modules/booking/domain/booking", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/modules/booking/domain/booking")>();
  return { ...actual, createBooking: vi.fn(actual.createBooking) };
});

const target: BookableItemOption = confocalTargetWith({
  timezone: "UTC",
  slotGranularityMinutes: 15,
  bufferBeforeMinutes: 15,
  bufferAfterMinutes: 15,
});

const creation: BookingCreationContext = {
  ownerId: "calendar",
  triggerId: "create-trigger",
  eventKind: "BOOKING",
  initialDate: "2030-10-07",
  lockTarget: true,
  target,
  window: { startDate: "2030-10-07", startTime: "10:00", endDate: "2030-10-07", endTime: "11:00" },
};

function doubleBooking(eventKind: BookingCreationContext["eventKind"]): BookingCreationContext {
  return {
    ...creation,
    eventKind,
    target: { ...target, allowDoubleBooking: true },
  };
}

function overlappingEvent(kind: "BOOKING" | "MAINTENANCE", privacy: "full" | "busy" = "full") {
  return {
    id: 41,
    version: 0,
    target: {
      relationTo: "booking-instruments" as const,
      value: { id: 123, name: "Confocal microscope", deleted: false },
      globalId: "IN123",
    },
    timezone: "UTC",
    start: "2030-10-07T10:30:00Z",
    end: "2030-10-07T11:30:00Z",
    state: "CONFIRMED",
    kind,
    privacy,
    purpose: privacy === "busy" ? null : "Private purpose",
    bookedBy: privacy === "busy" ? null : "Private requester",
    canEdit: false,
    canCancel: false,
    createdAt: "2030-10-01T09:00:00Z",
    updatedAt: "2030-10-01T09:00:00Z",
  };
}

function renderDialog(dialogCreation: BookingCreationContext = creation, events: readonly unknown[] = []) {
  server.use(http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse(events))));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, {
    ...inheritedBrowserBookingPreferences,
    timezoneMode: "CUSTOM",
    customTimezone: "UTC",
    overridden: true,
  });
  const root = createRootRoute({
    component: () => (
      <BookingCreationStoreProvider>
        <button type="button" id={creation.triggerId}>
          {"Create"}
        </button>
        <ActiveBookingCreationDialog creation={dialogCreation} />
      </BookingCreationStoreProvider>
    ),
  });
  const router = createRouter({ routeTree: root, history: createMemoryHistory({ initialEntries: ["/"] }) });
  render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>,
  );
}

describe("ActiveBookingCreationDialog", () => {
  it("blocks maintenance over an ordinary booking on a double-bookable target", async () => {
    renderDialog(doubleBooking("MAINTENANCE"), [overlappingEvent("BOOKING")]);

    const dialog = await screen.findByTestId("compact-booking-dialog");
    const alert = await within(dialog).findByRole("alert");
    expect(alert).toHaveTextContent("booking:bookings.errors.overlapSummary");
    expect(within(dialog).getByRole("button", { name: "booking:bookings.form.submitMaintenance" })).toBeDisabled();
  });

  it("blocks an ordinary booking over maintenance on a double-bookable target", async () => {
    renderDialog(doubleBooking("BOOKING"), [overlappingEvent("MAINTENANCE")]);

    const dialog = await screen.findByTestId("compact-booking-dialog");
    const alert = await within(dialog).findByRole("alert");
    expect(alert).toHaveTextContent("booking:bookings.errors.overlapSummary");
    expect(within(dialog).getByRole("button", { name: "booking:bookings.form.submit" })).toBeDisabled();
  });

  it("warns about ordinary overlaps and does not expose a redacted booking's details", async () => {
    renderDialog(doubleBooking("BOOKING"), [overlappingEvent("BOOKING", "busy")]);

    const dialog = await screen.findByTestId("compact-booking-dialog");
    const warning = await within(dialog).findByRole("status");
    expect(warning).toHaveTextContent("booking:bookings.errors.overlapSummary");
    expect(warning).not.toHaveTextContent("Private purpose");
    expect(warning).not.toHaveTextContent("Private requester");
    expect(within(dialog).getByRole("button", { name: "booking:bookings.form.submit" })).toBeEnabled();
  });

  it("keeps opening-hours validation separate from overlap fetching", async () => {
    const user = userEvent.setup();
    let createRequests = 0;
    const closedTarget = {
      ...target,
      openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "09:00" })),
    };
    server.use(
      http.post("/api/v2/bookings", () => {
        createRequests += 1;
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    renderDialog(
      {
        ...creation,
        target: closedTarget,
      },
      [],
    );

    const dialog = await screen.findByTestId("compact-booking-dialog");
    await user.click(within(dialog).getByRole("button", { name: "booking:bookings.form.submit" }));
    expect(await within(dialog).findByText("booking:bookings.errors.openingHours")).toBeVisible();
    expect(within(dialog).queryByText("booking:bookings.errors.overlap")).not.toBeInTheDocument();
    expect(createRequests).toBe(0);
  });

  it("lists the booking a server buffer rejection names and keeps submission blocked", async () => {
    const user = userEvent.setup();
    vi.mocked(createBooking).mockRejectedValueOnce(
      Object.assign(new ApiV2ProblemError(409, "errors.api.v2.booking.buffer", "private server detail"), {
        problem: {
          status: 409,
          code: "errors.api.v2.booking.buffer",
          conflict: { id: 59, kind: "BOOKING", start: "2026-09-28T08:00:00Z", end: "2026-09-28T10:00:00Z" },
          bufferBeforeMinutes: 15,
          bufferAfterMinutes: 15,
        },
      }),
    );
    renderDialog();

    const dialog = await screen.findByTestId("compact-booking-dialog");
    // Quick create stays in the viewer's zone; the full form offers the zone fields.
    expect(
      within(dialog).queryByRole("button", { name: "booking:bookings.form.changeTimezone" }),
    ).not.toBeInTheDocument();
    const submit = within(dialog).getByRole("button", { name: "booking:bookings.form.submit" });
    await vi.waitFor(() => expect(submit).toBeEnabled());
    await user.click(submit);

    const alert = await within(dialog).findByRole("alert");
    expect(alert).toHaveTextContent("booking:bookings.errors.buffer");
    expect(alert).toHaveTextContent("booking:bookings.errors.bufferSummary");
    expect(within(alert).getByRole("listitem")).toHaveTextContent(/booking:bookings\.errors\.overlapBooking · 0?8:00/);
    expect(alert).not.toHaveTextContent("private server detail");
    expect(within(dialog).getByRole("button", { name: "booking:bookings.form.submit" })).toBeDisabled();
  });
});
