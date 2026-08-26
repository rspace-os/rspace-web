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
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { NuqsAdapter } from "nuqs/adapters/react";
import { Suspense } from "react";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import { server } from "@/__tests__/mswServer";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import {
  bookableItemFixtures,
  bookableItemsHandlers,
  sampleBookingEvents,
} from "../bookable-items/mocks/bookableItemsMocks";
import AllBookableItemsPage from "./AllBookableItemsPage";
import { createAllBookableItemsRoute } from "./routes";

const fixedClock = () => new Date("2026-08-17T00:30:00Z");

function collectionPage(docs: readonly unknown[]) {
  return { docs, totalDocs: docs.length, totalPages: docs.length === 0 ? 0 : 1, page: 1 };
}

async function renderPage(initialEntry = "/booking/all-items?date=2026-08-17") {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rootRoute = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({ getParentRoute: () => rootRoute, path: "/booking", component: Outlet });
  const router = createRouter({
    routeTree: rootRoute.addChildren([
      bookingRoute.addChildren([
        createAllBookableItemsRoute(bookingRoute, () => <AllBookableItemsPage clock={fixedClock} />),
      ]),
    ]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });
  const wrapper = await createRealI18nWrapper({
    resources: { booking: bookingEnglish, common: commonEnglish },
    defaultNS: "common",
  });

  const result = render(
    <QueryClientProvider client={queryClient}>
      <NuqsAdapter>
        <Suspense fallback={null}>
          <RouterProvider router={router as never} />
        </Suspense>
      </NuqsAdapter>
    </QueryClientProvider>,
    { wrapper },
  );
  return { ...result, router };
}

describe("AllBookableItemsPage", () => {
  it.each([
    { name: null, globalId: "IC456" },
    { name: "Imaging lab", globalId: null },
    { name: undefined, globalId: undefined },
  ])("does not render a partial inventory location link", ({ name, globalId }) => {
    const { container } = render(<InventoryLocationLink name={name} globalId={globalId} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("shows booking availability and links to the routed booking form", async () => {
    let collectionRequest: Request | undefined;
    let bookingRequests = 0;
    const bookingDocs = [
      {
        id: 41,
        target: {
          relationTo: "instruments",
          value: { id: 123, name: "Confocal microscope", deleted: false },
          globalId: "IN123",
        },
        timezone: "Europe/Berlin",
        start: "2026-08-17T06:00:00Z",
        end: "2026-08-17T07:00:00Z",
        state: "CONFIRMED",
      },
    ];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers((request) => {
        collectionRequest = request;
      }),
      http.get("/api/v2/bookings", () => {
        bookingRequests += 1;
        return HttpResponse.json({
          docs: bookingDocs,
          totalDocs: bookingDocs.length,
          totalPages: 1,
          page: 1,
          hasNextPage: false,
        });
      }),
    );
    const { container, unmount } = await renderPage();

    expect(await screen.findByRole("heading", { name: "All Bookable Items" })).toBeVisible();
    await waitFor(() =>
      expect(
        within(screen.getByRole("table", { name: "All Bookable Items table" })).getByText("Confocal microscope"),
      ).toBeInTheDocument(),
    );
    for (const locationLink of screen.getAllByRole("link", { name: "Imaging lab" })) {
      expect(locationLink).toHaveAttribute("href", "/globalId/IC456");
    }
    for (const locationLink of screen.getAllByRole("link", { name: "Workbench" })) {
      expect(locationLink).toHaveAttribute("href", "/globalId/BE457");
    }
    for (const itemName of ["Mass spectrometer", "Microplate reader"]) {
      const row = within(screen.getByRole("table", { name: "All Bookable Items table" })).getByRole("row", {
        name: new RegExp(itemName),
      });
      expect(within(row).getAllByRole("link")).toHaveLength(2);
    }
    await waitFor(() =>
      expect(
        within(screen.getByRole("table", { name: "All Bookable Items table" })).getByRole("img", {
          name: "Confocal microscope availability",
        }),
      ).toHaveAccessibleDescription(/Booked:.*08:00.*09:00/),
    );
    const table = screen.getByRole("table", { name: "All Bookable Items table" });
    const availability = within(table).getByRole("img", { name: "Confocal microscope availability" });
    expect(availability).toHaveAccessibleDescription(/Booked:.*08:00.*09:00/);
    const where = new URL(collectionRequest?.url ?? "http://localhost").searchParams.get("where") ?? "";
    expect(where).toContain("enabled==true");
    expect(where).toContain("target.deleted==false");

    const bookLink = within(within(table).getByRole("row", { name: /Confocal microscope/ })).getByRole("link", {
      name: "Book",
    });
    expect(bookLink).toHaveAttribute("href", "/booking/calendar/bookings/add?date=2026-08-17&target=IN123");

    expect(bookingRequests).toBe(1);
    await expectAccessible(container);
    unmount();
  });

  it("shows synchronized current-time markers in quick mode", async () => {
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers(() => undefined),
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({
          ...collectionPage(sampleBookingEvents),
          hasNextPage: false,
        }),
      ),
    );
    await renderPage("/booking/all-items?date=2026-08-17&availability=available-now");

    const table = await screen.findByRole("table", { name: "All Bookable Items table" });
    const confocal = await within(table).findByRole("img", { name: "Confocal microscope availability" });
    const flowCytometer = await within(table).findByRole("img", { name: "Flow cytometer availability" });
    expect(confocal).toHaveAccessibleDescription(/Current time:/);
    expect(flowCytometer).toHaveAccessibleDescription(/Current time:/);
    const markers = within(table).getAllByTitle(/Current time:/);
    expect(markers.length).toBeGreaterThan(1);
    for (const marker of markers.slice(1)) {
      expect(marker.style.left).toBe(markers[0].style.left);
    }
  });

  it("hides unverified rows while the complete quick-filter index loads", async () => {
    let release: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.get("/api/v2/booking-configurations", async () => {
        await pending;
        return HttpResponse.json(collectionPage(bookableItemFixtures));
      }),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers(() => undefined),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
    );
    const { container, unmount } = await renderPage("/booking/all-items?date=2026-08-17&availability=available-now");

    expect(await screen.findByRole("status")).toHaveTextContent("Finding bookable items…");
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Date")).not.toBeInTheDocument();
    await expectAccessible(container);
    unmount();
    await act(async () => release?.());
  });

  it("retries a failed index and uses an impossible server filter for no matches", async () => {
    let candidateRequests = 0;
    const collectionRequests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", ({ request }) => {
        const url = new URL(request.url);
        collectionRequests.push(url);
        if (decodeURIComponent(url.searchParams.get("where") ?? "") === "enabled==true;target.deleted==false") {
          candidateRequests += 1;
          if (candidateRequests === 1) return new HttpResponse(null, { status: 500 });
          return HttpResponse.json(collectionPage(bookableItemFixtures));
        }
        return HttpResponse.json(collectionPage([]));
      }),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers(() => undefined),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
    );
    const { router } = await renderPage("/booking/all-items?date=2026-08-17&availability=free-later-today");

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not find available items.");
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(candidateRequests).toBe(2));
    await waitFor(() =>
      expect(collectionRequests.some((url) => decodeURIComponent(url.search).includes("id==-1"))).toBe(true),
    );
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(router.state.location.search.availability).toBe("free-later-today");
  });
});
