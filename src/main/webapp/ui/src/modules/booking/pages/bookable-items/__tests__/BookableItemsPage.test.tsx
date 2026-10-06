import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
  useNavigate,
} from "@tanstack/react-router";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { type ComponentType, type ReactNode, Suspense, useEffect } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import { MemoryHistoryNuqsAdapter as NuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import { server } from "@/__tests__/mswServer";
import { BookingNoticesProvider, useBookingNotices } from "@/modules/booking/feedback/BookingNotices";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import type { TableListAlert } from "@/modules/common/table-list/components/TableListAlerts";
import { mutateBookableItems } from "../BookableItemsPage";
import { calendarSubscriptionQueryKey } from "../bookableItemCalendarSubscription";
import { ownerBookingAccess } from "../mocks/bookableItemsMocks";
import { createBookableItemRoute, createBookableItemsRoute } from "../routes";

vi.mock("@/modules/common/queries/currentUser", () => ({ useCurrentUserQuery: vi.fn() }));

beforeEach(() => {
  vi.mocked(useCurrentUserQuery).mockReturnValue({
    data: { hasSysAdminRole: true, session: { operatedAs: false } },
  } as ReturnType<typeof useCurrentUserQuery>);
  // One instrument the caller could set up, so the page offers Add unless a test says otherwise.
  server.use(
    http.get("/api/v2/booking-configuration-targets", () =>
      HttpResponse.json([{ id: 900, globalId: "IN900", name: "Unconfigured spectrometer", deleted: false }]),
    ),
  );
});

const bookingConfiguration = {
  id: 7,
  configurationVersion: 0,
  state: "ACTIVE",
  target: {
    relationTo: "booking-instruments",
    value: { id: 123, name: "Confocal microscope", deleted: false },
    globalId: "IN123",
  },
  enabled: true,
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
  updatedAt: "2026-08-10T10:00:00Z",
  ...ownerBookingAccess,
};

const secondBookingConfiguration = {
  ...bookingConfiguration,
  id: 8,
  target: {
    ...bookingConfiguration.target,
    value: { ...bookingConfiguration.target.value, id: 124, name: "Electron microscope" },
    globalId: "IN124",
  },
};

function collectionResponse(docs: readonly Record<string, unknown>[]) {
  return {
    docs,
    totalDocs: docs.length,
    limit: 20,
    page: 1,
    pagingCounter: 1,
    totalPages: 1,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}

function pagedCollectionResponse(docs: readonly Record<string, unknown>[], page: number, totalDocs = 21) {
  return {
    ...collectionResponse(docs),
    totalDocs,
    page,
    pagingCounter: (page - 1) * 20 + 1,
    totalPages: Math.ceil(totalDocs / 20),
    hasPrevPage: page > 1,
    hasNextPage: page * 20 < totalDocs,
    prevPage: page > 1 ? page - 1 : null,
    nextPage: page * 20 < totalDocs ? page + 1 : null,
  };
}

const openApi = {
  paths: {
    "/api/v2/booking-configurations": {
      get: {
        parameters: [
          {
            name: "sort",
            "x-rspace-sort": {
              fields: ["id", "enabled", "timezone", "updatedAt"],
              default: ["id"],
              maximumFields: 5,
            },
          },
          {
            name: "where",
            schema: { type: "string", maxLength: 32768 },
            "x-rspace-filter": {
              maximumComparisons: 50,
              maximumLikeComparisons: 10,
              maximumNesting: 10,
              maximumArguments: 1000,
              selectors: {
                "createdBy.value": {
                  schema: { type: "integer", format: "int64" },
                  operators: ["==", "!=", "=in=", "=out="],
                  wildcards: false,
                },
                id: {
                  schema: { type: "integer", format: "int64" },
                  operators: ["==", "=in="],
                  wildcards: false,
                },
                enabled: { operators: ["==", "!=", "=out="], wildcards: false },
                bufferBeforeMinutes: { operators: ["==", "=gt=", "=lt="], wildcards: false },
                bufferAfterMinutes: { operators: ["==", "=gt=", "=lt="], wildcards: false },
                timezone: { operators: ["==", "!=", "=contains="], wildcards: true },
                updatedAt: { operators: ["==", "=gt=", "=lt="], wildcards: false },
                "target.id": {
                  schema: { type: "integer", format: "int64" },
                  operators: ["=="],
                  wildcards: false,
                  title: "Instrument ID",
                },
                "target.name": {
                  schema: { type: "string" },
                  operators: ["==", "=contains=", "=like="],
                  wildcards: true,
                  title: "Instrument name",
                },
                "target.deleted": {
                  schema: { type: "boolean" },
                  operators: ["=="],
                  wildcards: false,
                  title: "Deleted",
                },
              },
            },
            "x-rspace-relationship-fields": {
              "target.id": {
                schema: { type: "integer", format: "int64" },
                operators: ["=="],
                wildcards: false,
                title: "Instrument ID",
              },
              "target.name": {
                schema: { type: "string" },
                operators: ["==", "=contains=", "=like="],
                wildcards: true,
                title: "Instrument name",
              },
              "target.globalId": {
                schema: { type: "string" },
                operators: [],
                wildcards: false,
                title: "Global ID",
              },
              "target.deleted": {
                schema: { type: "boolean" },
                operators: ["=="],
                wildcards: false,
                title: "Deleted",
              },
            },
          },
          { name: "limit", schema: { type: "integer", default: 20, maximum: 100 } },
          {
            name: "fields",
            "x-rspace-allowed-fields": {
              "booking-configurations": [
                "id",
                "configurationVersion",
                "target",
                "enabled",
                "state",
                "timezone",
                "slotGranularityMinutes",
                "openingStart",
                "openingEnd",
                "openDays",
                "openingExceptions",
                "bufferBeforeMinutes",
                "bufferAfterMinutes",
                "maxBookingDurationMinutes",
                "allowDoubleBooking",
                "updatedAt",
              ],
            },
          },
        ],
      },
    },
  },
};

function SeedNotice({ alert }: { alert: TableListAlert }) {
  const { notify } = useBookingNotices();
  useEffect(() => notify("bookable-items", alert), [alert, notify]);
  return null;
}

function NoticeOrigin({ alert }: { alert: TableListAlert }) {
  const { notify } = useBookingNotices();
  const navigate = useNavigate();
  return (
    <button
      type="button"
      onClick={() => {
        notify("bookable-items", alert);
        void navigate({ to: "/booking/config/bookable-items" });
      }}
    >
      {"Finish item action"}
    </button>
  );
}

function renderBookableItemsPage(
  initialEntry = "/booking/config/bookable-items",
  wrapper?: ComponentType<{ children: ReactNode }>,
  initialNotice?: TableListAlert,
) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rootRoute = createRootRoute({
    component: () => (
      <NuqsAdapter>
        <Outlet />
      </NuqsAdapter>
    ),
  });
  const bookingRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/booking",
    component: () => (
      <BookingNoticesProvider>
        <Outlet />
        {initialNotice ? <SeedNotice alert={initialNotice} /> : null}
      </BookingNoticesProvider>
    ),
  });
  const router = createRouter({
    routeTree: rootRoute.addChildren([
      bookingRoute.addChildren([
        createBookableItemsRoute(bookingRoute),
        createBookableItemRoute(bookingRoute),
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "review-source",
          component: () => (initialNotice ? <NoticeOrigin alert={initialNotice} /> : null),
        }),
      ]),
    ]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });

  const page = (
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>
  );
  return { ...render(page, wrapper ? { wrapper } : undefined), queryClient, history: router.history };
}

