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
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { createBookableItemRoute } from "../routes";

vi.mock("@/modules/common/hooks/auth", () => ({ useOauthTokenQuery: vi.fn() }));
vi.mock("@/modules/common/queries/currentUser", () => ({ useCurrentUserQuery: vi.fn() }));

const configuration = {
  id: 7,
  target: {
    relationTo: "instruments",
    value: { id: 123, name: "Confocal microscope", deleted: false },
    globalId: "IN123",
  },
  enabled: true,
  timezone: "UTC",
  slotGranularityMinutes: 5,
  openingStart: "08:00",
  openingEnd: "17:30",
  bufferBeforeMinutes: 5,
  bufferAfterMinutes: 15,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  updatedAt: null,
};
const booking = {
  id: 41,
  target: configuration.target,
  timezone: "UTC",
  start: "2026-08-25T09:00:00Z",
  end: "2026-08-25T10:00:00Z",
  state: "CONFIRMED",
  privacy: "full",
  purpose: null,
  bookedBy: "Ada Lovelace (ada)",
  canEdit: false,
  createdAt: "2026-08-17T00:00:00Z",
  updatedAt: "2026-08-17T00:00:00Z",
};

function envelope(docs: unknown[], limit: number) {
  return {
    docs,
    totalDocs: docs.length,
    limit,
    page: 1,
    pagingCounter: 1,
    totalPages: docs.length === 0 ? 0 : 1,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}

const mockedUseOauthTokenQuery = vi.mocked(useOauthTokenQuery);
const mockedUseCurrentUserQuery = vi.mocked(useCurrentUserQuery);

beforeEach(() => {
  mockedUseOauthTokenQuery.mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
  mockedUseCurrentUserQuery.mockReturnValue({ data: { hasSysAdminRole: true } } as ReturnType<
    typeof useCurrentUserQuery
  >);
});

function renderPage(initialEntry = "/booking/bookable-items/IN123") {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const root = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const editRoute = createRoute({
    getParentRoute: () => bookingRoute,
    path: "/config/bookable-items/$id/edit",
    component: Outlet,
  });
  const router = createRouter({
    routeTree: root.addChildren([bookingRoute.addChildren([createBookableItemRoute(bookingRoute), editRoute])]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>,
  );
  return { ...result, queryClient };
}

describe("BookableItemPage", () => {
  it("renders identity, rules, role-sensitive actions, and event requests with one cutoff", async () => {
    const eventFilters: string[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", ({ request }) => {
        eventFilters.push(new URL(request.url).searchParams.get("where") ?? "");
        return HttpResponse.json(envelope([], 10));
      }),
    );
    const { container } = renderPage();

    // The item itself is the page heading now; the old "Bookable item" title is
    // the eyebrow above it.
    expect(await screen.findByRole("heading", { level: 1, name: "Confocal microscope" })).toBeVisible();
    expect(screen.getByText("IN123")).toBeVisible();
    expect(screen.getAllByText("UTC").length).toBeGreaterThan(0);
    expect(screen.getByText("08:00–17:30")).toBeVisible();
    expect(screen.getByText("booking:bookableItemDetails.unlimited")).toBeVisible();
    expect(screen.getByText("booking:bookableItemDetails.notAvailable")).toBeVisible();
    expect(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.details" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.audit" })).toBeVisible();
    // Editing is in-page now, so the action is a button rather than a link away.
    expect(screen.getByRole("button", { name: "booking:bookableItemDetails.edit" })).toBeVisible();
    await waitFor(() => expect(eventFilters).toHaveLength(2));
    const boundaries = eventFilters.map((where) => where.match(/end=(?:gt|le)=([^;]+)/)?.[1]);
    expect(boundaries[0]).toBeTruthy();
    expect(boundaries[0]).toBe(boundaries[1]);
    await expectAccessible(container);
  });

  it("saves booking rules in place and returns to the read-out", async () => {
    const user = userEvent.setup();
    const patches: unknown[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.patch("/api/v2/booking-configurations/7", async ({ request }) => {
        patches.push(await request.json());
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" }));
    // Edit mode replaces the read-out with the form, in the same card.
    const maximumDuration = await screen.findByLabelText("booking:settings.fields.maximumDuration");
    await user.clear(maximumDuration);
    await user.type(maximumDuration, "60");
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.save" }));

    await waitFor(() => expect(patches).toHaveLength(1));
    expect(patches[0]).toMatchObject({ maxBookingDurationMinutes: 60, timezone: "UTC" });
    // Back to view mode, with the Edit affordance restored.
    expect(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" })).toBeVisible();
  });

  it("cancels an edit without sending a request", async () => {
    const user = userEvent.setup();
    let patched = false;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.patch("/api/v2/booking-configurations/7", () => {
        patched = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.edit" }));
    await user.click(await screen.findByRole("button", { name: "booking:bookableItemDetails.cancelEdit" }));

    expect(await screen.findByText("booking:bookableItemDetails.unlimited")).toBeVisible();
    expect(patched).toBe(false);
  });

  it("keeps a non-administrator in view mode even on ?edit=1", async () => {
    mockedUseCurrentUserQuery.mockReturnValue({ data: { hasSysAdminRole: false } } as ReturnType<
      typeof useCurrentUserQuery
    >);
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
    );
    renderPage("/booking/bookable-items/IN123?edit=true");

    expect(await screen.findByText("booking:bookableItemDetails.unlimited")).toBeVisible();
    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.save" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookableItemDetails.edit" })).not.toBeInTheDocument();
  });

  it("loads the audit trail only once its tab is opened", async () => {
    const user = userEvent.setup();
    let auditRequests = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([configuration], 2))),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
      http.get("/api/v2/booking-configurations/7/audit", () => {
        auditRequests += 1;
        return HttpResponse.json(
          envelope(
            [
              {
                timestamp: "2026-08-25T10:42:18Z",
                username: "morgan.ellis",
                fullName: "Morgan Ellis",
                domain: "RECORD",
                action: "WRITE",
                description: "Updated booking configuration IN123",
                payload: { enabled: true, maxBookingDurationMinutes: 240 },
              },
            ],
            20,
          ),
        );
      }),
    );
    renderPage();

    await screen.findByRole("tab", { name: "booking:bookableItemDetails.tabs.audit" });
    expect(auditRequests).toBe(0);

    await user.click(screen.getByRole("tab", { name: "booking:bookableItemDetails.tabs.audit" }));

    // TableList keeps both the table and card presentations mounted and picks
    // between them with container queries, which jsdom does not evaluate, so
    // every cell matches twice.
    expect((await screen.findAllByText("Morgan Ellis"))[0]).toBeVisible();
    expect(screen.getAllByText("WRITE")[0]).toBeVisible();
    expect(screen.getAllByText("maxBookingDurationMinutes")[0]).toBeVisible();
    await waitFor(() => expect(auditRequests).toBe(1));
  });

  it("does not request events for an invalid lookup and offers a working retry", async () => {
    const user = userEvent.setup();
    let lookupFails = true;
    let eventRequests = 0;
    mockedUseCurrentUserQuery.mockReturnValue({ data: { hasSysAdminRole: false } } as ReturnType<
      typeof useCurrentUserQuery
    >);
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        lookupFails ? HttpResponse.json(envelope([], 2)) : HttpResponse.json(envelope([configuration], 2)),
      ),
      http.get("/api/v2/bookings", () => {
        eventRequests += 1;
        return HttpResponse.json(envelope([], 10));
      }),
    );
    renderPage();

    expect(await screen.findByText("booking:bookableItemDetails.error.title")).toBeVisible();
    expect(eventRequests).toBe(0);
    lookupFails = false;
    await user.click(screen.getByRole("button", { name: "common:actions.retry" }));

    expect(await screen.findByText("Confocal microscope")).toBeVisible();
    expect(screen.queryByRole("link", { name: "booking:bookableItemDetails.edit" })).not.toBeInTheDocument();
    await waitFor(() => expect(eventRequests).toBe(2));
  });

  it("marks the configuration update timestamp as machine-readable time", async () => {
    const updatedAt = "2026-08-10T12:00:00Z";
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(envelope([{ ...configuration, updatedAt }], 2)),
      ),
      http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 10))),
    );
    renderPage();

    const updatedTime = (await screen.findAllByRole("time")).find(
      (element) => element.getAttribute("datetime") === updatedAt,
    );
    expect(updatedTime).toBeVisible();
  });

  it("reformats existing events after a configuration timezone refresh without refetching events", async () => {
    let timezone = "UTC";
    let eventRequests = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(envelope([{ ...configuration, timezone }], 2)),
      ),
      http.get("/api/v2/bookings", () => {
        eventRequests += 1;
        return HttpResponse.json(envelope([booking], 10));
      }),
    );
    const { queryClient } = renderPage();

    const times = await screen.findAllByRole("time");
    const utcText = times[0].textContent;
    expect(eventRequests).toBe(2);
    timezone = "Europe/Berlin";
    await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations", "target", "IN123"] });

    await waitFor(() => expect(screen.getAllByRole("time")[0]).not.toHaveTextContent(utcText ?? ""));
    expect(eventRequests).toBe(2);
  });
});
