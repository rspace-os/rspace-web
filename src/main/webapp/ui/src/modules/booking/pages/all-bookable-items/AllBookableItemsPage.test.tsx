import { setupRealAppI18n } from "@/__tests__/helpers/realI18n";

setupRealAppI18n();

import "@/__tests__/__mocks__/matchMedia";
import { createMemoryHistory, createRootRoute, createRoute, createRouter, Outlet } from "@tanstack/react-router";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { MemoryHistoryNuqsAdapter as NuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import { server } from "@/__tests__/mswServer";
import { allBookableItemsSearch } from "@/modules/booking/pages/all-bookable-items/search";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import {
  bookableItemFixtures,
  bookableItemsHandlers,
  bookableItemsOpenApi,
  bookerBookingAccess,
  collectionPage,
} from "../bookable-items/mocks/bookableItemsMocks";
import { renderBookingTestPage } from "../calendar/__tests__/calendarTestHarness";
import AllBookableItemsPage from "./AllBookableItemsPage";

const { selectionChanged } = vi.hoisted(() => ({ selectionChanged: vi.fn() }));
// Observe the skipped selection reset: jsdom cannot distinguish a skipped redraw.
vi.mock("./useAllBookableItemsData", async (importOriginal) => {
  const original = await importOriginal<typeof import("./useAllBookableItemsData")>();
  return {
    useAllBookableItemsData: (...args: Parameters<typeof original.useAllBookableItemsData>) => {
      const data = original.useAllBookableItemsData(...args);
      selectionChanged(data.selectedRowIds);
      return data;
    },
  };
});

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

const pageRequest = (request: URL) => `${request.searchParams.get("page")}/${request.searchParams.get("limit")}`;

/** The server publishes user fields without titles and Location as a picker-backed identity filter. */
function filterMetadataOpenApi() {
  const document = structuredClone(bookableItemsOpenApi);
  const where = document.paths["/api/v2/booking-configurations"].get.parameters.find(
    (parameter) => parameter.name === "where",
  ) as { "x-rspace-filter": { selectors: Record<string, unknown> } };
  const text = { schema: { type: "string" }, operators: ["==", "=contains=", "=in=", "=like="], wildcards: true };
  where["x-rspace-filter"].selectors.location = {
    schema: { type: "string" },
    operators: ["==", "!=", "=in=", "=out=", "=exists="],
    wildcards: false,
    picker: { resource: "containers", identity: "globalId", globalIdPrefix: "IC" },
  };
  for (const selector of ["createdBy.firstName", "updatedBy.email"]) {
    where["x-rspace-filter"].selectors[selector] = text;
  }
  where["x-rspace-filter"].selectors["location.name"] = {
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

const sysadmin = { id: 1, hasSysAdminRole: true, session: { operatedAs: false } };

async function renderPage(
  initialEntry = "/booking/all-items?date=2026-08-17",
  extra?: ReactNode,
  currentUser: object = { id: 1 },
) {
  const rootRoute = createRootRoute({
    component: () => (
      <NuqsAdapter>
        <Outlet />
        {extra}
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
          component: () => <AllBookableItemsPage />,
        }),
      ]),
    ]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });
  return renderBookingTestPage(router, {
    prepareQueryClient: (queryClient) => queryClient.setQueryData(currentUserQueryKeys.me(), currentUser),
  });
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
        return HttpResponse.json({ docs: [] });
      }),
      catalogueHandler(() => HttpResponse.json(candidatePage([]))),
      http.get("/api/v2/booking-catalogue", () => HttpResponse.json(candidatePage([]))),
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );

    await renderPage();

    await screen.findAllByText(
      "Instruments from Inventory become bookable once someone who manages them adds booking rules. Access follows the instrument's Inventory sharing.",
      { exact: true },
    );
    await waitFor(() => expect(targetsRequested).toBe(true));
    await waitFor(() => expect(screen.queryByRole("link", { name: "Add" })).not.toBeInTheDocument());
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

    await screen.findAllByText(
      "Instruments from Inventory become bookable once someone who manages them adds booking rules. Access follows the instrument's Inventory sharing.",
      { exact: true },
    );
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
    expect(screen.queryByText("Instruments from Inventory become bookable", { exact: false })).not.toBeInTheDocument();
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
    expect(location.parentElement?.textContent).toBe("WB user1a");
    await expectAccessible(container);
  });

  it("hides selection controls when no bulk actions are available", async () => {
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue", () =>
        HttpResponse.json(candidatePage([{ ...bookableItemFixtures[0], ...bookerBookingAccess }])),
      ),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );
    await renderPage();
    await screen.findAllByRole("link", { name: "Confocal microscope" });
    expect(screen.queryByRole("checkbox", { name: "Select Confocal microscope" })).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: "Select all rows" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Actions for Confocal microscope" })).not.toBeInTheDocument();
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

  it("keeps owner row actions without bulk selection and archives with the row version", async () => {
    const user = userEvent.setup();
    let archiveRequest: Request | undefined;
    let archived = false;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/booking-catalogue", () =>
        HttpResponse.json(
          candidatePage(
            archived
              ? [{ ...bookableItemFixtures[1], ...bookerBookingAccess }]
              : [bookableItemFixtures[0], { ...bookableItemFixtures[1], ...bookerBookingAccess }],
          ),
        ),
      ),
      http.delete("/api/v2/booking-configurations/7", ({ request }) => {
        archiveRequest = request;
        archived = true;
        return HttpResponse.json({ data: bookableItemFixtures[0] });
      }),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );
    await renderPage();
    await screen.findAllByRole("link", { name: "Confocal microscope" });
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Subscribe" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Unsubscribe" })).not.toBeInTheDocument();
    await screen.findByRole("link", { name: "Add" });
    expect(screen.queryByRole("button", { name: "Actions for Electron microscope" })).not.toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: "Actions for Confocal microscope" })[0]);
    expect(await screen.findByRole("menuitem", { name: "Archive" })).toBeVisible();
    expect(screen.queryByRole("menuitem", { name: "Delete permanently" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("menuitem", { name: "Archive" }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Archive" }));
    await screen.findByText(
      "Confocal microscope is now archived. Its future bookings, if any, were cancelled and will not return when it is restored.",
    );
    expect(archiveRequest?.headers.get("If-Match")).toBe('"0"');
    await waitFor(() => expect(screen.queryByRole("link", { name: "Confocal microscope" })).not.toBeInTheDocument());
  });

  it.each([
    { selected: false, action: "Owned Items", mine: false },
    { selected: true, action: "Owned Items", mine: false },
    { selected: true, action: "Owned Items", mine: true },
    { selected: false, action: "Reset filters, sorting, and columns to defaults", mine: false },
    { selected: true, action: "Reset filters, sorting, and columns to defaults", mine: false },
  ])("clears selection only when needed: %j", async ({ selected, action, mine }) => {
    const requests: URL[] = [];
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
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );
    const user = userEvent.setup();
    await renderPage(
      `/booking/all-items?q=${action.startsWith("Reset") ? "scope" : ""}&page=2${mine ? "&where=targetOwner%3D%3Dme" : ""}`,
      undefined,
      sysadmin,
    );

    await screen.findAllByRole("link", { name: "Confocal microscope" });
    if (selected) await user.click((await screen.findAllByRole("checkbox", { name: "Select Confocal microscope" }))[0]);
    const before = selectionChanged.mock.lastCall?.[0];
    await user.click(await screen.findByRole("button", { name: action }));
    await waitFor(() => expect(requests.some((request) => request.searchParams.get("page") === "1")).toBe(true));
    const after = selectionChanged.mock.lastCall?.[0];
    expect(after.size).toBe(0);
    if (selected) expect(after).not.toBe(before);
    else expect(after).toBe(before);
    expect(screen.queryByRole("region", { name: "Selected rows actions" })).not.toBeInTheDocument();
    if (action === "Owned Items") {
      expect(screen.getByRole("button", { name: action })).toHaveAttribute("aria-pressed", String(!mine));
    }
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
    await user.click(await screen.findByRole("option", { name: "Bookable item → Name" }));
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
    await user.click(await screen.findByRole("option", { name: "WB adaBE7" }));
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
    expect(issue).toMatchTextContent("invalid or unavailable field");
    expect(issue).toMatchTextContent(original);
    expect(requests).toHaveLength(0);
    await userEvent.click(screen.getByRole("button", { name: "Reset saved view" }));
    await waitFor(() => expect(router.state.location.search.where).toBeUndefined());
    expect(router.state.location.search.q).toBe("scope");
  });

  it("clears a nonempty selection when removing a restored view issue", async () => {
    const user = userEvent.setup();
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      catalogueHandler(() => HttpResponse.json(candidatePage(bookableItemFixtures))),
      http.get("/api/v2/bookings", () => HttpResponse.json({ ...collectionPage([]), hasNextPage: false })),
      ...bookableItemsHandlers(() => undefined),
    );
    const { router } = await renderPage(undefined, undefined, sysadmin);
    await user.click((await screen.findAllByRole("checkbox", { name: "Select Confocal microscope" }))[0]);
    const before = selectionChanged.mock.lastCall?.[0];
    expect(before.size).toBe(1);

    const original = "target.customFields.SF999==missing";
    await act(() => router.navigate({ to: "/booking/all-items", search: { where: original } }));
    expect(await screen.findByRole("alert")).toMatchTextContent(original);
    expect(selectionChanged.mock.lastCall?.[0]).toBe(before);
    await user.click(screen.getByRole("button", { name: "Reset saved view" }));

    await waitFor(() => expect(router.state.location.search.where).toBeUndefined());
    expect(selectionChanged.mock.lastCall?.[0].size).toBe(0);
    expect(screen.queryByRole("region", { name: "Selected rows actions" })).not.toBeInTheDocument();
  });

  it.each(["types=NOTATYPE&pageSize=50", "target=IN123&q=microscope&page=2&pageSize=50&types=INSTRUMENT"])(
    "resets the full controlled view with one navigation: %s",
    async (search) => {
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
    },
  );

  it("keeps type filters and the selected page size in the URL and catalogue request", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(bookableItemsOpenApi)),
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
    expect(requests.map(pageRequest)).toEqual(["3/50"]);
    // Changing the size returns to page 1, which the table requests at its new size.
    await user.selectOptions(size, "10");
    await waitFor(() => expect(size).toHaveValue("10"));
    await waitFor(() => expect(router.state.location.search).toMatchObject({ pageSize: 10 }));
    await waitFor(() => expect(requests.map(pageRequest)).toContain("1/10"));
    expect(requests.map(pageRequest)).not.toContain("1/100");
  });
  it("carries item scope through View availability without fetching availability on All items", async () => {
    const hidden: string[] = [];
    server.use(...bookableItemsHandlers(() => undefined));
    server.use(
      http.get("/api/v2/booking-catalogue/availability-counts", ({ request }) => {
        hidden.push(request.url);
        return HttpResponse.json({ availableNow: 0, freeLaterToday: 0 });
      }),
      http.get("/api/v2/bookings", ({ request }) => {
        hidden.push(request.url);
        return HttpResponse.json(collectionPage([]));
      }),
    );
    await renderPage("/booking/all-items?q=scope&where=targetOwner%3D%3Dme");
    const link = await screen.findByRole("link", { name: "View availability" });
    const url = new URL(link.getAttribute("href") ?? "", window.location.origin);
    expect(url.pathname).toBe("/booking/calendar");
    expect(url.searchParams.get("layout")).toBe("availability");
    expect(url.searchParams.get("calendar-resources.q")).toBe("scope");
    expect(url.searchParams.get("calendar-resources.where")).toBe("targetOwner==me");
    expect(screen.queryByRole("button", { name: "Available now" })).not.toBeInTheDocument();
    expect(hidden).toEqual([]);
  });
});
