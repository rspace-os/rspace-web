import { HttpResponse, http } from "msw";
import { currentUser } from "../../calendar/calendarFixtures";
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
import {
  customNewYorkBookingPreferences,
  inheritedBrowserBookingPreferences,
} from "../../preferences/bookingPreferencesFixtures";
import { MyBookingsRoutePage } from "../MyBookingsPage";
import {
  bookingHandlers,
  bookingsOpenApi,
  cancelledBooking,
  pastBooking,
  roleLostBooking,
  upcomingBooking,
} from "../mocks/bookingMocks";

const initialColumns = '{ "fields": ["target", "start", "end"] }';
const initialPath = `/booking/my-bookings?period=upcoming&my-bookings.q=confocal&my-bookings.where=target.name%3Dcontains%3Dscope&my-bookings.columns=${encodeURIComponent(initialColumns)}&my-bookings.sort=-start`;
/** No column override, so the default columns, including the status chip, are shown. */
const defaultColumnsPath = "/booking/my-bookings?period=upcoming";

function renderPage(
  path = initialPath,
  requesterId = 84,
  onListRequest: (url: URL) => void = () => undefined,
  onCountRequest: (url: URL) => void = () => undefined,
  docs?: readonly unknown[],
  openApi: typeof bookingsOpenApi = bookingsOpenApi,
  preferences = inheritedBrowserBookingPreferences,
) {
  const location = new URL(path, window.location.origin);
  server.use(
    http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    ...bookingHandlers(onListRequest, onCountRequest, docs),
  );
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
  queryClient.setQueryData(bookingDisplayPreferencesQueryKey, preferences);
  queryClient.setQueryData(["api-v2", "openapi", "bookings"], apiV2CollectionMetadataFromOpenApi(openApi, "bookings"));
  let search = location.search;
  const onUrlUpdate = ({ queryString }: UrlUpdateEvent) => {
    search = queryString;
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
        <NuqsTestingAdapter searchParams={location.search} hasMemory onUrlUpdate={onUrlUpdate}>
          <RouterProvider router={router as never} />
        </NuqsTestingAdapter>
      </Suspense>
    </QueryClientProvider>,
  );
  return { searchParams: () => new URLSearchParams(search) };
}

beforeEach(() => {
  window.localStorage.clear();
});

