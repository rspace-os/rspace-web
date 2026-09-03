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
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { NuqsAdapter } from "nuqs/adapters/react";
import { Suspense } from "react";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import { server } from "@/__tests__/mswServer";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { bookableItemFixtures, bookableItemsHandlers } from "../bookable-items/mocks/bookableItemsMocks";
import { inheritedBrowserBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import AllBookableItemsPage from "./AllBookableItemsPage";
import { createAllBookableItemsRoute } from "./routes";

const fixedClock = () => new Date("2026-08-17T08:30:00Z");

function collectionPage(docs: readonly unknown[]) {
  return { docs, totalDocs: docs.length, totalPages: docs.length === 0 ? 0 : 1, page: 1 };
}

async function renderPage(initialEntry = "/booking/all-items?date=2026-08-17") {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  const rootRoute = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({ getParentRoute: () => rootRoute, path: "/booking", component: Outlet });
  const router = createRouter({
    routeTree: rootRoute.addChildren([
      bookingRoute.addChildren([
        createAllBookableItemsRoute(bookingRoute, () => <AllBookableItemsPage clock={fixedClock} userTimeZone="UTC" />),
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

  it("retries a failed index and hides catalogue rows that do not match", async () => {
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
      ...bookableItemsHandlers((request) => collectionRequests.push(new URL(request.url))),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
    );
    const { router } = await renderPage("/booking/all-items?date=2026-08-17&availability=free-later-today");

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not find available items.");
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(candidateRequests).toBe(2));
    expect(collectionRequests.some((url) => url.pathname === "/api/v2/booking-catalogue")).toBe(true);
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(router.state.location.search.availability).toBe("free-later-today");
  });
});
