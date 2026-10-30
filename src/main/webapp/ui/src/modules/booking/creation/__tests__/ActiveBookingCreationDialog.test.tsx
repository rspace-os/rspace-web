import { beforeAll as beforeBookingCatalog } from "vitest";
import appI18n from "@/modules/common/i18n";

beforeBookingCatalog(async () => {
  appI18n.addResourceBundle(
    "en-US",
    "booking",
    (await import("@/modules/common/i18n/locales/en-US/booking.json")).default,
  );
});

import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, it } from "vitest";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { ALL_ISO_WEEKDAYS } from "@/modules/booking/domain/bookingOpeningHours";
import { collectionResponse } from "@/modules/booking/pages/mocks/bookingCalendarFixtures";
import { inheritedBrowserBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { ActiveBookingCreationDialog } from "../ActiveBookingCreationDialog";
import type { BookableItemOption } from "../bookableItemOption";
import type { BookingCreationContext } from "../bookingCreationStore";
import { BookingCreationStoreProvider } from "../bookingCreationStore";

const target: BookableItemOption = {
  configurationId: 7,
  targetId: 123,
  globalId: "IN123",
  name: "Confocal microscope",
  timezone: "UTC",
  slotGranularityMinutes: 15,
  openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};

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
      relationTo: "instruments" as const,
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

function renderDialog(dialogCreation: BookingCreationContext, events: readonly unknown[] = []) {
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
});