describe("My Bookings page", () => {
  it("keeps TableList URL state while replacing only the period scope", async () => {
    const requests: URL[] = [];
    const { searchParams } = renderPage(initialPath, 84, (url) => requests.push(url));
    const user = userEvent.setup();

    expect(await screen.findByRole("heading", { name: "Test user bookings" })).toHaveClass("text-2xl", "font-semibold");
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
    expect(upcoming?.searchParams.get("where")).toContain("state==CONFIRMED");
    expect(upcoming?.searchParams.get("where")).toContain("end=gt=");
    expect(upcoming?.searchParams.get("where")).toContain("confocal");
    await waitFor(() =>
      expect(screen.getByRole("textbox", { name: "common:tableList.search.label" })).toHaveValue("confocal"),
    );
    await waitFor(() => expect(searchParams().get("my-bookings.q")).toBe("confocal"));
    const columnsBeforePeriodChange = searchParams().get("my-bookings.columns");

    await user.click(screen.getByRole("button", { name: "booking:myBookings.period.past" }));
    await waitFor(() => expect(searchParams().get("period")).toBe("past"));
    const parameters = searchParams();
    expect(parameters.get("my-bookings.q")).toBe("confocal");
    expect(parameters.get("my-bookings.where")).toBe("target.name=contains=scope");
    expect(parameters.get("my-bookings.columns")).toBe(columnsBeforePeriodChange);
    expect(parameters.get("my-bookings.sort")).toBe("-start");
    await waitFor(() => expect(requests.at(-1)?.searchParams.get("where")).toContain("end=le="));
    const pastWhere = requests.at(-1)?.searchParams.get("where");
    expect(pastWhere).toContain("target.name=contains=scope");
    expect(pastWhere).toContain("confocal");
    expect(pastWhere).toContain("state==CONFIRMED");
    expect(pastWhere).not.toContain("state==CANCELLED");

    await user.click(screen.getByRole("button", { name: "common:tableList.actions.resetToDefaults" }));
    await waitFor(() => expect(searchParams().get("period")).toBe("past"));
    const current = searchParams();
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
        .getAllByRole("link", { name: "booking:myBookings.actions.itemCalendar" })
        .some((link) => link.getAttribute("href") === "/booking/bookable-items/IN123"),
    ).toBe(true);
    expect(screen.getAllByRole("link", { name: "booking:myBookings.actions.viewDetails" })[0]).toHaveAttribute(
      "href",
      "/booking/calendar/bookings/41",
    );
    await waitFor(() => expect(counts).toHaveLength(1));
    const countWhere = counts[0].searchParams.get("where");
    expect(countWhere).toMatch(/^requesterId==84;kind==BOOKING;state==CONFIRMED;end=gt=.+Z$/);
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

  it("lists cancelled bookings in their own period, whatever their end time", async () => {
    const requests: URL[] = [];
    const { searchParams } = renderPage(defaultColumnsPath, 84, (url) => requests.push(url));
    const user = userEvent.setup();

    const table = within(await screen.findByRole("table"));
    expect(await table.findByText("Confocal microscope")).toBeVisible();
    expect(table.queryByText(cancelledBooking.target.value.name)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "booking:myBookings.period.cancelled" }));
    await waitFor(() => expect(searchParams().get("period")).toBe("cancelled"));
    expect(screen.getByRole("button", { name: "booking:myBookings.period.cancelled" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await waitFor(() => expect(requests.at(-1)?.searchParams.get("where")).toContain("state==CANCELLED"));
    const cancelledWhere = requests.at(-1)?.searchParams.get("where");
    expect(cancelledWhere).toContain("requesterId==84");
    expect(cancelledWhere).toContain("kind==BOOKING");
    expect(cancelledWhere).not.toContain("state==CONFIRMED");
    expect(cancelledWhere).not.toContain("end=");
    expect(await table.findByText(cancelledBooking.target.value.name)).toBeVisible();
    expect(table.queryByText("Confocal microscope")).not.toBeInTheDocument();
  });

  it("shows each booking's status in its row", async () => {
    renderPage(defaultColumnsPath);
    const user = userEvent.setup();

    const table = within(await screen.findByRole("table"));
    expect(await table.findByRole("columnheader", { name: "booking:myBookings.fields.state" })).toBeInTheDocument();
    const confirmedRow = (await table.findByText("Confocal microscope")).closest("tr");
    if (!confirmedRow) throw new Error("Expected a table row");
    expect(within(confirmedRow).getByText("booking:bookings.details.confirmed")).toBeVisible();

    await user.click(screen.getByRole("button", { name: "booking:myBookings.period.cancelled" }));
    const cancelledRow = (await table.findByText(cancelledBooking.target.value.name)).closest("tr");
    if (!cancelledRow) throw new Error("Expected a table row");
    const status = within(cancelledRow).getByText("booking:bookings.details.cancelled");
    expect(status).toHaveClass("text-muted-foreground");
    expect(within(cancelledRow).queryByText("booking:bookings.details.confirmed")).not.toBeInTheDocument();
  });

  it("formats booking times in the resolved display timezone", async () => {
    renderPage();
    const expectedStart = new Intl.DateTimeFormat(i18n.language, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    }).format(new Date(upcomingBooking.start));

    const table = within(await screen.findByRole("table"));
    expect(await table.findByText(expectedStart)).toBeVisible();
  });

  it("tells the two occurrences of a repeated hour apart with their offsets", async () => {
    // New York falls back on 2026-11-01: 05:30Z and 06:30Z are both 1:30 on the wall clock.
    const repeatedHour = { ...upcomingBooking, start: "2026-11-01T05:30:00Z", end: "2026-11-01T06:30:00Z" };
    renderPage(initialPath, 84, undefined, undefined, [repeatedHour], bookingsOpenApi, customNewYorkBookingPreferences);

    await screen.findByRole("table");
    const times = () =>
      Array.from(document.querySelectorAll(`time[datetime^="2026-11-01"]`), (time) => time.textContent);
    await waitFor(() => expect(times()).toEqual(expect.arrayContaining([expect.stringMatching(/1:30.* -04:00$/)])));
    expect(times()).toEqual(expect.arrayContaining([expect.stringMatching(/1:30.* -05:00$/)]));
  });

  it("shows an unknown item for a role-lost requester without item navigation", async () => {
    renderPage(initialPath, 84, undefined, undefined, [roleLostBooking]);

    const table = within(await screen.findByRole("table"));
    expect(await table.findByText("common:values.unknownItem")).toBeVisible();
    expect(table.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(table.queryByText("IN123", { exact: true })).not.toBeInTheDocument();
    expect(table.getByRole("link", { name: "booking:myBookings.actions.viewDetails" })).toHaveAttribute(
      "href",
      "/booking/calendar/bookings/43",
    );
    expect(screen.queryByRole("link", { name: "booking:myBookings.actions.itemCalendar" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "common:tableList.filters.openRecord" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "booking:myBookings.actions.edit" })).not.toBeInTheDocument();
    expect(table.getByText("booking:myBookings.roleLoss.readOnly")).toBeVisible();
    // Neither cancelling nor the calendar file (which needs the configuration read the row has lost) is offered.
    expect(table.queryByRole("button", { name: "booking:myBookings.actions.more" })).not.toBeInTheDocument();
  });

  it("offers a calendar file for a confirmed booking the requester can still read", async () => {
    renderPage();
    const user = userEvent.setup();

    const table = within(await screen.findByRole("table"));
    await user.click(await table.findByRole("button", { name: "booking:myBookings.actions.more" }));
    expect(
      await screen.findByRole("menuitem", { name: "booking:myBookings.actions.downloadCalendarFile" }),
    ).toBeVisible();
  });

  it("opens the cancel dialog from More actions and keeps the booking on dismissal", async () => {
    let requests = 0;
    server.use(
      http.patch("/api/v2/bookings/41", () => {
        requests += 1;
        return HttpResponse.json({});
      }),
    );
    renderPage();
    const user = userEvent.setup();

    const table = within(await screen.findByRole("table"));
    const moreActions = await table.findByRole("button", { name: "booking:myBookings.actions.more" });
    await user.click(moreActions);
    const cancel = await screen.findByRole("menuitem", { name: "booking:bookings.actions.cancel" });
    expect(cancel).toHaveClass("text-destructive");
    await user.click(cancel);

    const dialog = await screen.findByRole("alertdialog");
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(dialog).toHaveTextContent("booking:bookings.cancelDialog.description");
    await user.click(within(dialog).getByRole("button", { name: "booking:bookings.cancelDialog.keep" }));

    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    expect(requests).toBe(0);
    await waitFor(() => expect(moreActions).toHaveFocus());
  });

  it("reports a cancellation above the table and restores the booking on Undo", async () => {
    const docs: Array<Record<string, unknown>> = [{ ...upcomingBooking }, pastBooking, cancelledBooking];
    const patches: Array<{ body: unknown; ifMatch: string | null }> = [];
    server.use(
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        const body = (await request.json()) as { state: string };
        patches.push({ body, ifMatch: request.headers.get("If-Match") });
        docs[0] = { ...upcomingBooking, state: body.state, version: patches.length };
        return HttpResponse.json(docs[0]);
      }),
    );
    renderPage(initialPath, 84, undefined, undefined, docs);
    const user = userEvent.setup();

    const table = within(await screen.findByRole("table"));
    await user.click(await table.findByRole("button", { name: "booking:myBookings.actions.more" }));
    await user.click(await screen.findByRole("menuitem", { name: "booking:bookings.actions.cancel" }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "booking:bookings.actions.cancel" }));

    await waitFor(() => expect(table.queryByText(upcomingBooking.target.value.name)).not.toBeInTheDocument());
    // The row and its dialog are gone; the TableList alert outlives them and takes focus.
    const alerts = screen.getByRole("list", { name: "common:tableList.alerts.label" });
    const alert = within(alerts).getByRole("listitem", { name: "booking:myBookings.cancelled.alert" });
    await waitFor(() => expect(alert).toHaveFocus());

    await user.click(within(alert).getByRole("button", { name: "common:tableList.alerts.undo" }));

    // Undo sends the version the cancellation returned.
    await waitFor(() => expect(patches).toHaveLength(2));
    expect(patches[1]).toEqual({ body: { state: "CONFIRMED" }, ifMatch: '"1"' });
    expect(await table.findByText(upcomingBooking.target.value.name)).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "common:tableList.alerts.label" })).not.toBeInTheDocument();
    await waitFor(() =>
      expect(document.activeElement?.closest("[data-table-list-row-actions]")).toHaveAttribute(
        "data-table-list-row-actions",
        String(upcomingBooking.id),
      ),
    );
  });

  it("keeps page actions accessible by name", async () => {
    renderPage();

    const upcoming = await screen.findByRole("button", { name: "booking:myBookings.period.upcoming" });
    const past = screen.getByRole("button", { name: "booking:myBookings.period.past" });
    const viewDetails = (await screen.findAllByRole("link", { name: "booking:myBookings.actions.viewDetails" }))[0];
    const table = within(screen.getByRole("table"));
    const edit = table.getByRole("link", { name: "booking:myBookings.actions.edit" });
    const moreActions = table.getByRole("button", { name: "booking:myBookings.actions.more" });

    expect(screen.getByRole("group", { name: "booking:myBookings.period.legend" })).toContainElement(upcoming);
    expect(screen.queryByRole("heading", { name: "booking:myBookings.plural" })).not.toBeInTheDocument();
    expect(screen.queryByText("booking:myBookings.description")).not.toBeInTheDocument();
    expect(within(upcoming).getByText("booking:myBookings.period.upcoming")).toBeVisible();
    expect(within(past).getByText("booking:myBookings.period.past")).toBeVisible();
    expect(within(viewDetails).queryByText("booking:myBookings.actions.viewDetails")).not.toBeInTheDocument();
    expect(within(edit).queryByText("booking:myBookings.actions.edit")).not.toBeInTheDocument();
    expect(within(moreActions).queryByText("booking:myBookings.actions.more")).not.toBeInTheDocument();
    // The less frequent actions carry visible text inside the menu.
    await userEvent.setup().click(moreActions);
    expect(
      within(
        await screen.findByRole("menuitem", { name: "booking:myBookings.actions.downloadCalendarFile" }),
      ).getByText("booking:myBookings.actions.downloadCalendarFile"),
    ).toBeVisible();
    expect(
      within(screen.getByRole("menuitem", { name: "booking:bookings.actions.cancel" })).getByText(
        "booking:bookings.actions.cancel",
      ),
    ).toBeVisible();
  });

  it("shows period-specific empty states", async () => {
    renderPage(initialPath, 84, undefined, undefined, []);
    const user = userEvent.setup();

    const table = within(await screen.findByRole("table"));
    expect(await table.findByText("booking:myBookings.empty.upcoming")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "booking:myBookings.period.past" }));
    expect(await table.findByText("booking:myBookings.empty.past")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "booking:myBookings.period.cancelled" }));
    expect(await table.findByText("booking:myBookings.empty.cancelled")).toBeVisible();
  });
});
