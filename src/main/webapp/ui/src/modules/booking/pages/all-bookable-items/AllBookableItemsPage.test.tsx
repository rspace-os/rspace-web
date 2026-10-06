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
import { type ReactNode, Suspense } from "react";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import { server } from "@/__tests__/mswServer";
import { useCreateBooking } from "@/modules/booking/creation/useCreateBooking";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import {
  bookableItemFixtures,
  bookableItemsHandlers,
  bookableItemsOpenApi,
  bookerBookingAccess,
} from "../bookable-items/mocks/bookableItemsMocks";
import { DeleteBookingDialog } from "../bookings/DeleteBookingDialog";
import { inheritedBrowserBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import AllBookableItemsPage from "./AllBookableItemsPage";
import { createAllBookableItemsRoute } from "./routes";

const fixedClock = () => new Date("2026-08-17T08:30:00Z");

function collectionPage(docs: readonly unknown[]) {
  return { docs, totalDocs: docs.length, totalPages: docs.length === 0 ? 0 : 1, page: 1 };
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

function catalogueHandler(resolver: Parameters<typeof http.get>[1]) {
  return http.get("/api/v2/booking-catalogue", resolver);
}

function countsHandler(
  onRequest: (url: URL) => void = () => undefined,
  counts = { availableNow: 0, freeLaterToday: 0 },
) {
  return http.get("/api/v2/booking-catalogue/availability-counts", ({ request }) => {
    onRequest(new URL(request.url));
    return HttpResponse.json(counts);
  });
}

const pageRequest = (request: URL) => `${request.searchParams.get("page")}/${request.searchParams.get("limit")}`;

/** The server publishes user fields without titles and Location as a picker-backed identity filter. */
function filterMetadataOpenApi() {
  const document = structuredClone(bookableItemsOpenApi);
  const where = document.paths["/api/v2/booking-configurations"].get.parameters.find(
    (parameter) => parameter.name === "where",
  ) as {
    "x-rspace-filter": { selectors: Record<string, unknown> };
    "x-rspace-relationship-fields": Record<string, unknown>;
  };
  const text = { schema: { type: "string" }, operators: ["==", "=contains=", "=in=", "=like="], wildcards: true };
  where["x-rspace-filter"].selectors.location = {
    schema: { type: "string" },
    operators: ["==", "!=", "=in=", "=out=", "=exists="],
    wildcards: false,
    picker: { resource: "booking-locations", identity: "globalId", globalIdPrefix: "IC" },
  };
  for (const selector of ["createdBy.firstName", "updatedBy.email"]) {
    where["x-rspace-filter"].selectors[selector] = text;
    where["x-rspace-relationship-fields"][selector] = text;
  }
  where["x-rspace-relationship-fields"]["location.name"] = {
    schema: { type: "string" },
    operators: [],
    wildcards: false,
    title: "Name",
  };
  return document;
}

const readableLocations = [
  { globalId: "IC12", name: "Cold room" },
  { globalId: "BE7", name: "WB ada" },
];

function BookingMutationTrigger() {
  const mutation = useCreateBooking("new-token");
  return (
    <button
      type="button"
      onClick={() =>
        void mutation.mutateAsync({
          target: {
            configurationId: 7,
            targetId: 123,
            globalId: "IN123",
            name: "Confocal microscope",
            timezone: "Europe/Berlin",
            slotGranularityMinutes: 5,
            openingStart: "00:00",
            openingEnd: "24:00",
            openDays: [1, 2, 3, 4, 5, 6, 7],
            openingExceptions: [],
            bufferBeforeMinutes: 0,
            bufferAfterMinutes: 0,
            maxBookingDurationMinutes: 0,
            allowDoubleBooking: false,
          },
          window: { start: "2026-08-17T09:00:00Z", end: "2026-08-17T10:00:00Z" },
          purpose: null,
          eventKind: "BOOKING",
          returnDate: "2026-08-17",
        })
      }
    >
      {bookingEnglish.bookings.form.submit}
    </button>
  );
}

const sysadmin = { id: 1, hasSysAdminRole: true, session: { operatedAs: false } };

async function renderPage(
  initialEntry = "/booking/all-items?date=2026-08-17",
  extra?: ReactNode,
  currentUser: object = { id: 1 },
) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  queryClient.setQueryData(currentUserQueryKeys.me(), currentUser);
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
      {extra}
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
  it("offers item creation outside administration", async () => {
    server.use(
      catalogueHandler(() => HttpResponse.json(candidatePage(bookableItemFixtures))),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );

    await renderPage();

    expect(await screen.findByRole("link", { name: "Add" })).toHaveAttribute("href", "/booking/bookable-items/add");
  });

  it("offers no Add when the user has no instrument to set up", async () => {
    let targetsRequested = false;
    server.use(
      http.get("/api/v2/booking-configuration-targets", () => {
        targetsRequested = true;
        return HttpResponse.json([]);
      }),
      catalogueHandler(() => HttpResponse.json(candidatePage([]))),
      http.get("/api/v2/booking-catalogue", () => HttpResponse.json(candidatePage([]))),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );

    await renderPage();

    await screen.findAllByText(/Instruments from Inventory become bookable/);
    await vi.waitFor(() => expect(targetsRequested).toBe(true));
    await vi.waitFor(() => expect(screen.queryByRole("link", { name: "Add" })).not.toBeInTheDocument());
    expect(screen.queryByRole("link", { name: "Add Bookable Item" })).not.toBeInTheDocument();
  });

  it("explains how instruments become bookable when the user has none", async () => {
    server.use(
      catalogueHandler(() => HttpResponse.json(candidatePage([]))),
      http.get("/api/v2/booking-catalogue", () => HttpResponse.json(candidatePage([]))),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );

    await renderPage();

    const [primer] = await screen.findAllByText(/Instruments from Inventory become bookable/);
    expect(primer).toHaveTextContent("Access follows the instrument's Inventory sharing.");
    const [addLink] = screen.getAllByRole("link", { name: "Add Bookable Item" });
    expect(addLink).toHaveAttribute("href", "/booking/bookable-items/add");
  });

  it("keeps the generic empty text when a search hides every item", async () => {
    server.use(
      http.get("/api/v2/booking-catalogue", () => HttpResponse.json(candidatePage([]))),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );

    await renderPage("/booking/all-items?date=2026-08-17&q=nothing");

    expect((await screen.findAllByText(commonEnglish.tableList.empty.title))[0]).toBeVisible();
    expect(screen.queryByText(/Instruments from Inventory become bookable/)).not.toBeInTheDocument();
  });

  it("labels the Inventory location and explains workbench names", async () => {
    server.use(
      http.get("/api/v2/booking-catalogue", () =>
        HttpResponse.json({
          ...candidatePage([bookableItemFixtures[0]]),
          items: candidatePage([bookableItemFixtures[0]]).items.map((item) => ({
            ...item,
            location: { name: "WB user1a", globalId: "BE1" },
          })),
        }),
      ),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );

    const { container } = await renderPage();

    const [location] = await screen.findAllByRole("link", { name: "WB user1a" });
    expect(location).toHaveAttribute("href", "/globalId/BE1");
    expect(location.parentElement).toHaveTextContent(/^WB user1a$/);
    await waitFor(() => expect(screen.getByRole("button", { name: /^Available now/ })).toHaveTextContent(/\d/));
    await expectAccessible(container);
  });

  it("subscribes selected owned and readable non-owned items in bulk", async () => {
    const user = userEvent.setup();
    let updateBody: unknown;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const page = candidatePage([
          bookableItemFixtures[0],
          {
            ...bookableItemFixtures[1],
            ...bookerBookingAccess,
            capabilities: { ...bookerBookingAccess.capabilities, canManageNotificationSubscription: true },
          },
        ]);
        return HttpResponse.json({ ...page, pageSize: Number(new URL(request.url).searchParams.get("limit")) });
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      http.put("/api/v2/users/me/booking-notification-subscriptions", async ({ request }) => {
        const body = (await request.json()) as { configurationIds: number[]; enabled: boolean };
        updateBody = body;
        return HttpResponse.json(
          body.configurationIds.map((configurationId) => ({
            configurationId,
            enabled: body.enabled,
            version: 1,
            createdEnabled: true,
            cancelledEnabled: true,
            emailEnabled: false,
          })),
        );
      }),
      ...bookableItemsHandlers(() => undefined),
    );
    renderPage();

    const confocalSelection = await screen.findAllByRole("checkbox", { name: "Select Confocal microscope" });
    const electronSelection = await screen.findAllByRole("checkbox", { name: "Select Electron microscope" });
    await user.click(confocalSelection[0]);
    await user.click(electronSelection[0]);
    await user.click(screen.getByRole("button", { name: "Subscribe" }));

    await waitFor(() => expect(updateBody).toEqual({ configurationIds: [7, 8], enabled: true }));
    expect(await screen.findByText("Subscribed to 2 instruments.")).toBeVisible();
    expect(screen.queryByRole("columnheader", { name: "My notifications" })).not.toBeInTheDocument();
  });

  it("keeps the selection across pages and archives rows from both pages in one request", async () => {
    const user = userEvent.setup();
    const archiveRequests: string[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"));
        return HttpResponse.json({
          ...candidatePage([bookableItemFixtures[page === 2 ? 1 : 0]]),
          page,
          pageSize: 20,
          total: 40,
        });
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      http.delete("/api/v2/booking-configurations", ({ request }) => {
        archiveRequests.push(new URL(request.url).searchParams.get("where") ?? "");
        return HttpResponse.json({ docs: [{ id: 7 }, { id: 8 }] });
      }),
      ...bookableItemsHandlers(() => undefined),
    );
    await renderPage(undefined, undefined, sysadmin);

    await user.click((await screen.findAllByRole("checkbox", { name: "Select Confocal microscope" }))[0]);
    await user.click(screen.getByRole("button", { name: "Next page" }));
    await user.click((await screen.findAllByRole("checkbox", { name: "Select Electron microscope" }))[0]);
    const selectionBar = screen.getByRole("region", { name: "Selected rows actions" });
    expect(within(selectionBar).getByText("2 rows selected")).toBeVisible();
    // Every listed item is already enabled, so only the changes that can apply are offered.
    expect(within(selectionBar).getByRole("button", { name: "Disable" })).toBeVisible();
    expect(within(selectionBar).queryByRole("button", { name: "Enable" })).not.toBeInTheDocument();

    await user.click(within(selectionBar).getByRole("button", { name: "Archive selected" }));
    const dialog = screen.getByRole("alertdialog", { name: "Archive 2 booking configurations?" });
    await user.click(within(dialog).getByRole("button", { name: "Archive" }));

    await waitFor(() => expect(archiveRequests).toEqual(["id=in=(7,8)"]));
    expect(
      await screen.findByText(
        "2 items are now archived. Their future bookings, if any, were cancelled and will not return when they are restored.",
      ),
    ).toBeVisible();
    await waitFor(() =>
      expect(screen.queryByRole("region", { name: "Selected rows actions" })).not.toBeInTheDocument(),
    );
  });

  it("offers a regular user the lifecycle actions their role allows, without sysadmin bulk changes", async () => {
    const user = userEvent.setup();
    const archiveRequests: Array<{ url: string; ifMatch: string | null }> = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue", () =>
        HttpResponse.json(
          candidatePage([bookableItemFixtures[0], { ...bookableItemFixtures[1], ...bookerBookingAccess }]),
        ),
      ),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      http.delete("/api/v2/booking-configurations/7", ({ request }) => {
        archiveRequests.push({ url: request.url, ifMatch: request.headers.get("If-Match") });
        return new HttpResponse(null, { status: 204 });
      }),
      ...bookableItemsHandlers(() => undefined),
    );
    await renderPage();

    await user.click((await screen.findAllByRole("checkbox", { name: "Select Confocal microscope" }))[0]);
    const selectionBar = screen.getByRole("region", { name: "Selected rows actions" });
    expect(within(selectionBar).getByRole("button", { name: "Subscribe" })).toBeVisible();
    expect(within(selectionBar).queryByRole("button", { name: "Disable" })).not.toBeInTheDocument();
    expect(within(selectionBar).queryByRole("button", { name: "Archive selected" })).not.toBeInTheDocument();
    // A booker cannot change the item, so its row has no lifecycle menu.
    expect(screen.queryByRole("button", { name: "Actions for Electron microscope" })).not.toBeInTheDocument();

    await user.click(screen.getAllByRole("button", { name: "Actions for Confocal microscope" })[0]);
    expect(screen.queryByRole("menuitem", { name: "Delete permanently" })).not.toBeInTheDocument();
    await user.click(await screen.findByRole("menuitem", { name: "Archive" }));
    const dialog = await screen.findByRole("alertdialog", { name: "Archive booking configuration?" });
    await user.click(within(dialog).getByRole("button", { name: "Archive" }));

    await waitFor(() => expect(archiveRequests).toHaveLength(1));
    expect(archiveRequests[0]).toEqual({
      url: expect.stringMatching(/\/api\/v2\/booking-configurations\/7$/),
      ifMatch: '"0"',
    });
    // The archived row leaves the selection with the list.
    await waitFor(() =>
      expect(screen.queryByRole("region", { name: "Selected rows actions" })).not.toBeInTheDocument(),
    );
  });

  it("combines Owned Items with the table request and the counts request", async () => {
    const requests: URL[] = [];
    const countRequests: URL[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json({
          ...candidatePage(bookableItemFixtures),
          pageSize: Number(url.searchParams.get("limit")),
        });
      }),
      countsHandler((url) => countRequests.push(url)),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );
    const user = userEvent.setup();
    await renderPage();

    await user.click(await screen.findByRole("button", { name: "Owned Items" }));

    await waitFor(() => {
      const mineRequests = requests.filter((request) => request.searchParams.get("mine") === "true");
      expect(mineRequests.map(pageRequest)).toEqual(["1/20"]);
      expect(countRequests.filter((request) => request.searchParams.get("mine") === "true")).toHaveLength(1);
    });
  });

  it("requests the table's own page and both counts without paging the catalogue", async () => {
    const requests: URL[] = [];
    const countRequests: URL[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json({ ...candidatePage(bookableItemFixtures), pageSize: 20 });
      }),
      countsHandler((url) => countRequests.push(url), { availableNow: 2, freeLaterToday: 1 }),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );

    await renderPage();

    expect((await screen.findAllByText("Confocal microscope"))[0]).toBeVisible();
    await waitFor(() => expect(screen.getByRole("button", { name: /^Available now/ })).toHaveTextContent("2"));
    expect(screen.getByRole("button", { name: /^Free later/ })).toHaveTextContent("1");
    expect(requests.map(pageRequest)).toEqual(["1/20"]);
    expect(requests[0].searchParams.has("availability")).toBe(false);
    expect(countRequests).toHaveLength(1);
    expect(countRequests[0].searchParams.has("page")).toBe(false);
  });

  it("requests only the table page when the page lies further on", async () => {
    const requests: URL[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json({
          ...candidatePage(bookableItemFixtures),
          page: Number(url.searchParams.get("page")),
          pageSize: Number(url.searchParams.get("limit")),
          total: 150,
        });
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );

    await renderPage("/booking/all-items?date=2026-08-17&page=3&pageSize=50");

    expect((await screen.findAllByText("Confocal microscope"))[0]).toBeVisible();
    expect(requests.map(pageRequest)).toEqual(["3/50"]);
  });

  it("searches and filters the catalogue by Inventory instrument name", async () => {
    const user = userEvent.setup();
    const catalogueRequests: URL[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        catalogueRequests.push(url);
        return HttpResponse.json({
          ...candidatePage(bookableItemFixtures),
          pageSize: Number(url.searchParams.get("limit")),
        });
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );
    await renderPage();

    await user.type(await screen.findByRole("textbox", { name: "Search All Bookable Items" }), "confocal");
    await waitFor(() =>
      expect(catalogueRequests.some((request) => request.searchParams.get("q") === "confocal")).toBe(true),
    );

    await user.click(screen.getByRole("button", { name: "Filters, none applied" }));
    await user.click(screen.getByRole("button", { name: "Add filter" }));
    await user.click(screen.getByRole("combobox", { name: "Field for filter 1" }));
    await user.click(await screen.findByRole("option", { name: "Bookable item → Instrument name" }));
    await user.click(screen.getByRole("combobox", { name: "Operator for filter 1" }));
    await user.click(screen.getByRole("option", { name: "contains" }));
    await user.type(screen.getByRole("textbox", { name: "Value for filter 1" }), "confocal");
    await user.click(screen.getByRole("button", { name: "Apply filters" }));

    await waitFor(() =>
      expect(
        catalogueRequests.some((request) => request.searchParams.get("where") === "target.name=contains=confocal"),
      ).toBe(true),
    );
  });

  it("offers audit and location filters with translated labels", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v2/openapi.json", () => HttpResponse.json(filterMetadataOpenApi())),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue", () => HttpResponse.json(candidatePage(bookableItemFixtures))),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );
    await renderPage();

    await user.click(await screen.findByRole("button", { name: "Filters, none applied" }));
    await user.click(screen.getByRole("button", { name: "Add filter" }));
    await user.click(screen.getByRole("combobox", { name: "Field for filter 1" }));

    expect(await screen.findByRole("option", { name: "Created by → First name" })).toBeVisible();
    expect(screen.getByRole("option", { name: "Updated by → Email" })).toBeVisible();
    expect(screen.getByRole("option", { name: "Location" })).toBeVisible();
    expect(screen.queryByRole("option", { name: /tableList\.|createdBy|firstName/ })).not.toBeInTheDocument();
  });

  it("filters by a readable location chosen without typing", async () => {
    const user = userEvent.setup();
    const catalogueWheres: (string | null)[] = [];
    const locationRequests: URL[] = [];
    server.use(
      http.get("/api/v2/openapi.json", () => HttpResponse.json(filterMetadataOpenApi())),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue/locations", ({ request }) => {
        locationRequests.push(new URL(request.url));
        return HttpResponse.json({ items: readableLocations, page: 1, pageSize: 20, total: 2 });
      }),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        catalogueWheres.push(new URL(request.url).searchParams.get("where"));
        return HttpResponse.json(candidatePage(bookableItemFixtures));
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );
    await renderPage();

    await user.click(await screen.findByRole("button", { name: "Filters, none applied" }));
    await user.click(screen.getByRole("button", { name: "Add filter" }));
    await user.click(screen.getByRole("combobox", { name: "Field for filter 1" }));
    await user.click(await screen.findByRole("option", { name: "Location" }));
    await user.click(screen.getByRole("button", { name: commonEnglish.relationshipPicker.openOptions }));
    await user.click(await screen.findByRole("option", { name: /WB ada/ }));
    await user.click(screen.getByRole("button", { name: "Apply filters" }));

    expect(locationRequests[0]?.searchParams.has("q")).toBe(false);
    await waitFor(() => expect(catalogueWheres).toContain("location==IC7"));
  });

  it("restores saved locations in one batched request", async () => {
    const user = userEvent.setup();
    const locationRequests: URL[] = [];
    server.use(
      http.get("/api/v2/openapi.json", () => HttpResponse.json(filterMetadataOpenApi())),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue/locations", ({ request }) => {
        const url = new URL(request.url);
        locationRequests.push(url);
        return HttpResponse.json({ items: readableLocations, page: 1, pageSize: 2, total: 2 });
      }),
      http.get("/api/v2/booking-catalogue", () => HttpResponse.json(candidatePage(bookableItemFixtures))),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );
    await renderPage(`/booking/all-items?date=2026-08-17&where=${encodeURIComponent("location=in=(IC12,IC7)")}`);

    await user.click(await screen.findByRole("button", { name: /^Filters/ }));

    expect(await screen.findByText("Cold room")).toBeVisible();
    expect(await screen.findByText("WB ada")).toBeVisible();
    // The browsable picker may also load its first page; the saved values restore in one batch.
    const restores = locationRequests.filter((request) => request.searchParams.has("globalId"));
    expect(restores).toHaveLength(1);
    expect(restores[0].searchParams.getAll("globalId")).toEqual(["IC12", "IC7"]);
  });

  it("loads the visible rows' bars with one booking request while the server counts", async () => {
    const bookings = vi.fn(() => HttpResponse.json({ ...collectionPage([]), hasNextPage: false }));
    server.use(
      catalogueHandler(() => HttpResponse.json(candidatePage(bookableItemFixtures))),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/bookings", bookings),
      ...bookableItemsHandlers(() => undefined),
    );
    await renderPage();
    await screen.findAllByRole("link", { name: "Book" });
    await waitFor(() => expect(screen.getByRole("button", { name: /^Available now/ })).toHaveTextContent(/\d/));
    await waitFor(() => expect(bookings).toHaveBeenCalledTimes(1));
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
    expect(screen.getByRole("button", { name: "Remove filter 4" })).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Apply filters" }));
    expect(router.state.location.search.where).toBe(original);
    await user.click(screen.getByRole("button", { name: /^Available now/ }));
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
      catalogueHandler(() => HttpResponse.json(candidatePage([]))),
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
      countsHandler(),
      http.get("/api/v2/booking-configuration-targets", () => HttpResponse.json([])),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json({
          items: [],
          page: 1,
          pageSize: Number(url.searchParams.get("limit")),
          total: 0,
          facets: { types: ["INSTRUMENT"] },
        });
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
    );
    const { router } = await renderPage("/booking/all-items?types=INSTRUMENT&page=3&pageSize=50");
    const size = await screen.findByRole("combobox", { name: "Rows per page" });
    await waitFor(() => expect(size).toHaveValue("50"));
    expect(router.state.location.search).toMatchObject({ page: 3, pageSize: 50, types: ["INSTRUMENT"] });
    await waitFor(() =>
      expect(
        requests.some(
          (request) => request.searchParams.get("limit") === "50" && request.searchParams.get("page") === "3",
        ),
      ).toBe(true),
    );
    expect(requests.every((request) => request.searchParams.getAll("type").join() === "INSTRUMENT")).toBe(true);
    // Changing the size returns to page 1, which the table requests at its new size.
    await user.selectOptions(size, "10");
    await waitFor(() => expect(size).toHaveValue("10"));
    await waitFor(() => expect(router.state.location.search).toMatchObject({ pageSize: 10 }));
    await waitFor(() => expect(requests.map(pageRequest)).toContain("1/10"));
    expect(requests.map(pageRequest)).not.toContain("1/100");
  });

  it("shows today's server counts before selecting a filter and after clearing it", async () => {
    const countRequests: URL[] = [];
    server.use(
      catalogueHandler(() => HttpResponse.json(candidatePage(bookableItemFixtures.slice(0, 2)))),
      countsHandler((url) => countRequests.push(url), { availableNow: 1, freeLaterToday: 1 }),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers(() => undefined),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
    );
    await renderPage("/booking/all-items?date=2026-08-20");
    const available = await screen.findByRole("button", { name: /^Available now/ });
    const later = screen.getByRole("button", { name: /^Free later/ });
    // The categories are mutually exclusive, so each chip carries what it counts.
    expect(available).toHaveAccessibleName("Available now");
    expect(available).toHaveAccessibleDescription("Free at this moment");
    expect(later).toHaveAccessibleName("Free later");
    expect(later).toHaveAccessibleDescription("Busy now, free again later today");
    await waitFor(() => expect(available).toHaveTextContent("Available now1"));
    expect(later).toHaveTextContent("Free later1");
    // The counts describe today at the page clock's minute, not the displayed 20 August.
    expect(countRequests[0].searchParams.get("availabilityStart")).toMatch(/^2026-08-17T/);
    expect(countRequests[0].searchParams.get("now")).toBe("2026-08-17T08:30:00.000Z");
    for (const link of screen.getAllByRole("link", { name: "Book" })) {
      expect(link.getAttribute("href")).toContain("date=2026-08-20");
    }
    await userEvent.click(available);
    await userEvent.click(available);
    await waitFor(() => expect(available).toHaveAttribute("aria-pressed", "false"));
    expect(available).toHaveTextContent("Available now1");
    expect(later).toHaveTextContent("Free later1");
  });

  it.each(["create", "cancel"])(
    "refreshes filtered rows and counts after %s without advancing the page clock",
    async (action) => {
      const user = userEvent.setup();
      let bookingChanged = false;
      const availableCount = () => (bookingChanged === (action === "create") ? 1 : 2);
      const catalogueRequests: URL[] = [];
      const countRequests: URL[] = [];
      server.use(
        (action === "create" ? http.post : http.patch)(
          action === "create" ? "/api/v2/bookings" : "/api/v2/bookings/41",
          () => {
            bookingChanged = true;
            return HttpResponse.json(
              {
                id: 41,
                version: 0,
                target: { relationTo: "booking-instruments", value: 123, globalId: "IN123" },
                timezone: "Europe/Berlin",
                start: "2026-08-17T09:00:00Z",
                end: "2026-08-17T10:00:00Z",
                state: action === "create" ? "CONFIRMED" : "CANCELLED",
                kind: "BOOKING",
                privacy: "full",
                purpose: null,
                cancellationReason: null,
                bookedBy: "Ada Lovelace (ada)",
                canEdit: true,
                canCancel: true,
                createdAt: "2026-08-17T08:30:00Z",
                updatedAt: "2026-08-17T08:30:00Z",
              },
              { status: 201 },
            );
          },
        ),
        catalogueHandler(({ request }) => {
          catalogueRequests.push(new URL(request.url));
          return HttpResponse.json(candidatePage(bookableItemFixtures.slice(0, availableCount())));
        }),
        http.get("/api/v2/booking-catalogue/availability-counts", ({ request }) => {
          countRequests.push(new URL(request.url));
          return HttpResponse.json({ availableNow: availableCount(), freeLaterToday: 2 - availableCount() });
        }),
        http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
        ...bookableItemsHandlers(() => undefined),
        http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      );

      await renderPage(
        "/booking/all-items?date=2026-08-17&where=availability%3D%3Davailable-now",
        action === "create" ? (
          <BookingMutationTrigger />
        ) : (
          <DeleteBookingDialog bookingId={41} bookingVersion={0} token="new-token" onDeleted={() => undefined} />
        ),
      );

      const available = await screen.findByRole("button", { name: /^Available now/ });
      await waitFor(() => expect(available).toHaveTextContent(`Available now${action === "create" ? 2 : 1}`));
      if (action === "create") {
        await waitFor(() => expect(screen.getAllByText("Electron microscope")[0]).toBeVisible());
      } else {
        expect(screen.queryByText("Electron microscope")).not.toBeInTheDocument();
      }
      const initialNow = countRequests.at(-1)?.searchParams.get("now");
      expect(initialNow).toBe(fixedClock().toISOString());

      if (action === "create") {
        await user.click(screen.getByRole("button", { name: bookingEnglish.bookings.form.submit }));
      } else {
        await user.click(screen.getByRole("button", { name: "Cancel booking" }));
        await user.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "Cancel booking" }));
      }

      if (action === "create") {
        await waitFor(() => expect(screen.queryByText("Electron microscope")).not.toBeInTheDocument());
      } else {
        await waitFor(() => expect(screen.getAllByText("Electron microscope")[0]).toBeVisible());
      }
      await waitFor(() => expect(available).toHaveTextContent(`Available now${action === "create" ? 1 : 2}`));
      expect(catalogueRequests.length).toBeGreaterThan(1);
      expect(countRequests.length).toBeGreaterThan(1);
      expect(countRequests.at(-1)?.searchParams.get("now")).toBe(initialNow);
    },
  );

  it.each([false, true])("never blocks rows while the counts load (active: %s)", async (active) => {
    let release: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    const requests: URL[] = [];
    server.use(
      catalogueHandler(({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(candidatePage(bookableItemFixtures));
      }),
      http.get("/api/v2/booking-catalogue/availability-counts", async () => {
        await pending;
        return HttpResponse.json({ availableNow: 0, freeLaterToday: 0 });
      }),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers(() => undefined),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
    );
    const { container, unmount } = await renderPage(
      `/booking/all-items?date=2026-08-17${active ? "&availability=available-now" : ""}`,
    );

    expect((await screen.findAllByText("Confocal microscope"))[0]).toBeVisible();
    expect(requests.map((request) => request.searchParams.get("availability"))).toEqual([
      active ? "available-now" : null,
    ]);
    expect(screen.getByRole("button", { name: "Jump to date" })).toBeVisible();
    await expectAccessible(container);
    unmount();
    await act(async () => release?.());
  });

  it("announces loading while the quick-filtered page loads", async () => {
    let release: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      catalogueHandler(async () => {
        await pending;
        return HttpResponse.json(candidatePage(bookableItemFixtures));
      }),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers(() => undefined),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
    );
    const { container, unmount } = await renderPage("/booking/all-items?availability=available-now");

    expect(await screen.findByText("Finding bookable items…")).toHaveAttribute("role", "status");
    expect(screen.getByText("Finding bookable items…")).toHaveClass("sr-only");
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Jump to date" })).toBeVisible();
    await expectAccessible(container);
    unmount();
    await act(async () => release?.());
  });

  it("retries failed counts and keeps the quick filter's server-filtered rows", async () => {
    let countRequests = 0;
    server.use(
      catalogueHandler(({ request }) =>
        HttpResponse.json(
          candidatePage(new URL(request.url).searchParams.get("availability") ? [] : bookableItemFixtures),
        ),
      ),
      http.get("/api/v2/booking-catalogue/availability-counts", () => {
        countRequests += 1;
        return countRequests === 1
          ? new HttpResponse(null, { status: 500 })
          : HttpResponse.json({ availableNow: 2, freeLaterToday: 0 });
      }),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers(() => undefined),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
    );
    const { router } = await renderPage("/booking/all-items?date=2026-08-17&availability=free-later-today");

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not find available items.");
    expect(screen.queryByText("Finding bookable items…")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(countRequests).toBe(2));
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: /^Available now/ })).toHaveTextContent("2");
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(router.state.location.search.availability).toBe("free-later-today");
  });

  it("reports an availability rule inside an OR group as an unsupported filter", async () => {
    const requests: URL[] = [];
    server.use(
      catalogueHandler(({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(candidatePage(bookableItemFixtures));
      }),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      ...bookableItemsHandlers(() => undefined),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
    );
    const where = "availability==available-now,target==IN123";
    await renderPage(`/booking/all-items?where=${encodeURIComponent(where)}`);

    const issue = await screen.findByRole("alert");
    expect(issue).toHaveTextContent("invalid or unavailable field");
    expect(requests).toHaveLength(0);
  });
});
