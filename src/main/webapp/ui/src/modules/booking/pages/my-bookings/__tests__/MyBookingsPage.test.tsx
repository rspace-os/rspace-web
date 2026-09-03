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
import { NuqsTestingAdapter, type UrlUpdateEvent } from "nuqs/adapters/testing";
import { Suspense } from "react";
import { beforeEach, describe, expect, it } from "vitest";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import i18n from "@/modules/common/i18n";
import { apiV2CollectionMetadataFromOpenApi } from "@/modules/common/table-list/adapters/apiV2/apiV2CollectionMetadata";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";
import { MyBookingsRoutePage } from "../MyBookingsPage";
import { bookingHandlers, bookingsOpenApi, roleLostBooking, upcomingBooking } from "../mocks/bookingMocks";

const initialColumns = '{ "fields": ["target", "start", "end"] }';
const initialPath = `/booking/my-bookings?period=upcoming&my-bookings.q=confocal&my-bookings.where=target.name%3Dcontains%3Dscope&my-bookings.columns=${encodeURIComponent(initialColumns)}&my-bookings.sort=-start`;

function renderPage(
  path = initialPath,
  requesterId = 84,
  onListRequest: (url: URL) => void = () => undefined,
  onCountRequest: (url: URL) => void = () => undefined,
  docs?: readonly unknown[],
) {
  const location = new URL(path, window.location.origin);
  // useTableListQueryString checks the browser URL when deciding whether URL state or saved local
  // state owns the initial view, so keep jsdom and the nuqs adapter on the same location.
  window.history.replaceState({}, "", `${location.pathname}${location.search}`);
  server.use(...bookingHandlers(onListRequest, onCountRequest, docs));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, inheritedBrowserBookingPreferences);
  queryClient.setQueryData(
    ["api-v2", "openapi", "bookings"],
    apiV2CollectionMetadataFromOpenApi(bookingsOpenApi, "bookings"),
  );
  const syncWindowUrl = ({ queryString }: UrlUpdateEvent) => {
    window.history.replaceState({}, "", `${location.pathname}${queryString}`);
  };
  const root = createRootRoute({ component: Outlet });
  const pageRoute = createRoute({
    getParentRoute: () => root,
    path: "/booking/my-bookings",
    component: () => <MyBookingsRoutePage requesterId={requesterId} title="Test user bookings" />,
  });
  const detailsRoute = createRoute({
    getParentRoute: () => root,
    path: "/booking/bookable-items/$globalId/{-$tab}",
    component: Outlet,
  });
  const router = createRouter({
    routeTree: root.addChildren([pageRoute, detailsRoute]),
    history: createMemoryHistory({ initialEntries: [`${location.pathname}${location.search}`] }),
  });
  render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <NuqsTestingAdapter searchParams={location.search} hasMemory onUrlUpdate={syncWindowUrl}>
          <RouterProvider router={router as never} />
        </NuqsTestingAdapter>
      </Suspense>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  window.localStorage.clear();
  window.history.replaceState({}, "", "/");
});

