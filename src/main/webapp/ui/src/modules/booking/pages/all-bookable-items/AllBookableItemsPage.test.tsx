import { setupRealAppI18n } from "@/__tests__/helpers/realI18n";

setupRealAppI18n();

import "@/__tests__/__mocks__/matchMedia";
import { createMemoryHistory, createRootRoute, createRoute, createRouter, Outlet } from "@tanstack/react-router";
import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { allBookableItemsSearch } from "@/modules/booking/pages/all-bookable-items/search";
import {
  allDayBookableItemCandidates,
  bookableItemFixtures,
  bookableItemsHandlers,
  bookingCataloguePage,
  collectionPage,
} from "../bookable-items/mocks/bookableItemsMocks";
import { renderBookingTestPage } from "../calendar/__tests__/calendarTestHarness";
import AllBookableItemsPage from "./AllBookableItemsPage";

const fixedClock = () => new Date("2026-08-17T08:30:00Z");

function catalogueItem(configuration: (typeof bookableItemFixtures)[number]) {
  const target = configuration.target;
  if (!target) throw new Error("Expected a bookable item fixture with a target");
  return {
    configurationId: configuration.id,
    configurationVersion: configuration.configurationVersion,
    targetType: "INSTRUMENT",
    targetId: target.value.id,
    globalId: target.globalId,
    name: target.value.name,
    timezone: configuration.timezone,
    slotGranularityMinutes: configuration.slotGranularityMinutes,
    openingHours: configuration.openingHours,
    bufferBeforeMinutes: configuration.bufferBeforeMinutes,
    bufferAfterMinutes: configuration.bufferAfterMinutes,
    maxBookingDurationMinutes: configuration.maxBookingDurationMinutes,
    allowDoubleBooking: configuration.allowDoubleBooking,
    effectiveRole: configuration.effectiveRole,
    capabilities: configuration.capabilities,
    location:
      target.value.parentContainerName && target.value.parentContainerGlobalId
        ? { name: target.value.parentContainerName, globalId: target.value.parentContainerGlobalId }
        : null,
  };
}

async function renderPage(initialEntry = "/booking/all-items?date=2026-08-17") {
  const rootRoute = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({ getParentRoute: () => rootRoute, path: "/booking", component: Outlet });
  const router = createRouter({
    routeTree: rootRoute.addChildren([
      bookingRoute.addChildren([
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/all-items/",
          validateSearch: allBookableItemsSearch,
          component: () => <AllBookableItemsPage clock={fixedClock} />,
        }),
      ]),
    ]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });
  return renderBookingTestPage(router);
}

