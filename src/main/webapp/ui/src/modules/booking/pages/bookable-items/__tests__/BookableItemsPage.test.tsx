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
import { NuqsAdapter } from "nuqs/adapters/react";
import { type ComponentType, type ReactNode, Suspense } from "react";
import { afterEach, describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import { server } from "@/__tests__/mswServer";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { mutateBookableItems } from "../BookableItemsPage";
import { createBookableItemRoute, createBookableItemsRoute } from "../routes";

afterEach(() => {
  window.history.replaceState({}, "", "/");
});

const bookingConfiguration = {
  id: 7,
  target: {
    relationTo: "instruments",
    value: { id: 123, name: "Confocal microscope", deleted: false },
    globalId: "IN123",
  },
  enabled: true,
  timezone: "Europe/Berlin",
  slotGranularityMinutes: 5,
  openingStart: "00:00",
  openingEnd: "24:00",
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  updatedAt: "2026-08-10T10:00:00Z",
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
                enabled: { operators: ["==", "!=", "=out="], wildcards: false },
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
                "target",
                "enabled",
                "timezone",
                "slotGranularityMinutes",
                "openingStart",
                "openingEnd",
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

function renderBookableItemsPage(
  initialEntry = "/booking/config/bookable-items",
  wrapper?: ComponentType<{ children: ReactNode }>,
) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rootRoute = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/booking",
    component: Outlet,
  });
  const router = createRouter({
    routeTree: rootRoute.addChildren([
      bookingRoute.addChildren([createBookableItemsRoute(bookingRoute), createBookableItemRoute(bookingRoute)]),
    ]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });

  const page = (
    <QueryClientProvider client={queryClient}>
      <NuqsAdapter>
        <Suspense fallback={null}>
          <RouterProvider router={router as never} />
        </Suspense>
      </NuqsAdapter>
    </QueryClientProvider>
  );
  return render(page, wrapper ? { wrapper } : undefined);
}

function realI18nWrapper() {
  return createRealI18nWrapper({
    resources: { booking: bookingEnglish, common: commonEnglish },
    defaultNS: "common",
  });
}

describe("BookableItemsPage", () => {
  it("lists expanded booking configurations and links to the Add page", async () => {
    let collectionRequest: Request | undefined;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "new-token" })),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
      http.get("/api/v2/booking-configurations", ({ request }) => {
        collectionRequest = request;
        return HttpResponse.json(collectionResponse([bookingConfiguration]));
      }),
    );
    const { container } = renderBookableItemsPage();

    const targetName = await screen.findByText("Confocal microscope");
    const targetCell = targetName.closest("td");
    expect(targetCell).not.toBeNull();
    expect(
      within(targetCell as HTMLTableCellElement).getByRole("link", { name: "common:tableList.filters.openRecord" }),
    ).toHaveAttribute("href", "/globalId/IN123");
    expect(screen.getByRole("heading", { name: "booking:bookableItems.plural" })).toBeVisible();
    expect(collectionRequest?.headers.get("Authorization")).toBe("Bearer new-token");
    expect(new URL(collectionRequest?.url ?? "http://localhost").searchParams.get("depth")).toBe("1");
    expect(screen.getByRole("link", { name: "booking:bookableItems.actions.add" })).toHaveAttribute(
      "href",
      "/booking/config/bookable-items/add",
    );
    expect(screen.getByRole("columnheader", { name: "booking:bookableItems.fields.actions" })).toBeVisible();
    expect(screen.getByRole("link", { name: "booking:bookableItems.actions.edit" })).toHaveAttribute(
      "href",
      "/booking/bookable-items/IN123?tab=details&edit=true",
    );
    expect(screen.getByRole("link", { name: "booking:bookableItems.actions.viewDetails" })).toHaveAttribute(
      "href",
      "/booking/bookable-items/IN123",
    );
    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.delete" })).toBeVisible();
    await expectAccessible(container);
  });

  it("renders an unknown item when the related instrument is unreadable", async () => {
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
  });

  it("deletes a booking configuration after confirmation", async () => {
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
    renderBookableItemsPage();

    const deleteButtons = await screen.findAllByRole("button", { name: "booking:bookableItems.actions.delete" });
    expect(deleteButtons).toHaveLength(2);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    await user.click(deleteButtons[0]);
    expect(screen.getAllByRole("alertdialog", { name: "booking:bookableItems.deleteDialog.title" })).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: "common:actions.delete" }));

    expect(await screen.findByText("Electron microscope")).toBeVisible();
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(deleteRequest?.method).toBe("DELETE");
    expect(deleteRequest?.headers.get("Authorization")).toBe("Bearer new-token");
    expect(deleteRequest?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
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
    renderBookableItemsPage();

    await user.type(await screen.findByRole("textbox", { name: "common:tableList.search.label" }), "confocal");

    await waitFor(() => expect(new URLSearchParams(window.location.search).get("bookable-items.q")).toBe("confocal"));
    await waitFor(() => expect(searchRequests).toEqual(["target.name=contains=confocal"]));
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
    window.history.replaceState({}, "", `/?bookable-items.columns=${columns}`);

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
        "id,target,enabled,timezone,updatedAt,slotGranularityMinutes,openingStart,openingEnd,bufferBeforeMinutes,bufferAfterMinutes,allowDoubleBooking,maxBookingDurationMinutes",
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
        return HttpResponse.json({ data: [] });
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
        return HttpResponse.json({ data: [] });
      }),
    );
    renderBookableItemsPage("/booking/config/bookable-items", await realI18nWrapper());

    await user.click(await screen.findByRole("checkbox", { name: "Select Confocal microscope" }));
    await user.click(screen.getByRole("checkbox", { name: "Select Electron microscope" }));
    await user.click(screen.getByRole("button", { name: "Disable" }));

    await waitFor(() => expect(requests).toHaveLength(1));
    expect(requests[0]).toEqual({ where: "id=in=(7,8)", body: { enabled: false } });
  });

  it("confirms the multi-page count and deletes all selected rows with one request", async () => {
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
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderBookableItemsPage("/booking/config/bookable-items", await realI18nWrapper());

    await user.click(await screen.findByRole("checkbox", { name: "Select Confocal microscope" }));
    await user.click(screen.getByRole("button", { name: "Next page" }));
    await user.click(await screen.findByRole("checkbox", { name: "Select Electron microscope" }));
    await user.click(screen.getByRole("button", { name: "Delete selected" }));

    let dialog = screen.getByRole("alertdialog", { name: "Delete 2 bookable items?" });
    await expectAccessible(dialog);
    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));
    expect(screen.getByRole("region", { name: "Selected rows actions" })).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Delete selected" }));
    dialog = screen.getByRole("alertdialog", { name: "Delete 2 bookable items?" });
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    await waitFor(() => expect(deleteRequests).toHaveLength(1));
    expect(deleteRequests[0]).toEqual({ where: "id=in=(7,8)", body: "", contentType: null });
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    await waitFor(() => expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "false"));
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
        return HttpResponse.json({ data: [] });
      }),
    );
    const ids = Array.from({ length: 1001 }, (_, index) => String(index + 1));

    await expect(mutateBookableItems("enable", ids, "new-token")).rejects.toThrow(/more than 1000 row IDs/);
    expect(bulkRequests).toBe(0);
  });
});
