import { setupRealAppI18n } from "@/__tests__/helpers/realI18n";

setupRealAppI18n();

import "@/__tests__/__mocks__/matchMedia";
import { createMemoryHistory, createRootRoute, createRoute, createRouter, Outlet } from "@tanstack/react-router";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import { MemoryHistoryNuqsAdapter as NuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import { allBookableItemsSearch } from "@/modules/booking/pages/all-bookable-items/search";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import { currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import {
  allDayBookableItemCandidates,
  bookableItemFixtures,
  bookableItemsHandlers,
  bookableItemsOpenApi,
  bookingCataloguePage,
  collectionPage,
  sampleBookingEvents,
} from "../bookable-items/mocks/bookableItemsMocks";
import { renderBookingTestPage } from "../calendar/__tests__/calendarTestHarness";
import { collectionResponse, ownBooking } from "../mocks/bookingCalendarFixtures";
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
    location: target.value.parentContainer?.value ?? null,
  };
}

function candidatePage(docs: readonly Omit<BookingConfiguration, "roleSources">[]) {
  return {
    items: docs.map((doc) => ({
      ...doc,
      configurationId: doc.id,
      targetType: "INSTRUMENT",
      targetId: doc.target?.value.id,
      globalId: doc.target?.globalId,
      name: doc.target?.value.name,
      location: null,
    })),
    page: 1,
    pageSize: 100,
    total: docs.length,
    facets: { types: ["INSTRUMENT"] },
  };
}

function candidateHandler(resolver: Parameters<typeof http.get>[1]) {
  return http.get("/api/v2/booking-catalogue", (info) =>
    new URL(info.request.url).searchParams.get("limit") === "100" ? resolver(info) : undefined,
  );
}

async function renderPage(initialEntry = "/booking/all-items?date=2026-08-17") {
  const rootRoute = createRootRoute({
    component: () => (
      <NuqsAdapter>
        <Outlet />
      </NuqsAdapter>
    ),
  });
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
  return renderBookingTestPage(router, (queryClient) => queryClient.setQueryData(currentUserQueryKeys.me(), { id: 1 }));
}

async function renderMutationPages(initialEntry: string) {
  const rootRoute = createRootRoute({
    component: () => (
      <BookingCreationStoreProvider>
        <NuqsAdapter>
          <Outlet />
        </NuqsAdapter>
      </BookingCreationStoreProvider>
    ),
  });
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
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/bookable-items/$globalId/{-$tab}/",
          validateSearch: bookableItemSearch,
          component: BookableItemPage,
        }),
      ]),
    ]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });
  return renderBookingTestPage(router, (queryClient) => {
    queryClient.setQueryData(currentUserQueryKeys.me(), currentUser);
    queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  });
}