describe("AllBookableItemsPage", () => {
  it("retains every advanced rule through apply, quick-filter changes, and date navigation", async () => {
    const user = userEvent.setup();
    const original = "id=ge=7;id=le=7;target==IN123;target==IN123";
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers(() => undefined),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
    );
    const { router } = await renderPage(`/booking/all-items?where=${encodeURIComponent(original)}`);
    expect((await screen.findAllByText("Confocal microscope"))[0]).toBeVisible();
    expect(screen.queryByText("Electron microscope")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /^Filters/ }));
    expect(screen.getByRole("button", { name: "Remove filter 4" })).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Apply filters" }));
    expect(router.state.location.search.where).toBe(original);
    await user.click(screen.getByRole("button", { name: "Available now" }));
    await waitFor(() => expect(router.state.location.search.where).toBe(`${original};availability==available-now`));
    await user.click(screen.getByRole("button", { name: "Next day" }));
    await waitFor(() => expect(router.state.location.search.where).toBe(original));
  });

  it.each(["availability=available-now&pageSize=50", "date=2026-08-18&target=IN123&q=microscope&page=2&pageSize=50"])(
    "resets the full controlled view with one navigation: %s",
    async (search) => {
      const user = userEvent.setup();
      server.use(
        http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
        http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([]))),
        ...bookableItemsHandlers(() => undefined),
        http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      );
      const { router } = await renderPage(`/booking/all-items?${search}`);
      const reset = await screen.findByRole("button", { name: "Reset filters, sorting, and columns to defaults" });
      const navigate = vi.spyOn(router, "navigate");
      try {
        await user.click(reset);
        await waitFor(() => expect(router.state.location.search).toEqual({ pageSize: 50 }));
        expect(navigate).toHaveBeenCalledOnce();
      } finally {
        navigate.mockRestore();
      }
    },
  );

  it("keeps the selected page size in the URL and catalogue request", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([]))),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(bookingCataloguePage([], 1, Number(url.searchParams.get("limit")), 0));
      }),
    );
    const { router } = await renderPage();
    const size = await screen.findByRole("combobox", { name: "Rows per page" });
    await user.selectOptions(size, "50");
    await waitFor(() => expect(size).toHaveValue("50"));
    expect(router.state.location.search).toMatchObject({ pageSize: 50 });
    await waitFor(() => expect(requests.at(-1)?.searchParams.get("limit")).toBe("50"));
    await user.selectOptions(size, "10");
    await waitFor(() => expect(size).toHaveValue("10"));
    await waitFor(() => expect(requests.at(-1)?.searchParams.get("limit")).toBe("10"));
  });

  it("preserves saved OR matches outside the availability index when catalogue requests are bounded", async () => {
    const candidateConfiguration = bookableItemFixtures[0];
    const candidateItem = catalogueItem(candidateConfiguration);
    const outsideCandidateItem = catalogueItem(bookableItemFixtures[1]);
    const requests: URL[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([candidateConfiguration]))),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        const where = url.searchParams.get("where") ?? "";
        if (where.includes(",") && where.includes("target=in=(") && where.includes("target==IN124")) {
          return new HttpResponse(null, { status: 414 });
        }
        const docs =
          where.includes("target==IN124") && !where.includes("target=in=(")
            ? [outsideCandidateItem]
            : where.includes("target=in=(") && !where.includes("target==IN124")
              ? [candidateItem]
              : [];
        const page = Number(url.searchParams.get("page"));
        const pageSize = Number(url.searchParams.get("limit"));
        return HttpResponse.json({
          items: docs,
          page,
          pageSize,
          total: docs.length,
          facets: { types: ["INSTRUMENT"] },
        });
      }),
    );

    await renderPage(`/booking/all-items?where=${encodeURIComponent("availability==available-now,target==IN124")}`);

    await waitFor(() => expect(requests).toHaveLength(3));
    expect(requests.map((request) => request.searchParams.get("where"))).toEqual([
      "target==IN124",
      "target=in=(IN123)",
      "target==IN124;target=in=(IN123)",
    ]);
    expect(await screen.findAllByText("Confocal microscope")).not.toHaveLength(0);
    expect(await screen.findAllByText("Electron microscope")).not.toHaveLength(0);
  });

  it("shows both availability counts before selecting a filter and after clearing it", async () => {
    const candidates = allDayBookableItemCandidates();
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage(candidates))),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers(() => undefined),
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({
          ...collectionPage([
            {
              id: 999,
              version: 0,
              target: candidates[0].target,
              timezone: "UTC",
              start: "2026-08-17T08:00:00Z",
              end: "2026-08-17T10:00:00Z",
              state: "CONFIRMED",
              kind: "BOOKING",
              privacy: "busy",
              purpose: null,
              bookedBy: null,
              canEdit: false,
              canCancel: false,
              createdAt: "2026-08-17T08:00:00Z",
              updatedAt: "2026-08-17T08:00:00Z",
            },
          ]),
          hasNextPage: false,
        }),
      ),
    );
    await renderPage("/booking/all-items?date=2026-08-20");
    const available = await screen.findByRole("button", { name: "Available now" });
    const later = screen.getByRole("button", { name: "Free later today" });
    await waitFor(() => expect(available).toHaveTextContent("Available now1"));
    expect(later).toHaveTextContent("Free later today1");
    for (const link of screen.getAllByRole("link", { name: "Book" })) {
      expect(link.getAttribute("href")).toContain("date=2026-08-20");
    }
    await userEvent.click(available);
    await userEvent.click(available);
    await waitFor(() => expect(available).toHaveAttribute("aria-pressed", "false"));
    expect(available).toHaveTextContent("Available now1");
    expect(later).toHaveTextContent("Free later today1");
  });

  it.each([false, true])(
    "only blocks rows for an active quick filter while counts load (active: %s)",
    async (active) => {
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
      const { container, unmount } = await renderPage(
        `/booking/all-items?date=2026-08-17${active ? "&availability=available-now" : ""}`,
      );

      if (active) {
        expect(await screen.findByText("Finding bookable items…")).toHaveAttribute("role", "status");
        expect(screen.getByRole("status")).toHaveClass("sr-only");
        expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
      } else {
        expect((await screen.findAllByText("Confocal microscope"))[0]).toBeVisible();
      }
      expect(screen.getByRole("button", { name: "Jump to date" })).toBeVisible();
      await expectAccessible(container);
      unmount();
      await act(async () => release?.());
    },
  );

  it("retries a failed index and hides catalogue rows that do not match", async () => {
    let candidateRequests = 0;
    server.use(
      http.get("/api/v2/booking-configurations", ({ request }) => {
        const url = new URL(request.url);
        if (
          decodeURIComponent(url.searchParams.get("where") ?? "") ===
          "enabled==true;state==ACTIVE;target.deleted==false"
        ) {
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
    expect(screen.queryByText("Finding bookable items…")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(candidateRequests).toBe(2));
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(router.state.location.search.availability).toBe("free-later-today");
  });
});
