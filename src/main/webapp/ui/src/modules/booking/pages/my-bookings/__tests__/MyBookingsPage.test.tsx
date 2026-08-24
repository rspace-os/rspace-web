import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NuqsTestingAdapter, type UrlUpdateEvent } from "nuqs/adapters/testing";
import { Suspense } from "react";
import { beforeEach, describe, expect, it } from "vitest";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import i18n from "@/modules/common/i18n";
import { apiV2CollectionMetadataFromOpenApi } from "@/modules/common/table-list/adapters/apiV2/apiV2CollectionMetadata";
import { MyBookingsRoutePage } from "../MyBookingsPage";
import { bookingHandlers, bookingsOpenApi, upcomingBooking } from "../mocks/bookingMocks";

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
  queryClient.setQueryData(
    ["api-v2", "openapi", "bookings"],
    apiV2CollectionMetadataFromOpenApi(bookingsOpenApi, "bookings"),
  );
  const syncWindowUrl = ({ queryString }: UrlUpdateEvent) => {
    window.history.replaceState({}, "", `${location.pathname}${queryString}`);
  };
  render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <NuqsTestingAdapter searchParams={location.search} hasMemory onUrlUpdate={syncWindowUrl}>
          <MyBookingsRoutePage requesterId={requesterId} title="Test user bookings" />
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
    expect(await screen.findByText("Confocal microscope")).toBeVisible();
    await waitFor(() => expect(counts).toHaveLength(1));
    const countWhere = counts[0].searchParams.get("where");
    expect(countWhere).toMatch(/^requesterId==84;end=gt=.+Z$/);
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

  it("formats booking times in the booking timezone", async () => {
    renderPage();
    const expectedStart = new Intl.DateTimeFormat(i18n.language, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: upcomingBooking.timezone,
    }).format(new Date(upcomingBooking.start));

    expect(await screen.findByText(expectedStart)).toBeVisible();
  });

  it("shows period-specific empty states", async () => {
    renderPage(initialPath, 84, undefined, undefined, []);
    const user = userEvent.setup();

    expect(await screen.findByText("booking:myBookings.empty.upcoming")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "booking:myBookings.period.past" }));
    expect(await screen.findByText("booking:myBookings.empty.past")).toBeVisible();
  });
});