describe("AllBookableItemsPage", () => {
  it.each([{ name: "Catalogue lab", globalId: "IC999" }, null])(
    "uses the catalogue location in the table without repeating it in booking availability details: %s",
    async (location) => {
      const user = userEvent.setup();
      const item = { ...catalogueItem(bookableItemFixtures[0]), location };
      server.use(...bookableItemsHandlers(() => undefined));
      server.use(
        http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
        http.get("/api/v2/booking-catalogue", () => HttpResponse.json(bookingCataloguePage([item], 1, 20))),
        http.get("/api/v2/bookings", () =>
          HttpResponse.json({
            ...collectionPage(
              sampleBookingEvents.slice(0, 1).map((booking) => ({
                ...booking,
                kind: "BOOKING",
                canEdit: false,
                canCancel: false,
                createdAt: "2026-08-01T09:00:00Z",
                updatedAt: "2026-08-01T09:00:00Z",
              })),
            ),
            hasNextPage: false,
          }),
        ),
      );
      await renderPage();
      await screen.findAllByRole("img", { name: "Confocal microscope availability" });
      if (location) {
        expect(screen.getAllByRole("link", { name: location.name })[0]).toHaveAttribute("href", "/globalId/IC999");
      } else {
        expect(screen.queryByRole("link", { name: "Imaging lab" })).not.toBeInTheDocument();
      }
      await user.click(screen.getAllByRole("button", { name: /^Confocal microscope, Booked,/ })[0]);
      const dialog = await screen.findByRole("dialog");
      await user.click(within(dialog).getByLabelText(/^Show details for Confocal microscope,/));
      expect(within(dialog).queryByRole("link", { name: location?.name ?? "Imaging lab" })).not.toBeInTheDocument();
      expect(within(dialog).getByRole("link", { name: "Details" })).toHaveAttribute(
        "href",
        "/booking/calendar/bookings/41",
      );
    },
  );

  it("shares today's booking request between availability counts and bars", async () => {
    const bookings = vi.fn(() => HttpResponse.json({ ...collectionPage([]), hasNextPage: false }));
    server.use(
      candidateHandler(() => HttpResponse.json(candidatePage(bookableItemFixtures))),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/bookings", bookings),
      ...bookableItemsHandlers(() => undefined),
    );
    await renderPage();
    await screen.findAllByRole("link", { name: "Book" });
    await waitFor(() => expect(screen.getByRole("button", { name: "Available now" })).toHaveTextContent(/\d/));
    expect(bookings).toHaveBeenCalledTimes(1);
  });

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
    expect(await screen.findByRole("combobox", { name: "Value for filter 3" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Remove filter 4" })).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Apply filters" }));
    expect(router.state.location.search.where).toBe(original);
    await user.click(screen.getByRole("button", { name: "Available now" }));
    await waitFor(() => expect(router.state.location.search.where).toBe(`${original};availability==available-now`));
    await user.click(screen.getByRole("button", { name: "Next day" }));
    await waitFor(() => expect(router.state.location.search.where).toBe(original));
  });

  it("hydrates runtime property filters before parsing and querying the catalogue", async () => {
    const user = userEvent.setup();
    const original = "target.customFields.SF152==BSL-2";
    const requests: URL[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json({
          items: [],
          page: 1,
          pageSize: 20,
          total: 0,
          facets: { types: ["INSTRUMENT"] },
        });
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );
    await renderPage(`/booking/all-items?where=${encodeURIComponent(original)}`);

    await waitFor(() => expect(requests.at(-1)?.searchParams.get("where")).toBe(original));
    await user.click(await screen.findByRole("button", { name: /^Filters/ }));
    expect(
      await screen.findByRole("combobox", { name: "Search Bookable item custom fields for filter 1" }),
    ).toHaveValue("Hazard class (Cell line template · SF152)");
  });

  it("blocks the catalogue and preserves an unavailable runtime filter for explicit removal", async () => {
    const original = "target.customFields.SF999==missing";
    const requests: URL[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json({ items: [], page: 1, pageSize: 20, total: 0, facets: { types: ["INSTRUMENT"] } });
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );
    const { router } = await renderPage(`/booking/all-items?where=${encodeURIComponent(original)}&q=scope`);

    const issue = await screen.findByRole("alert");
    expect(issue).toHaveTextContent("invalid or unavailable field");
    expect(issue).toHaveTextContent(original);
    expect(requests).toHaveLength(0);
    await userEvent.click(screen.getByRole("button", { name: "Reset saved view" }));
    await waitFor(() => expect(router.state.location.search.where).toBeUndefined());
    expect(router.state.location.search.q).toBe("scope");
  });

  it.each([
    "availability=available-now&pageSize=50",
    "types=NOTATYPE&pageSize=50",
    "date=2026-08-18&target=IN123&q=microscope&page=2&pageSize=50&types=INSTRUMENT",
  ])("resets the full controlled view with one navigation: %s", async (search) => {
    const user = userEvent.setup();
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      candidateHandler(() => HttpResponse.json(candidatePage([]))),
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
  });

  it("keeps type filters and the selected page size in the URL and catalogue request", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(bookableItemsOpenApi)),
      candidateHandler(() => HttpResponse.json(candidatePage([]))),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(bookingCataloguePage([], 1, Number(url.searchParams.get("limit")), 0));
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
    );
    const { router } = await renderPage("/booking/all-items?types=INSTRUMENT");
    const size = await screen.findByRole("combobox", { name: "Rows per page" });
    await user.selectOptions(size, "50");
    await waitFor(() => expect(size).toHaveValue("50"));
    expect(router.state.location.search).toMatchObject({ pageSize: 50, types: ["INSTRUMENT"] });
    await waitFor(() => expect(requests.at(-1)?.searchParams.get("limit")).toBe("50"));
    expect(requests.at(-1)?.searchParams.getAll("type")).toEqual(["INSTRUMENT"]);
    await user.selectOptions(size, "10");
    await waitFor(() => expect(size).toHaveValue("10"));
    await waitFor(() => expect(requests.at(-1)?.searchParams.get("limit")).toBe("10"));
  });

  it("retains selected types across availability residual retries and baseline pagination", async () => {
    const candidates = allDayBookableItemCandidates();
    const candidateItems = bookableItemFixtures.slice(0, 2).map(catalogueItem);
    const baselineItems = Array.from({ length: 101 }, (_, index) => ({
      ...catalogueItem(bookableItemFixtures[2]),
      configurationId: 1000 + index,
      targetId: 2000 + index,
      globalId: `IN${2000 + index}`,
      name: `Scoped baseline ${1000 + index}`,
    }));
    const requests: URL[] = [];
    let oversizedRequests = 0;
    server.use(
      http.get("/api/v2/openapi.json", () => HttpResponse.json(bookableItemsOpenApi)),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        if (!url.searchParams.has("where")) return HttpResponse.json(candidatePage(candidates));
        requests.push(url);
        const where = url.searchParams.get("where") ?? "";
        const targetIds = where.split("target=in=(")[1]?.split(")")[0]?.split(",") ?? [];
        if (targetIds.length === 2 && !where.includes("id=ge=1000")) {
          oversizedRequests += 1;
          return new HttpResponse(null, { status: 414 });
        }
        const items =
          targetIds.length === 0
            ? baselineItems
            : where.includes("id=ge=1000")
              ? []
              : candidateItems.filter((item) => targetIds.includes(item.globalId));
        const page = Number(url.searchParams.get("page"));
        const pageSize = Number(url.searchParams.get("limit"));
        return HttpResponse.json(
          bookingCataloguePage(items.slice((page - 1) * pageSize, page * pageSize), page, pageSize, items.length),
        );
      }),
    );

    await renderPage(
      `/booking/all-items?types=INSTRUMENT&page=3&pageSize=50&where=${encodeURIComponent("availability==available-now,id=ge=1000")}`,
    );

    expect((await screen.findAllByText("Scoped baseline 1100"))[0]).toBeVisible();
    expect(oversizedRequests).toBe(1);
    expect(requests.some((url) => url.searchParams.get("page") === "2")).toBe(true);
    expect(requests.some((url) => url.searchParams.get("where") === "id=ge=1000;target=in=(IN123)")).toBe(true);
    expect(requests.some((url) => url.searchParams.get("where") === "target=in=(IN124)")).toBe(true);
    expect(requests.every((url) => url.searchParams.getAll("type").join(",") === "INSTRUMENT")).toBe(true);
  });

  it("preserves saved OR matches outside the availability index when catalogue requests are bounded", async () => {
    const candidateConfiguration = bookableItemFixtures[0];
    const candidateItem = catalogueItem(candidateConfiguration);
    const outsideCandidateItem = catalogueItem(bookableItemFixtures[1]);
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/openapi.json", () => HttpResponse.json(bookableItemsOpenApi)),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        if (!url.searchParams.has("where")) return HttpResponse.json(candidatePage([candidateConfiguration]));
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
      candidateHandler(() => HttpResponse.json(candidatePage(candidates))),
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
        candidateHandler(async () => {
          await pending;
          return HttpResponse.json(candidatePage(bookableItemFixtures));
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
      candidateHandler(({ request }) => {
        const url = new URL(request.url);
        if (!url.searchParams.get("where")) {
          candidateRequests += 1;
          if (candidateRequests <= 4) return new HttpResponse(null, { status: 500 });
          return HttpResponse.json(candidatePage(bookableItemFixtures));
        }
        return HttpResponse.json(candidatePage([]));
      }),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers(() => undefined),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
    );
    const { router } = await renderPage("/booking/all-items?date=2026-08-17&availability=free-later-today");

    expect(await screen.findByRole("alert", {}, { timeout: 10_000 })).toMatchTextContent(
      "Could not find available items.",
    );
    expect(screen.queryByText("Finding bookable items…")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(candidateRequests).toBe(5));
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(router.state.location.search.availability).toBe("free-later-today");
  }, 15_000);

  it("refreshes quick-filter availability after a successful configuration save in the stale window", async () => {
    const user = userEvent.setup();
    let allowDoubleBooking = false;
    let configurationVersion: number = bookableItemFixtures[0].configurationVersion;
    let configuration = {
      ...bookableItemFixtures[0],
      roleSources: [...bookableItemFixtures[0].roleSources],
      allowDoubleBooking,
      configurationVersion,
    };
    const patches: unknown[] = [];
    const candidateRequests: string[] = [];
    const catalogueItem = () => ({
      configurationId: configuration.id,
      configurationVersion: configuration.configurationVersion,
      targetType: "INSTRUMENT",
      targetId: configuration.target.value.id,
      globalId: configuration.target.globalId,
      name: configuration.target.value.name,
      timezone: configuration.timezone,
      slotGranularityMinutes: configuration.slotGranularityMinutes,
      openingHours: configuration.openingHours,
      bufferBeforeMinutes: configuration.bufferBeforeMinutes,
      bufferAfterMinutes: configuration.bufferAfterMinutes,
      maxBookingDurationMinutes: configuration.maxBookingDurationMinutes,
      allowDoubleBooking: configuration.allowDoubleBooking,
      effectiveRole: configuration.effectiveRole,
      capabilities: configuration.capabilities,
      location: null,
    });
    const startedAt = Date.now();
    server.use(
      http.patch("/api/v2/booking-configurations/7", async ({ request }) => {
        patches.push(await request.json());
        allowDoubleBooking = true;
        configurationVersion = 1;
        configuration = { ...configuration, allowDoubleBooking, configurationVersion };
        return new HttpResponse(null, { status: 204 });
      }),
      http.get("/api/v2/booking-configurations", ({ request }) => {
        const where = new URL(request.url).searchParams.get("where") ?? "";
        return where.includes("IN123") ? HttpResponse.json(collectionPage([configuration], 2)) : undefined;
      }),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        const where = url.searchParams.get("where") ?? "";
        if (url.searchParams.get("limit") === "100" && url.searchParams.get("where") === null) {
          candidateRequests.push(where);
        }
        const items = where.includes("id==0") ? [] : [catalogueItem()];
        const page = Number(url.searchParams.get("page") ?? "1");
        const pageSize = Number(url.searchParams.get("limit") ?? "20");
        return HttpResponse.json(bookingCataloguePage(items, page, pageSize, items.length));
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers(() => undefined),
    );
    const { router } = await renderMutationPages("/booking/all-items?date=2026-08-17&availability=available-now");

    expect((await screen.findAllByText("No records found"))[0]).toBeVisible();
    expect(candidateRequests).toHaveLength(1);
    await act(async () => {
      await router.navigate({
        to: "/booking/bookable-items/$globalId/{-$tab}",
        params: { globalId: "IN123", tab: "details" },
        search: { edit: true },
      });
    });
    await user.click(
      await screen.findByRole("checkbox", { name: bookingEnglish.bookableItemDetails.fields.doubleBooking }),
    );
    await user.click(screen.getByRole("button", { name: bookingEnglish.bookableItems.actions.save }));
    await waitFor(() => expect(patches).toHaveLength(1));
    expect(patches[0]).toMatchObject({ allowDoubleBooking: true });
    await waitFor(() => expect(router.state.location.search.edit).toBeUndefined());

    await act(async () => {
      await router.navigate({
        to: "/booking/all-items",
        search: { date: "2026-08-17", availability: "available-now" },
      });
    });

    await waitFor(() => expect(candidateRequests).toHaveLength(2));
    expect((await screen.findAllByText("Confocal microscope"))[0]).toBeVisible();
    expect(Date.now() - startedAt).toBeLessThan(30_000);
  });
});
