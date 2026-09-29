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

function existingBooking({ id, start, end }: { id: number; start: string; end: string }) {
  return {
    id,
    version: 0,
    target: {
      relationTo: "booking-instruments",
      value: { id: 123, name: "Confocal microscope", deleted: false },
      globalId: "IN123",
    },
    timezone: "UTC",
    start,
    end,
    state: "CONFIRMED",
    kind: "BOOKING",
    privacy: "full",
    purpose: `Purpose ${id}`,
    cancellationReason: null,
    bookedBy: "Ada Lovelace",
    canEdit: false,
    canCancel: false,
    createdAt: "2030-10-01T09:00:00Z",
    updatedAt: "2030-10-01T09:00:00Z",
  };
}

function renderDialog(dialogCreation: BookingCreationContext = creation, bookings: readonly unknown[] = []) {
  server.use(
    http.get("/api/v2/bookings", () =>
      HttpResponse.json({
        docs: bookings,
        totalDocs: bookings.length,
        totalPages: bookings.length ? 1 : 0,
        page: 1,
        hasNextPage: false,
      }),
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
  it("prevents dismissal while a creation request is pending", async () => {
    const user = userEvent.setup();
    let release: () => void = () => {};
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.post("/api/v2/bookings", async () => {
        await pending;
        return HttpResponse.json({ status: 503 }, { status: 503 });
      }),
    );
    renderDialog();
    const dialog = await screen.findByTestId("compact-booking-dialog");
    const submit = within(dialog).getByRole("button", { name: "booking:bookings.form.submit" });
    await vi.waitFor(() => expect(submit).toBeEnabled());
    await user.click(submit);
    try {
      expect(within(dialog).getByRole("button", { name: "common:actions.close" })).toBeDisabled();
      expect(within(dialog).getByRole("button", { name: "booking:bookings.form.cancel" })).toBeDisabled();
      await user.keyboard("{Escape}");
      expect(dialog).toBeInTheDocument();
    } finally {
      release();
    }
    await vi.waitFor(() => expect(within(dialog).getByRole("button", { name: "common:actions.close" })).toBeEnabled());
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

  describe("on an item that allows double booking, over an existing booking", () => {
    const doubleBooking = (eventKind: BookingCreationContext["eventKind"]): BookingCreationContext => ({
      ...creation,
      eventKind,
      initialDate: "2030-10-07",
      target: creation.target && {
        ...creation.target,
        bufferBeforeMinutes: 0,
        bufferAfterMinutes: 0,
        allowDoubleBooking: true,
      },
      window: { startDate: "2030-10-07", startTime: "10:00", endDate: "2030-10-07", endTime: "11:00" },
    });
    const overlapping = existingBooking({ id: 41, start: "2030-10-07T10:30:00Z", end: "2030-10-07T11:30:00Z" });

    it("warns about the overlap but lets a booking be created", async () => {
      renderDialog(doubleBooking("BOOKING"), [overlapping]);

      const dialog = await screen.findByTestId("compact-booking-dialog");
      const warning = await within(dialog).findByRole("status");
      expect(warning).toHaveTextContent("booking:bookings.errors.overlapSummary");
      expect(within(warning).getByRole("listitem")).toHaveTextContent("Purpose 41");
      expect(within(dialog).queryByRole("alert")).not.toBeInTheDocument();
      expect(within(dialog).getByRole("button", { name: "booking:bookings.form.submit" })).toBeEnabled();
    });

    it("blocks a maintenance event, which the server rejects over any booking", async () => {
      renderDialog(doubleBooking("MAINTENANCE"), [overlapping]);

      const dialog = await screen.findByTestId("compact-booking-dialog");
      const alert = await within(dialog).findByRole("alert");
      expect(alert).toHaveTextContent("booking:bookings.errors.overlapSummary");
      expect(within(alert).getByRole("listitem")).toHaveTextContent("Purpose 41");
      expect(within(dialog).getByRole("button", { name: "booking:bookings.form.submitMaintenance" })).toBeDisabled();
    });
  });
});
