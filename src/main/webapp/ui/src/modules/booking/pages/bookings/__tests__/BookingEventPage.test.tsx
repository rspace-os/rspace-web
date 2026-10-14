import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, it } from "vitest";
import { OAUTH_TOKEN, oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { createBookingEventRouteTree } from "../routes";

const document = {
  id: 41,
  version: 0,
  target: {
    relationTo: "booking-instruments",
    value: {
      id: 123,
      name: "Confocal microscope",
      deleted: false,
      parentContainerName: "Imaging lab",
      parentContainerGlobalId: "IC456",
    },
    globalId: "IN123",
  },
  canViewConfiguration: true,
  timezone: "Europe/Berlin",
  start: "2026-08-17T08:00:00Z",
  end: "2026-08-17T10:00:00Z",
  state: "CONFIRMED",
  kind: "BOOKING",
  privacy: "full",
  purpose: "Cell imaging",
  bookedBy: "Ada Lovelace (ada)",
  createdBy: "Grace Hopper (grace)",
  canEdit: true,
  canCancel: true,
  createdAt: "2026-08-01T09:00:00Z",
  updatedAt: "2026-08-02T10:00:00Z",
} as const;

function renderPage(path = "/booking/calendar/bookings/41", preferencesReady?: Promise<void>) {
  server.use(oauthTokenHandler(true));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  if (preferencesReady) {
    server.use(
      http.get("/api/v2/users/me/booking-preferences", async () => {
        await preferencesReady;
        return HttpResponse.json(inheritedBrowserBookingPreferences);
      }),
    );
  } else {
    queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  }
  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const myBookings = createRoute({ getParentRoute: () => booking, path: "/my-bookings", component: Outlet });
  const item = createRoute({
    getParentRoute: () => booking,
    path: "/bookable-items/$globalId/{-$tab}",
    component: Outlet,
  });
  const router = createRouter({
    routeTree: root.addChildren([booking.addChildren([myBookings, item, createBookingEventRouteTree(booking)])]),
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  return {
    ...render(
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>
          <RouterProvider router={router as never} />
        </Suspense>
      </QueryClientProvider>,
    ),
    router,
    queryClient,
  };
}

describe("BookingEventPage", () => {
  it("uses the same page skeleton across preference suspension and the booking read", async () => {
    const preferences = Promise.withResolvers<void>();
    const booking = Promise.withResolvers<void>();
    let requested = false;
    server.use(
      http.get("/api/v2/bookings/41", async () => {
        requested = true;
        await booking.promise;
        return HttpResponse.json(document);
      }),
    );
    renderPage("/booking/calendar/bookings/41", preferences.promise);
    try {
      const main = await screen.findByRole("main");
      expect(main).toHaveAttribute("aria-busy", "true");
      expect(requested).toBe(false);
      preferences.resolve();
      await waitFor(() => expect(requested).toBe(true));
      expect(screen.getByRole("main")).toHaveAttribute("class", main.className);
      expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
      expect(screen.queryByText("Cell imaging")).not.toBeInTheDocument();
    } finally {
      preferences.resolve();
      booking.resolve();
    }
    expect(await screen.findByText("Cell imaging")).toBeVisible();
  });

  it("reserves the page while loading without exposing resource controls", async () => {
    const response = Promise.withResolvers<Response>();
    server.use(http.get("/api/v2/bookings/41", () => response.promise));
    renderPage();

    try {
      expect(await screen.findByRole("main")).toHaveAttribute("aria-busy", "true");
      expect(screen.getByRole("status")).toHaveClass("sr-only");
      expect(screen.queryByRole("link")).not.toBeInTheDocument();
      expect(screen.queryByRole("button")).not.toBeInTheDocument();
    } finally {
      response.resolve(HttpResponse.json(document));
    }
    expect(await screen.findByRole("heading", { name: "Confocal microscope" })).toBeVisible();
    expect(screen.getByRole("main")).not.toHaveAttribute("aria-busy", "true");
  });

  it.each([403, 404])("does not retain another booking while navigating to a denied booking (%s)", async (status) => {
    const response = Promise.withResolvers<Response>();
    server.use(
      http.get("/api/v2/bookings/41", () => HttpResponse.json(document)),
      http.get("/api/v2/bookings/42", () => response.promise),
    );
    const { router } = renderPage();
    expect(await screen.findByText("Cell imaging")).toBeVisible();
    try {
      await router.navigate({ to: "/booking/calendar/bookings/$id", params: { id: "42" } });
      expect(await screen.findByRole("status")).toHaveClass("sr-only");
      expect(screen.queryByText("Cell imaging")).not.toBeInTheDocument();
      expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "booking:bookings.actions.edit" })).not.toBeInTheDocument();
    } finally {
      response.resolve(new HttpResponse(null, { status }));
    }
    expect(await screen.findByText("booking:bookings.details.unavailableTitle")).toBeVisible();
    expect(screen.getByRole("main")).not.toHaveAttribute("aria-busy", "true");
  });

  it("renders the event readout, metadata aside, item return, and compact location", async () => {
    server.use(http.get("/api/v2/bookings/41", () => HttpResponse.json(document)));
    renderPage();

    expect(await screen.findByRole("heading", { level: 1, name: "Confocal microscope" })).toBeVisible();
    expect(screen.getByRole("heading", { name: "booking:bookings.details.title" })).toBeVisible();
    expect(screen.getByText("Cell imaging")).toBeVisible();
    expect(screen.getByText("Ada Lovelace (ada)")).toBeVisible();
    const aside = screen.getByRole("complementary", { name: "booking:bookings.details.aboutBooking" });
    expect(within(aside).getByText("booking:bookings.details.timesShownIn")).toBeVisible();
    expect(within(aside).getByText("booking:bookings.details.lastUpdated")).toBeVisible();
    expect(screen.getByRole("link", { name: "booking:bookings.details.returnToItemCalendar" })).toHaveAttribute(
      "href",
      "/booking/bookable-items/IN123",
    );
    expect(screen.getByRole("link", { name: "booking:bookings.details.viewItem" })).toHaveAttribute(
      "href",
      "/booking/bookable-items/IN123",
    );
    expect(screen.getByRole("link", { name: "Imaging lab" })).toHaveAttribute("href", "/globalId/IC456");
    expect(screen.getByRole("link", { name: "booking:bookings.actions.edit" })).toHaveAttribute(
      "href",
      "/booking/calendar/bookings/41/edit",
    );
  });

  it("keeps role-lost details readable without configuration capabilities", async () => {
    server.use(
      http.get("/api/v2/bookings/41", () =>
        HttpResponse.json({
          ...document,
          canViewConfiguration: false,
          canEdit: false,
          canCancel: false,
        }),
      ),
    );
    renderPage();

    expect(await screen.findByText("Cell imaging")).toBeVisible();
    expect(screen.getByRole("link", { name: "booking:bookings.details.returnToMyBookings" })).toHaveAttribute(
      "href",
      "/booking/my-bookings?period=upcoming",
    );
    expect(screen.queryByRole("link", { name: "Imaging lab" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "booking:bookings.actions.edit" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:calendar.file.accessibleLabel" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookings.actions.cancel" })).not.toBeInTheDocument();
  });

  it("cancels in place and keeps the same readable URL", async () => {
    let current = {
      ...document,
      version: document.version as number,
      state: document.state as "CONFIRMED" | "CANCELLED",
      canEdit: document.canEdit as boolean,
      canCancel: document.canCancel as boolean,
    };
    server.use(
      http.get("/api/v2/bookings/41", () => HttpResponse.json(current)),
      http.patch("/api/v2/bookings/41", () => {
        current = { ...current, version: 1, state: "CANCELLED", canEdit: false, canCancel: false };
        return HttpResponse.json(current);
      }),
    );
    const { router } = renderPage();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "booking:bookings.actions.cancel" }));
    const cancellationButtons = screen.getAllByRole("button", { name: "booking:bookings.actions.cancel" });
    await user.click(cancellationButtons[cancellationButtons.length - 1]);

    expect(await screen.findByText("booking:bookings.details.cancelled")).toBeVisible();
    expect(router.state.location.pathname).toBe("/booking/calendar/bookings/41");
    expect(screen.queryByRole("button", { name: "booking:bookings.actions.cancel" })).not.toBeInTheDocument();
  });

  it("conceals missing or inaccessible ids behind one unavailable state", async () => {
    server.use(http.get("/api/v2/bookings/41", () => new HttpResponse(null, { status: 404 })));
    renderPage();

    expect(await screen.findByText("booking:bookings.details.unavailableTitle")).toBeVisible();
    expect(screen.getByRole("link", { name: "booking:bookings.details.returnToMyBookings" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "common:actions.retry" })).not.toBeInTheDocument();
  });

  it("conceals a busy-only response behind the unavailable state", async () => {
    server.use(
      http.get("/api/v2/bookings/41", () =>
        HttpResponse.json({ ...document, privacy: "busy", purpose: null, bookedBy: null }),
      ),
    );
    renderPage();

    expect(await screen.findByText("booking:bookings.details.unavailableTitle")).toBeVisible();
    expect(screen.queryByRole("button", { name: "common:actions.retry" })).not.toBeInTheDocument();
  });
});
