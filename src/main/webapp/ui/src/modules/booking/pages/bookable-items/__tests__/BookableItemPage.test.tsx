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

function renderPage() {
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
    history: createMemoryHistory({ initialEntries: ["/booking/bookable-items/IN123"] }),
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

    expect(await screen.findByRole("heading", { name: "booking:bookableItemDetails.title" })).toBeVisible();
    expect(screen.getByText("Confocal microscope")).toBeVisible();
    expect(screen.getByText("IN123")).toBeVisible();
    expect(screen.getByText("UTC")).toBeVisible();
    expect(screen.getByText("08:00–17:30")).toBeVisible();
    expect(screen.getByText("booking:bookableItemDetails.unlimited")).toBeVisible();
    expect(screen.getByText("booking:bookableItemDetails.notAvailable")).toBeVisible();
    expect(screen.getByRole("link", { name: "booking:bookableItemDetails.edit" })).toHaveAttribute(
      "href",
      "/booking/config/bookable-items/7/edit",
    );
    await waitFor(() => expect(eventFilters).toHaveLength(2));
    const boundaries = eventFilters.map((where) => where.match(/end=(?:gt|le)=([^;]+)/)?.[1]);
    expect(boundaries[0]).toBeTruthy();
    expect(boundaries[0]).toBe(boundaries[1]);
    await expectAccessible(container);
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