describe("My Bookings page", () => {
  it("keeps TableList URL state while replacing only the period scope", async () => {
    const requests: URL[] = [];
    renderPage(initialPath, 84, (url) => requests.push(url));
    const user = userEvent.setup();

    expect(await screen.findByRole("heading", { name: "Test user bookings" })).toBeVisible();
    await waitFor(() =>
      expect(
        requests.some((request) => request.searchParams.get("where")?.includes("target.name=contains=scope")),
      ).toBe(true),
    );
    const upcoming = requests.findLast((request) =>
      request.searchParams.get("where")?.includes("target.name=contains=scope"),
    );
    expect(upcoming?.searchParams.get("where")).toContain("requesterId==84");
    expect(upcoming?.searchParams.get("where")).toContain("kind==BOOKING");
    expect(upcoming?.searchParams.get("where")).toContain("end=gt=");
    expect(upcoming?.searchParams.get("where")).toContain("confocal");
    await waitFor(() =>
      expect(screen.getByRole("textbox", { name: "common:tableList.search.label" })).toHaveValue("confocal"),
    );
    await waitFor(() => expect(new URLSearchParams(window.location.search).get("my-bookings.q")).toBe("confocal"));
    const columnsBeforePeriodChange = new URLSearchParams(window.location.search).get("my-bookings.columns");

    await user.click(screen.getByRole("button", { name: "booking:myBookings.period.past" }));
    await waitFor(() => expect(new URLSearchParams(window.location.search).get("period")).toBe("past"));
    const parameters = new URLSearchParams(window.location.search);
    expect(parameters.get("my-bookings.q")).toBe("confocal");
    expect(parameters.get("my-bookings.where")).toBe("target.name=contains=scope");
    expect(parameters.get("my-bookings.columns")).toBe(columnsBeforePeriodChange);
    expect(parameters.get("my-bookings.sort")).toBe("-start");
    await waitFor(() => expect(requests.at(-1)?.searchParams.get("where")).toContain("end=le="));
    const pastWhere = requests.at(-1)?.searchParams.get("where");
    expect(pastWhere).toContain("target.name=contains=scope");
    expect(pastWhere).toContain("confocal");

    await user.click(screen.getByRole("button", { name: "common:tableList.actions.resetToDefaults" }));
    await waitFor(() => expect(new URLSearchParams(window.location.search).get("period")).toBe("past"));
    const current = new URLSearchParams(window.location.search);
    expect(current.get("my-bookings.q")).toBeNull();
    expect(current.get("my-bookings.where")).toBeNull();
    await waitFor(() => expect(requests.at(-1)?.searchParams.get("where")).toContain("end=le="));
    expect(requests.at(-1)?.searchParams.get("where")).toContain("requesterId==84");
  });

  it("shows the full upcoming count independently of list filters and uses the same boundary", async () => {
    const lists: URL[] = [];
    const counts: URL[] = [];
    renderPage(
      initialPath,
      84,
      (url) => lists.push(url),
      (url) => counts.push(url),
    );

    expect(await screen.findByLabelText("booking:myBookings.count.accessible")).toHaveTextContent("2");
    expect(await screen.findAllByText("Confocal microscope")).not.toHaveLength(0);
    expect(
      screen
        .getAllByRole("link", { name: "booking:myBookings.actions.viewDetails" })
        .some((link) => link.getAttribute("href") === "/booking/bookable-items/IN123"),
    ).toBe(true);
    await waitFor(() => expect(counts).toHaveLength(1));
    const countWhere = counts[0].searchParams.get("where");
    expect(countWhere).toMatch(/^requesterId==84;kind==BOOKING;end=gt=.+Z$/);
    expect(countWhere).not.toContain("confocal");
    expect(countWhere).not.toContain("target.name");
    expect([...counts[0].searchParams.keys()]).toEqual(["where"]);
    const asOf = countWhere?.match(/end=gt=(.+)$/)?.[1] ?? "";
    expect(asOf).not.toBe("");
    expect(Date.parse(upcomingBooking.start)).toBeLessThan(Date.parse(asOf));
    expect(Date.parse(upcomingBooking.end)).toBeGreaterThan(Date.parse(asOf));
    expect(
      lists.findLast((request) => request.searchParams.get("where")?.includes("confocal"))?.searchParams.get("where"),
    ).toContain(`end=gt=${asOf}`);
  });

  it("formats booking times in the resolved display timezone", async () => {
    renderPage();
    const expectedStart = new Intl.DateTimeFormat(i18n.language, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    }).format(new Date(upcomingBooking.start));

    expect(await screen.findByText(expectedStart)).toBeVisible();
  });

  it("keeps a role-lost requester's target label read-only without item navigation", async () => {
    renderPage(initialPath, 84, undefined, undefined, [roleLostBooking]);

    expect(await screen.findByText("Confocal microscope")).toBeVisible();
    expect(screen.getByText("IN123", { exact: true })).toBeVisible();
    expect(screen.queryByRole("link", { name: "booking:myBookings.actions.viewDetails" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "common:tableList.filters.openRecord" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "booking:myBookings.actions.edit" })).not.toBeInTheDocument();
    expect(screen.getByText("booking:myBookings.roleLoss.readOnly")).toBeVisible();
    expect(screen.queryByRole("button", { name: "booking:bookings.actions.cancel" })).not.toBeInTheDocument();
    // The download endpoint requires the same configuration read the row has lost.
    expect(screen.queryByRole("button", { name: "booking:calendar.file.accessibleLabel" })).not.toBeInTheDocument();
  });

  it("offers a calendar file for a confirmed booking the requester can still read", async () => {
    renderPage();

    expect(await screen.findByRole("button", { name: "booking:calendar.file.accessibleLabel" })).toBeVisible();
  });

  it("keeps icon-only page actions accessible by name", async () => {
    renderPage();

    const upcoming = await screen.findByRole("button", { name: "booking:myBookings.period.upcoming" });
    const past = screen.getByRole("button", { name: "booking:myBookings.period.past" });
    const viewDetails = (await screen.findAllByRole("link", { name: "booking:myBookings.actions.viewDetails" }))[0];
    const edit = screen.getByRole("link", { name: "booking:myBookings.actions.edit" });
    const calendarFile = screen.getByRole("button", { name: "booking:calendar.file.accessibleLabel" });
    const cancel = screen.getByRole("button", { name: "booking:bookings.actions.cancel" });

    expect(within(upcoming).queryByText("booking:myBookings.period.upcoming")).not.toBeInTheDocument();
    expect(within(past).queryByText("booking:myBookings.period.past")).not.toBeInTheDocument();
    expect(within(viewDetails).queryByText("booking:myBookings.actions.viewDetails")).not.toBeInTheDocument();
    expect(within(edit).queryByText("booking:myBookings.actions.edit")).not.toBeInTheDocument();
    expect(within(calendarFile).queryByText("booking:calendar.file.label")).not.toBeInTheDocument();
    expect(within(cancel).queryByText("booking:bookings.actions.cancel")).not.toBeInTheDocument();
  });

  it("shows period-specific empty states", async () => {
    renderPage(initialPath, 84, undefined, undefined, []);
    const user = userEvent.setup();

    expect(await screen.findByText("booking:myBookings.empty.upcoming")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "booking:myBookings.period.past" }));
    expect(await screen.findByText("booking:myBookings.empty.past")).toBeVisible();
  });
});
