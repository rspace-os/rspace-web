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
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { BookingEventList } from "../BookingEventList";
import { bookableItemEventResponse, booking, busyBooking, maintenanceBooking } from "./bookableItemEventFixtures";

vi.mock("@/modules/common/hooks/auth", () => ({ useOauthTokenQuery: vi.fn() }));

const fullBooking = { ...booking, purpose: "Cell imaging" };

const mockedUseOauthTokenQuery = vi.mocked(useOauthTokenQuery);
const changeTimezoneLabel = "Change timezone";
const changeTargetLabel = "Change target";

beforeEach(() => {
  mockedUseOauthTokenQuery.mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
});

function renderList(options: { controls?: boolean } = {}) {
  function Page() {
    const [timezone, setTimezone] = useState("UTC");
    const [globalId, setGlobalId] = useState("IN12");
    return (
      <>
        {options.controls ? (
          <>
            <button type="button" onClick={() => setTimezone("Europe/Berlin")}>
              {changeTimezoneLabel}
            </button>
            <button type="button" onClick={() => setGlobalId("IN13")}>
              {changeTargetLabel}
            </button>
          </>
        ) : null}
        <BookingEventList globalId={globalId} timezone={timezone} period="upcoming" cutoff="2026-08-24T12:00:00Z" />
      </>
    );
  }
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const root = createRootRoute({ component: Outlet });
  const listRoute = createRoute({ getParentRoute: () => root, path: "/events", component: Page });
  const detailsRoute = createRoute({
    getParentRoute: () => root,
    path: "/booking/calendar/bookings/$id",
    component: Outlet,
  });
  const editRoute = createRoute({
    getParentRoute: () => root,
    path: "/booking/calendar/bookings/$id/edit",
    component: Outlet,
  });
  const router = createRouter({
    routeTree: root.addChildren([listRoute, detailsRoute, editRoute]),
    history: createMemoryHistory({ initialEntries: ["/events"] }),
  });
  const rendered = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router as never} />
    </QueryClientProvider>,
  );
  return { ...rendered, queryClient };
}

