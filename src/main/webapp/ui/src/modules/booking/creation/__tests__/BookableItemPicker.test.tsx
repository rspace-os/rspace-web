import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRouteWithContext,
  createRoute,
  createRouter,
  RouterProvider,
} from "@tanstack/react-router";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { DEFAULT_SCHEDULING_SETTINGS } from "@/modules/booking/configuration/schedulingSettings";
import type { AppRouterContext } from "@/modules/common/app/router";
import { BookableItemPicker, loadBookableItems } from "../BookableItemPicker";
import { cataloguePage, configuration } from "./creationTestFixtures";

function renderPicker() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const root = createRootRouteWithContext<AppRouterContext>()();
  const picker = createRoute({
    getParentRoute: () => root,
    path: "/",
    component: () => <BookableItemPicker value={undefined} onChange={() => {}} />,
  });
  const allItems = createRoute({
    getParentRoute: () => root,
    path: "/booking/all-items",
    component: () => <h1>{"All items destination"}</h1>,
  });
  const router = createRouter({
    routeTree: root.addChildren([picker, allItems]),
    context: { queryClient },
    history: createMemoryHistory({ initialEntries: ["/"] }),
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

describe("BookableItemPicker", () => {
  it("always applies enabled and resolves an exact global ID", async () => {
    let requestUrl: URL | undefined;
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        requestUrl = new URL(request.url);
        return HttpResponse.json(cataloguePage([configuration(12, "Scope")]));
      }),
    );

    const page = await loadBookableItems({ target: "IN12" }, new AbortController().signal);

    expect(requestUrl?.searchParams.get("target")).toBe("IN12");
    expect(requestUrl?.searchParams.get("capability")).toBe("CREATE_BOOKING");
    expect(requestUrl?.searchParams.get("page")).toBe("1");
    expect(requestUrl?.searchParams.get("limit")).toBe("20");
    expect(page.options[0]).toEqual({
      configurationId: 12,
      targetId: 12,
      globalId: "IN12",
      name: "Scope",
      timezone: "Europe/Berlin",
      ...DEFAULT_SCHEDULING_SETTINGS,
      capabilities: configuration(12, "Scope").capabilities,
    });
  });

  it("offers maintenance only for items granting the server-computed capability", async () => {
    const manager = configuration(12, "Managed item");
    manager.capabilities.canCreateBlockout = true;
    server.use(
      http.get("/api/v2/booking-catalogue", () =>
        HttpResponse.json(cataloguePage([manager, configuration(13, "Booker item")])),
      ),
    );
    const page = await loadBookableItems({ eventKind: "MAINTENANCE" }, new AbortController().signal);
    expect(page.options.map((item) => item.globalId)).toEqual(["IN12"]);
    expect(page.options[0].capabilities?.canCreateBlockout).toBe(true);
  });

  it("excludes view-only items from both search and preselected targets", async () => {
    const viewer = configuration(12, "View only");
    viewer.capabilities.canCreateBooking = false;
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) =>
        HttpResponse.json(
          cataloguePage(
            new URL(request.url).searchParams.has("target") ? [viewer] : [viewer, configuration(13, "Bookable")],
          ),
        ),
      ),
    );
    const signal = new AbortController().signal;
    expect((await loadBookableItems({}, signal)).options.map((item) => item.globalId)).toEqual(["IN13"]);
    expect((await loadBookableItems({ target: "IN12" }, signal)).options).toEqual([]);
  });

  it("debounces search and pages through results on demand", async () => {
    const pages: string[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        const page = url.searchParams.get("page") ?? "1";
        const query = url.searchParams.get("q") ?? "";
        if (query !== "confocal") return HttpResponse.json(cataloguePage([]));
        pages.push(page);
        return HttpResponse.json(
          page === "1"
            ? cataloguePage([configuration(1, "Confocal A")], 1, 21)
            : cataloguePage([configuration(2, "Confocal B")], 2, 21),
        );
      }),
    );
    const user = userEvent.setup();
    renderPicker();

    await user.type(await screen.findByRole("combobox", { name: "booking:bookings.form.item" }), "confocal");

    expect(await screen.findByRole("option", { name: "booking:bookings.form.itemOption" })).toBeVisible();
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.nextItems" }));
    await waitFor(() => expect(pages).toEqual(["1", "2"]));
  });

  it("explains Inventory access and links to All items when no items are available", async () => {
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));
    const user = userEvent.setup();
    renderPicker();
    expect(await screen.findByText("booking:allBookableItems.primer.description")).toBeVisible();
    expect(screen.getByText("booking:bookings.form.itemAccess")).toBeVisible();
    expect(screen.getByRole("link", { name: "booking:sidebar.allItems" })).toHaveAttribute(
      "href",
      "/booking/all-items",
    );
    await user.click(screen.getByRole("link", { name: "booking:sidebar.allItems" }));
    expect(await screen.findByRole("heading", { name: "All items destination" })).toBeVisible();
  });

  it("shows failure without inventing an option and honors cancellation", async () => {
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json({}, { status: 500 })));
    const user = userEvent.setup();
    renderPicker();
    await user.click(await screen.findByRole("button", { name: "booking:bookings.form.itemChoose" }));
    expect(await screen.findByText("booking:bookings.errors.itemLoad")).toBeVisible();

    const controller = new AbortController();
    controller.abort();
    await expect(loadBookableItems({}, controller.signal)).rejects.toThrow();
  });

  it("announces loading without showing the empty state", async () => {
    let finishRequest = () => {};
    const requestPending = new Promise<void>((resolve) => {
      finishRequest = resolve;
    });
    server.use(
      http.get("/api/v2/booking-catalogue", async () => {
        await requestPending;
        return HttpResponse.json(cataloguePage([]));
      }),
    );
    renderPicker();

    expect(await screen.findByRole("status")).toHaveTextContent("booking:bookings.loadingConfiguration");
    expect(screen.queryByText("booking:bookings.form.itemNone")).not.toBeInTheDocument();

    finishRequest();
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "booking:bookings.form.itemChoose" }));
    expect(await screen.findByText("booking:bookings.form.itemNone")).toBeVisible();
  });
});
