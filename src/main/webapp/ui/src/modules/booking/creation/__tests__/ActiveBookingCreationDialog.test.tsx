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
import { inheritedBrowserBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { ActiveBookingCreationDialog } from "../ActiveBookingCreationDialog";
import type { BookingCreationContext } from "../bookingCreationStore";
import { BookingCreationStoreProvider } from "../bookingCreationStore";

// Delegates to the real request unless a test rejects with a problem carrying its parsed body.
vi.mock("@/modules/booking/domain/booking", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/modules/booking/domain/booking")>();
  return { ...actual, createBooking: vi.fn(actual.createBooking) };
});

const creation: BookingCreationContext = {
  ownerId: "calendar",
  triggerId: "create-trigger",
  eventKind: "BOOKING",
  initialDate: "2026-09-28",
  lockTarget: true,
  target: {
    configurationId: 7,
    targetId: 123,
    globalId: "IN123",
    name: "Confocal microscope",
    timezone: "UTC",
    slotGranularityMinutes: 15,
    openingStart: "00:00",
    openingEnd: "24:00",
    openDays: [1, 2, 3, 4, 5, 6, 7],
    openingExceptions: [],
    bufferBeforeMinutes: 15,
    bufferAfterMinutes: 15,
    maxBookingDurationMinutes: 0,
    allowDoubleBooking: false,
  },
  window: { startDate: "2026-09-28", startTime: "10:00", endDate: "2026-09-28", endTime: "11:00" },
};

function renderDialog() {
  server.use(
    http.get("/api/v2/bookings", () =>
      HttpResponse.json({ docs: [], totalDocs: 0, totalPages: 0, page: 1, hasNextPage: false }),
    ),
  );
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
        <ActiveBookingCreationDialog creation={creation} />
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