describe("BookingEventList", () => {
  it("requests totals once and navigates count-free pages using hasNextPage", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    let releaseTotals: () => void = () => undefined;
    const totalsReady = new Promise<void>((resolve) => {
      releaseTotals = resolve;
    });
    server.use(
      http.get("/api/v2/bookings", async ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        const page = Number(url.searchParams.get("page"));
        const response = bookableItemEventResponse([fullBooking], { page, hasNextPage: page === 1, totalDocs: 11 });
        if (url.searchParams.get("totals") === "true") {
          await totalsReady;
          return HttpResponse.json(response);
        }
        const { totalDocs: _totalDocs, totalPages: _totalPages, ...slice } = response;
        return HttpResponse.json(slice);
      }),
    );
    const { queryClient } = renderList();
    await screen.findAllByText("Cell imaging");
    expect(screen.getByText("common:tableList.resultsUnknown")).toBeVisible();
    releaseTotals();
    expect(await screen.findByText("common:tableList.results")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.nextPage" }));
    await expect.poll(() => requests.some((url) => url.searchParams.get("page") === "2")).toBe(true);
    expect(requests.filter((url) => url.searchParams.get("totals") === "true")).toHaveLength(1);
    expect(requests.find((url) => url.searchParams.get("page") === "2")?.searchParams.has("totals")).toBe(false);
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "common:tableList.actions.nextPage" })).toBeDisabled(),
    );
    await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
    expect(requests.filter((url) => url.searchParams.get("totals") === "true")).toHaveLength(2);
  });

  it("renders full and busy events without leaking busy details", async () => {
    server.use(
      http.get("/api/v2/bookings", () => HttpResponse.json(bookableItemEventResponse([fullBooking, busyBooking]))),
    );
    renderList();

    await screen.findAllByText("Ada Lovelace (ada)");
    const table = screen.getByRole("table");
    expect(within(table).getByText("Ada Lovelace (ada)")).toBeVisible();
    expect(within(table).getByText("Cell imaging")).toBeVisible();
    expect(within(table).getByText("booking:bookableItemDetails.events.busy")).toBeVisible();
    expect(within(table).getAllByRole("time")).toHaveLength(2);
    expect(within(table).getByRole("link", { name: "booking:bookableItemDetails.events.edit" })).toHaveAttribute(
      "href",
      "/booking/calendar/bookings/41/edit",
    );
    expect(within(table).getByRole("link", { name: "booking:calendar.actions.viewDetails" })).toHaveAttribute(
      "href",
      "/booking/calendar/bookings/41",
    );
    expect(within(table).getAllByRole("link")).toHaveLength(2);
  });

  it("renders maintenance with its creator rather than as a busy booking", async () => {
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(bookableItemEventResponse([maintenanceBooking]))));
    renderList();

    await screen.findAllByText("Morgan Ellis (mellis)");
    const table = await screen.findByRole("table");
    expect(within(table).getByText("booking:bookings.form.typeBlockout")).toBeVisible();
    expect(within(table).getByText("Morgan Ellis (mellis)")).toBeVisible();
    expect(within(table).getByText("Laser alignment")).toBeVisible();
    expect(within(table).queryByText("booking:bookableItemDetails.events.busy")).not.toBeInTheDocument();
  });

  it("reformats for a timezone change without another request", async () => {
    const user = userEvent.setup();
    let requests = 0;
    server.use(
      http.get("/api/v2/bookings", () => {
        requests += 1;
        return HttpResponse.json(bookableItemEventResponse([fullBooking]));
      }),
    );
    renderList({ controls: true });

    const table = await screen.findByRole("table");
    const time = await within(table).findByRole("time");
    const utcText = time.textContent;
    await user.click(screen.getByRole("button", { name: changeTimezoneLabel }));

    expect(within(table).getByRole("time")).not.toHaveTextContent(utcText ?? "");
    expect(requests).toBe(2);
  });

  it("paginates and resets to the first page for a new target", async () => {
    const user = userEvent.setup();
    const pages: Array<[string | null, string | null]> = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        const where = url.searchParams.get("where");
        pages.push([where, url.searchParams.get("page")]);
        const page = Number(url.searchParams.get("page"));
        const booking = where?.includes("target==IN13")
          ? { ...fullBooking, target: { ...fullBooking.target, globalId: "IN13" } }
          : fullBooking;
        return HttpResponse.json(
          bookableItemEventResponse([booking], {
            page,
            hasPrevPage: page > 1,
            hasNextPage: page === 1,
            totalDocs: 11,
          }),
        );
      }),
    );
    renderList({ controls: true });

    await user.click(await screen.findByRole("button", { name: "common:tableList.actions.nextPage" }));
    expect(await screen.findByText("common:tableList.page")).toBeVisible();
    await user.click(screen.getByRole("button", { name: changeTargetLabel }));

    await expect.poll(() => pages.at(-1)).toEqual([expect.stringContaining("target==IN13"), "1"]);
  });

  it("renders empty and retryable error states", async () => {
    const user = userEvent.setup();
    let fail = true;
    server.use(
      http.get("/api/v2/bookings", () =>
        fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(bookableItemEventResponse([])),
      ),
    );
    renderList();

    expect(await screen.findByText("booking:bookableItemDetails.events.error.title")).toBeVisible();
    fail = false;
    await user.click(screen.getByRole("button", { name: "common:actions.retry" }));

    expect((await screen.findAllByText("booking:bookableItemDetails.events.empty"))[0]).toBeVisible();
  });

  it("can return from an empty later page", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"));
        return HttpResponse.json(
          page === 1
            ? bookableItemEventResponse([fullBooking], { page: 1, hasNextPage: true, totalDocs: 11 })
            : bookableItemEventResponse([], { page: 2, hasPrevPage: true, totalDocs: 11 }),
        );
      }),
    );
    renderList();

    await user.click(await screen.findByRole("button", { name: "common:tableList.actions.nextPage" }));
    expect((await screen.findAllByText("booking:bookableItemDetails.events.empty"))[0]).toBeVisible();
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.previousPage" }));

    expect((await screen.findAllByText("Cell imaging"))[0]).toBeVisible();
  });
});