function realI18nWrapper() {
  return createRealI18nWrapper({
    resources: { booking: bookingEnglish, common: commonEnglish },
    defaultNS: "common",
  });
}

describe("BookableItemsPage", () => {
  it("lists booking targets without obsolete owner-health controls", async () => {
    const user = userEvent.setup();
    const collectionRequests: Request[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", ({ request }) => {
        collectionRequests.push(request);
        return HttpResponse.json(
          collectionResponse([{ ...bookingConfiguration, ownerHealth: { hasEffectiveOwner: false } }]),
        );
      }),
    );
    const { container } = renderBookableItemsPage();

    const targetName = await screen.findByText("Confocal microscope");
    const targetCell = targetName.closest("td");
    expect(targetCell).not.toBeNull();
    expect(
      within(targetCell as HTMLTableCellElement).queryByRole("link", { name: "common:tableList.filters.openRecord" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "booking:bookableItems.plural" })).toHaveClass(
      "text-2xl",
      "font-semibold",
    );
    expect(screen.queryByText("booking:bookableItems.ownerHealth.needsOwner")).not.toBeInTheDocument();
    expect(collectionRequests[0]?.headers.get("Authorization")).toBe("Bearer new-token");
    expect(new URL(collectionRequests[0]?.url ?? "http://localhost").searchParams.get("depth")).toBe("1");
    expect(await screen.findByRole("link", { name: "booking:bookableItems.actions.add" })).toHaveAttribute(
      "href",
      "/booking/bookable-items/add",
    );
    expect(screen.getByRole("columnheader", { name: "booking:bookableItems.fields.actions" })).toBeVisible();
    expect(screen.getByRole("link", { name: "booking:bookableItems.actions.viewDetails" })).toHaveAttribute(
      "href",
      "/booking/bookable-items/IN123",
    );
    expect(screen.queryByRole("link", { name: "booking:bookableItems.actions.edit" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "booking:bookableItems.actions.access" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "booking:bookableItems.actions.repairAccess" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.archive" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.menu" }));
    expect(
      await screen.findByRole("menuitem", { name: "booking:bookableItems.actions.deletePermanently" }),
    ).toBeVisible();
    expect(screen.getByRole("menuitem", { name: "booking:bookableItems.actions.archive" })).toBeVisible();
    await user.keyboard("{Escape}");

    expect(screen.queryByRole("button", { name: "booking:bookableItems.ownerHealth.filter" })).not.toBeInTheDocument();
    await expectAccessible(container);
  });

  it("hides Add when the user has no instrument to set up", async () => {
    let targetRequests = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionResponse([]))),
      http.get("/api/v2/booking-configuration-targets", () => {
        targetRequests += 1;
        return HttpResponse.json([]);
      }),
    );
    renderBookableItemsPage();

    expect(await screen.findByRole("heading", { name: "booking:bookableItems.plural" })).toBeVisible();
    await waitFor(() => expect(targetRequests).toBe(1));
    expect(screen.queryByRole("link", { name: "booking:bookableItems.actions.add" })).not.toBeInTheDocument();
  });

  it("combines lifecycle state and enabled into one Status column", async () => {
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(
          collectionResponse([
            bookingConfiguration,
            { ...secondBookingConfiguration, enabled: false },
            {
              ...bookingConfiguration,
              id: 9,
              state: "ARCHIVED",
              target: { ...bookingConfiguration.target, globalId: "IN125" },
            },
          ]),
        ),
      ),
    );
    renderBookableItemsPage();

    expect(await screen.findByText("Electron microscope")).toBeVisible();
    expect(screen.getByRole("columnheader", { name: "booking:bookableItems.fields.status" })).toBeVisible();
    expect(
      screen.queryByRole("columnheader", { name: "booking:bookableItems.fields.enabled" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "booking:bookableItemDetails.enabled" })).toBeVisible();
    expect(screen.getByRole("cell", { name: "booking:bookableItemDetails.disabled" })).toBeVisible();
    expect(screen.getByRole("cell", { name: "booking:bookableItemDetails.archived" })).toBeVisible();
    expect(screen.queryByText("booking:bookableItems.states.active")).not.toBeInTheDocument();

    // The columns panel names the column with the same label as its header.
    await userEvent.setup().click(screen.getByRole("button", { name: "common:tableList.toolbar.columns" }));
    const columnsPanel = await screen.findByRole("region", { name: "common:tableList.columns.title" });
    expect(within(columnsPanel).getByText("booking:bookableItems.fields.status")).toBeVisible();
    expect(within(columnsPanel).queryByText("booking:bookableItems.fields.state")).not.toBeInTheDocument();
  });

  it("renders an unknown item when the related instrument is unreadable", async () => {
    vi.mocked(useCurrentUserQuery).mockReturnValue({
      data: { hasSysAdminRole: false, session: { operatedAs: false } },
    } as ReturnType<typeof useCurrentUserQuery>);
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(collectionResponse([{ ...bookingConfiguration, target: null }])),
      ),
    );

    renderBookableItemsPage();

    expect(await screen.findByText("common:values.unknownItem")).toBeVisible();
    expect(screen.queryByRole("link", { name: "booking:bookableItems.actions.viewDetails" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "booking:bookableItems.actions.edit" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.archive" })).toBeVisible();
  });

  it("archives a booking configuration after confirmation", async () => {
    vi.mocked(useCurrentUserQuery).mockReturnValue({
      data: { hasSysAdminRole: false, session: { operatedAs: false } },
    } as ReturnType<typeof useCurrentUserQuery>);
    const user = userEvent.setup();
    let deleted = false;
    let deleteRequest: Request | undefined;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(
          collectionResponse(
            deleted ? [secondBookingConfiguration] : [bookingConfiguration, secondBookingConfiguration],
          ),
        ),
      ),
      http.delete("/api/v2/booking-configurations/7", ({ request }) => {
        deleteRequest = request;
        deleted = true;
        return HttpResponse.json({ data: bookingConfiguration });
      }),
    );
    const { queryClient } = renderBookableItemsPage();
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");

    const archiveButtons = await screen.findAllByRole("button", { name: "booking:bookableItems.actions.archive" });
    expect(archiveButtons).toHaveLength(2);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    await user.click(archiveButtons[0]);
    expect(screen.getAllByRole("alertdialog", { name: "booking:bookableItems.archiveDialog.title" })).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.archive" }));

    expect(await screen.findByText("Electron microscope")).toBeVisible();
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(deleteRequest?.method).toBe("DELETE");
    expect(deleteRequest?.headers.get("Authorization")).toBe("Bearer new-token");
    expect(deleteRequest?.headers.get("If-Match")).toBe('"0"');
    expect(deleteRequest?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["api-v2", "bookings"] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: calendarSubscriptionQueryKey(7) });
    expect(screen.getByText("booking:bookableItems.feedback.archived")).toBeVisible();
  });

  it("restores a configuration and refreshes dependent queries", async () => {
    vi.mocked(useCurrentUserQuery).mockReturnValue({
      data: { hasSysAdminRole: false, session: { operatedAs: false } },
    } as ReturnType<typeof useCurrentUserQuery>);
    const user = userEvent.setup();
    const archived = { ...bookingConfiguration, state: "ARCHIVED" as const, configurationVersion: 2 };
    let current: typeof archived | typeof bookingConfiguration = archived;
    let patchRequest: Request | undefined;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionResponse([current]))),
      http.patch("/api/v2/booking-configurations/7", ({ request }) => {
        patchRequest = request;
        current = bookingConfiguration;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { queryClient } = renderBookableItemsPage();
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");

    await user.click(await screen.findByRole("button", { name: "booking:bookableItems.actions.menu" }));
    await user.click(await screen.findByRole("menuitem", { name: "booking:bookableItems.actions.restore" }));

    await waitFor(() => expect(patchRequest).toBeDefined());
    expect(patchRequest?.headers.get("If-Match")).toBe('"2"');
    await waitFor(() => expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["api-v2", "bookings"] }));
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: calendarSubscriptionQueryKey(7) });
    expect(screen.getByText("booking:bookableItems.feedback.restored")).toBeVisible();
  });

  it("debounces search and searches the target name", async () => {
    const user = userEvent.setup();
    const searchRequests: string[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", ({ request }) => {
        const where = new URL(request.url).searchParams.get("where");
        if (where) searchRequests.push(where);
        return HttpResponse.json(collectionResponse([bookingConfiguration]));
      }),
    );
    const { history } = renderBookableItemsPage();

    await user.type(await screen.findByRole("textbox", { name: "common:tableList.search.label" }), "confocal");

    await waitFor(() => expect(new URLSearchParams(history.location.search).get("bookable-items.q")).toBe("confocal"));
    await waitFor(() => expect(searchRequests).toEqual(["target.name=contains=confocal"]));
  });

  it("distinguishes before and after buffers in the filter field list", async () => {
    const user = userEvent.setup();
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionResponse([bookingConfiguration]))),
    );
    renderBookableItemsPage("/booking/config/bookable-items", await realI18nWrapper());

    await user.click(await screen.findByRole("button", { name: "Filters, none applied" }));
    await user.click(screen.getByRole("button", { name: "Add filter" }));
    await user.click(screen.getByRole("combobox", { name: "Field for filter 1" }));

    expect(await screen.findByRole("option", { name: "Buffer before booking (minutes)" })).toBeVisible();
    expect(screen.getByRole("option", { name: "Buffer after booking (minutes)" })).toBeVisible();
  });

  it("hides a column locally when the table uses a fixed projection", async () => {
    const user = userEvent.setup();
    let collectionRequests = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      // Answers like the backend: the document carries the selected fields only.
      http.get("/api/v2/booking-configurations", ({ request }) => {
        collectionRequests += 1;
        const selected = new URL(request.url).searchParams.get("fields[booking-configurations]")?.split(",") ?? [];
        const sparse = Object.fromEntries(
          Object.entries(bookingConfiguration).filter(([field]) => selected.includes(field)),
        );
        return HttpResponse.json(collectionResponse([sparse]));
      }),
    );

    renderBookableItemsPage();

    expect(await screen.findByText("Confocal microscope")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "common:tableList.toolbar.columns" }));
    // Hides "Time zone", the third shown column.
    await user.click(screen.getAllByRole("button", { name: "common:tableList.actions.hideColumn" })[2]);

    expect(await screen.findByText("Confocal microscope")).toBeVisible();
    expect(collectionRequests).toBe(1);
    expect(screen.queryByText(/Validation failed/)).not.toBeInTheDocument();
  });

  it("shows selected target fields while keeping the fixed projection", async () => {
    let collectionRequest: Request | undefined;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", ({ request }) => {
        collectionRequest = request;
        return HttpResponse.json(collectionResponse([bookingConfiguration]));
      }),
    );
    const columns = encodeURIComponent(JSON.stringify({ fields: ["target.name", "target.deleted"] }));

    renderBookableItemsPage(`/booking/config/bookable-items?bookable-items.columns=${columns}`);

    await screen.findByRole("cell", { name: "Confocal microscope" });
    expect(
      await screen.findByRole("columnheader", {
        name: "booking:bookableItems.fields.target → Instrument name",
      }),
    ).toBeVisible();
    expect(screen.getByRole("columnheader", { name: "booking:bookableItems.fields.target → Deleted" })).toBeVisible();
    expect(screen.getByRole("cell", { name: "Confocal microscope" })).toBeVisible();
    expect(screen.getByRole("cell", { name: "false" })).toBeVisible();
    expect(screen.queryByRole("columnheader", { name: "booking:bookableItems.fields.target" })).not.toBeInTheDocument();
    await waitFor(() => {
      const params = new URL(collectionRequest?.url ?? "http://localhost").searchParams;
      expect(params.get("fields[booking-configurations]")).toBe(
        "id,target,enabled,state,timezone,updatedAt,slotGranularityMinutes,openingStart,openingEnd,bufferBeforeMinutes,bufferAfterMinutes,allowDoubleBooking,maxBookingDurationMinutes,configurationVersion,openDays,openingExceptions,effectiveRole,roleSources,capabilities",
      );
      expect(params.get("depth")).toBe("1");
    });
  });

  it("keeps multi-page selection and enables all selected rows with one request", async () => {
    const user = userEvent.setup();
    const bulkRequests: Array<{ url: string; body: unknown; authorization: string | null }> = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"));
        return HttpResponse.json(
          pagedCollectionResponse(page === 2 ? [secondBookingConfiguration] : [bookingConfiguration], page),
        );
      }),
      http.patch("/api/v2/booking-configurations", async ({ request }) => {
        bulkRequests.push({
          url: request.url,
          body: await request.json(),
          authorization: request.headers.get("Authorization"),
        });
        return HttpResponse.json({ docs: [{ id: 7 }, { id: 8 }] });
      }),
    );
    renderBookableItemsPage("/booking/config/bookable-items", await realI18nWrapper());

    await user.click(await screen.findByRole("checkbox", { name: "Select Confocal microscope" }));
    await user.click(screen.getByRole("button", { name: "Next page" }));
    await user.click(await screen.findByRole("checkbox", { name: "Select Electron microscope" }));
    const selectionBar = screen.getByRole("region", { name: "Selected rows actions" });
    await expectAccessible(selectionBar);

    await user.click(screen.getByRole("button", { name: "Enable" }));

    await waitFor(() => expect(bulkRequests).toHaveLength(1));
    const enableRequest = bulkRequests[0];
    expect(new URL(enableRequest.url).searchParams.get("where")).toBe("id=in=(7,8)");
    expect(enableRequest.body).toEqual({ enabled: true });
    expect(enableRequest.authorization).toBe("Bearer new-token");
    expect(await screen.findByText("2 items are now enabled.")).toBeVisible();
    await waitFor(() =>
      expect(screen.queryByRole("region", { name: "Selected rows actions" })).not.toBeInTheDocument(),
    );
    await waitFor(() => expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "false"));
  });

  it("disables selected rows with one bulk patch", async () => {
    const user = userEvent.setup();
    const requests: Array<{ where: string | null; body: unknown }> = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(collectionResponse([bookingConfiguration, secondBookingConfiguration])),
      ),
      http.patch("/api/v2/booking-configurations", async ({ request }) => {
        requests.push({
          where: new URL(request.url).searchParams.get("where"),
          body: await request.json(),
        });
        return HttpResponse.json({ docs: [{ id: 7 }, { id: 8 }] });
      }),
    );
    renderBookableItemsPage("/booking/config/bookable-items", await realI18nWrapper());

    await user.click(await screen.findByRole("checkbox", { name: "Select Confocal microscope" }));
    await user.click(screen.getByRole("checkbox", { name: "Select Electron microscope" }));
    await user.click(screen.getByRole("button", { name: "Disable" }));

    await waitFor(() => expect(requests).toHaveLength(1));
    expect(requests[0]).toEqual({ where: "id=in=(7,8)", body: { enabled: false } });
    expect(await screen.findByText("2 items are now disabled.")).toBeVisible();
  });

  it("confirms the multi-page count and archives all selected rows with one request", async () => {
    const user = userEvent.setup();
    const deleteRequests: Array<{ where: string | null; body: string; contentType: string | null }> = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"));
        return HttpResponse.json(
          pagedCollectionResponse(page === 2 ? [secondBookingConfiguration] : [bookingConfiguration], page),
        );
      }),
      http.delete("/api/v2/booking-configurations", async ({ request }) => {
        deleteRequests.push({
          where: new URL(request.url).searchParams.get("where"),
          body: await request.text(),
          contentType: request.headers.get("Content-Type"),
        });
        return HttpResponse.json({ docs: [{ id: 7 }, { id: 8 }] });
      }),
    );
    const { queryClient } = renderBookableItemsPage("/booking/config/bookable-items", await realI18nWrapper(), {
      id: "bookable-item-IN123",
      message: "Bookable item added.",
      actions: <a href="/booking/calendar?target=IN123">{"View calendar"}</a>,
    });
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");

    expect(await screen.findByRole("link", { name: "View calendar" })).toBeVisible();
    await user.click(await screen.findByRole("checkbox", { name: "Select Confocal microscope" }));
    await user.click(screen.getByRole("button", { name: "Next page" }));
    await user.click(await screen.findByRole("checkbox", { name: "Select Electron microscope" }));
    await user.click(screen.getByRole("button", { name: "Archive selected" }));

    let dialog = screen.getByRole("alertdialog", { name: "Archive 2 booking configurations?" });
    await expectAccessible(dialog);
    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));
    expect(screen.getByRole("region", { name: "Selected rows actions" })).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Archive selected" }));
    dialog = screen.getByRole("alertdialog", { name: "Archive 2 booking configurations?" });
    await user.click(within(dialog).getByRole("button", { name: "Archive" }));

    await waitFor(() => expect(deleteRequests).toHaveLength(1));
    expect(deleteRequests[0]).toEqual({ where: "id=in=(7,8)", body: "", contentType: null });
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    await waitFor(() => expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "false"));
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["api-v2", "bookings"] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: calendarSubscriptionQueryKey(7) });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: calendarSubscriptionQueryKey(8) });
    expect(screen.queryByRole("link", { name: "View calendar" })).not.toBeInTheDocument();
  });

  it("keeps selection and re-enables the action after a failed bulk request", async () => {
    const user = userEvent.setup();
    let patchRequests = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(collectionResponse([bookingConfiguration, secondBookingConfiguration])),
      ),
      http.patch("/api/v2/booking-configurations", () => {
        patchRequests += 1;
        return HttpResponse.json({ error: "failed" }, { status: 500 });
      }),
    );
    renderBookableItemsPage("/booking/config/bookable-items", await realI18nWrapper());

    const confocalCheckbox = await screen.findByRole("checkbox", { name: "Select Confocal microscope" });
    await user.click(confocalCheckbox);
    await user.click(screen.getByRole("button", { name: "Enable" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not enable the selected rows. No rows changed. Try again.",
    );
    expect(patchRequests).toBe(1);
    expect(confocalCheckbox).toBeChecked();
    expect(screen.getByRole("button", { name: "Enable" })).toBeEnabled();

    await user.click(screen.getByRole("checkbox", { name: "Select Electron microscope" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("2 rows selected")).toBeVisible();
  });

  it("rejects 1,001 IDs before a request is sent", async () => {
    let bulkRequests = 0;
    server.use(
      http.patch("/api/v2/booking-configurations", () => {
        bulkRequests += 1;
        return HttpResponse.json({ docs: [{ id: 7 }, { id: 8 }] });
      }),
    );
    const ids = Array.from({ length: 1001 }, (_, index) => String(index + 1));

    await expect(mutateBookableItems("enable", ids, "new-token")).rejects.toThrow(/more than 1000 row IDs/);
    expect(bulkRequests).toBe(0);
  });
  it("delivers a pending result after navigating to the real table host", async () => {
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionResponse([bookingConfiguration]))),
    );
    renderBookableItemsPage("/booking/review-source", await realI18nWrapper(), {
      id: "bookable-item-IN123",
      message: "Confocal microscope was added as a bookable item.",
      actions: <a href="/booking/bookable-items/IN123">{"View details"}</a>,
    });
    await userEvent.setup().click(await screen.findByRole("button", { name: "Finish item action" }));
    const alerts = await screen.findByRole("list", { name: "Recent changes" });
    expect(within(alerts).getByText("Confocal microscope was added as a bookable item.")).toBeVisible();
    expect(within(alerts).getByRole("link", { name: "View details" })).toHaveAttribute(
      "href",
      "/booking/bookable-items/IN123",
    );
  });

  it("refreshes and clears stale item actions after a committed bulk success with an unreadable receipt", async () => {
    let changed = false;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(collectionResponse([{ ...bookingConfiguration, enabled: !changed }])),
      ),
      http.patch("/api/v2/booking-configurations", () => {
        changed = true;
        return HttpResponse.json({ docs: [{ id: "invalid" }] });
      }),
    );
    renderBookableItemsPage("/booking/config/bookable-items", await realI18nWrapper(), {
      id: "bookable-item-IN123",
      message: "Confocal microscope was added as a bookable item.",
      actions: <a href="/booking/calendar?target=IN123">{"View calendar"}</a>,
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole("checkbox", { name: "Select Confocal microscope" }));
    await user.click(screen.getByRole("button", { name: "Disable" }));
    expect(
      await screen.findByText("The disable request succeeded. Refreshing the items to confirm their current state."),
    ).toBeVisible();
    expect(await screen.findByRole("cell", { name: "booking:bookableItemDetails.disabled" })).toBeVisible();
    expect(screen.queryByRole("link", { name: "View calendar" })).not.toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Selected rows actions" })).not.toBeInTheDocument();
  });

  it("accepts a committed bulk success with an unreadable receipt", async () => {
    server.use(http.patch("/api/v2/booking-configurations", () => HttpResponse.json({ docs: [{ id: "invalid" }] })));
    await expect(mutateBookableItems("disable", ["7"], "new-token")).resolves.toBeNull();
  });
});
