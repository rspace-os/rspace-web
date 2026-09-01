import "@/__tests__/__mocks__/matchMedia";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { bookableItemsHandlers } from "../../bookable-items/mocks/bookableItemsMocks";
import { CALENDAR_BOOKING_FIELDS } from "../calendarEvents";
import {
  busyBooking,
  collectionResponse,
  currentUser,
  deletedParentBooking,
  noParentBooking,
  otherBooking,
  ownBooking,
  renderCalendar,
} from "./calendarTestHarness";

const scrollToDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollTo");

beforeAll(() => {
  Object.defineProperty(HTMLElement.prototype, "scrollTo", { configurable: true, value: vi.fn() });
});

beforeEach(() => {
  server.use(...bookableItemsHandlers(() => undefined));
});

afterAll(() => {
  if (scrollToDescriptor) Object.defineProperty(HTMLElement.prototype, "scrollTo", scrollToDescriptor);
  else Reflect.deleteProperty(HTMLElement.prototype, "scrollTo");
});

describe("CalendarPage", () => {
  it("renders the prototype calendar with search, privacy, editing, and personal filtering", async () => {
    const requests: URL[] = [];
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(collectionResponse([ownBooking, otherBooking, busyBooking]));
      }),
    );
    const user = userEvent.setup();
    await renderCalendar();

    expect(await screen.findByRole("heading", { name: "Calendar" })).toBeVisible();
    expect(await screen.findByRole("region", { name: "Time grid" })).toBeVisible();
    expect(screen.getByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
    expect(screen.getByRole("article", { name: /^Busy,/ })).toBeVisible();
    expect(screen.queryByText("private server detail")).not.toBeInTheDocument();
    expect(requests[0].searchParams.get("fields[bookings]")).toBe(CALENDAR_BOOKING_FIELDS);
    expect(requests[0].searchParams.get("where")).toContain("state==CONFIRMED");

    await user.click(screen.getByRole("button", { name: /Show details for Busy/ }));
    expect(screen.getByRole("link", { name: "View details" })).toHaveAttribute("href", "/booking/bookable-items/IN124");
    expect(screen.queryByRole("link", { name: "Edit" })).not.toBeInTheDocument();

    const search = screen.getByRole("textbox", { name: "Search Calendar" });
    await user.type(search, "Grace");
    expect(screen.queryByRole("article", { name: /Confocal microscope/ })).not.toBeInTheDocument();
    expect(screen.getByRole("article", { name: /Electron microscope · Grace Hopper/ })).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Clear search" }));
    await user.click(screen.getByRole("button", { name: /My calendar/ }));
    expect(screen.getByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
    expect(screen.queryByRole("article", { name: /Electron microscope · Grace Hopper/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Show details for Confocal microscope/ }));
    expect(screen.getByRole("link", { name: "View details" })).toHaveAttribute("href", "/booking/bookable-items/IN123");
    expect(screen.getByRole("link", { name: "Edit" })).toHaveAttribute(
      "href",
      "/booking/calendar/bookings/41?date=2026-08-17&target=IN123",
    );
  });

  it("switches layouts and periods while keeping the date in route state", async () => {
    const requests: URL[] = [];
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(collectionResponse([ownBooking, otherBooking, noParentBooking, deletedParentBooking]));
      }),
    );
    const user = userEvent.setup();
    const { router } = await renderCalendar();
    await screen.findByRole("heading", { name: "Calendar" });
    await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });

    await user.click(screen.getByRole("button", { name: "Month" }));
    expect(screen.getByRole("button", { name: "Month" })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: "Resources" }));
    expect(await screen.findByRole("region", { name: "Resources" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Month" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Week" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    for (const locationLink of screen.getAllByRole("link", { name: "Imaging lab" })) {
      expect(locationLink).toHaveAttribute("href", "/globalId/IC456");
    }
    for (const locationLink of screen.getAllByRole("link", { name: "Workbench" })) {
      expect(locationLink).toHaveAttribute("href", "/globalId/BE457");
    }
    expect(within(screen.getByRole("region", { name: "Resource booking schedule" })).getAllByRole("link")).toHaveLength(
      4,
    );

    await user.click(screen.getByRole("button", { name: "Day" }));
    await waitFor(() => expect(requests.length).toBeGreaterThan(1));
    expect(screen.getAllByTestId("day-timeline-scroller")).toHaveLength(5);

    await user.click(screen.getByRole("button", { name: "Agenda" }));
    expect(screen.getByRole("region", { name: "Booking agenda" })).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Next day" }));
    await waitFor(() => expect(router.state.location.search).toMatchObject({ date: "2026-08-18" }));
    await waitFor(() => expect(requests.length).toBeGreaterThan(2));
  });

  it("places creation beside the title, keeps the toolbar below it, and jumps to a date", async () => {
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    const user = userEvent.setup();
    const { router } = await renderCalendar();
    await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });

    const heading = screen.getByRole("heading", { name: "Calendar" });
    const pageHeader = heading.closest("header");
    expect(pageHeader).not.toBeNull();
    expect(within(pageHeader as HTMLElement).getByRole("button", { name: "New Booking" })).toBeVisible();
    expect(screen.queryByText("Browse booking events by day, week, or month.")).not.toBeInTheDocument();
    expect(heading.compareDocumentPosition(screen.getByRole("toolbar", { name: "Calendar controls" }))).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
    const navigation = screen.getByRole("group", { name: "Calendar period navigation" });
    expect(within(navigation).getByRole("button", { name: "Previous week" })).toBeVisible();
    expect(within(navigation).getByRole("button", { name: "Today" })).toBeVisible();
    expect(within(navigation).getByRole("button", { name: "Next week" })).toBeVisible();
    expect(screen.queryByText(/17.*23.*2026/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Jump to date" }));
    await user.click(await screen.findByRole("button", { name: /August 18.*2026/ }));

    await waitFor(() => expect(router.state.location.search).toMatchObject({ date: "2026-08-18" }));
  });

  it("offers a retry when booking events cannot be loaded", async () => {
    let requests = 0;
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => {
        requests += 1;
        return requests === 1
          ? new HttpResponse(null, { status: 503 })
          : HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    const user = userEvent.setup();
    await renderCalendar();

    expect(await screen.findByRole("alert")).toHaveTextContent("Booking events are unavailable.");
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
    expect(requests).toBe(2);
  });

  it("keeps calendar controls when search has no matches", async () => {
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });

    await user.type(screen.getByRole("textbox", { name: "Search Calendar" }), "no matching booking");

    expect(screen.getByRole("button", { name: "Jump to date" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Time grid" })).toBeVisible();
    expect(screen.getByText("No records found")).toBeVisible();
  });

  it("does not offer the unsupported bookable-item relationship filter", async () => {
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });

    await user.click(screen.getByRole("button", { name: "Filters, none applied" }));
    await user.click(screen.getByRole("button", { name: "Add filter" }));
    const field = screen.getByRole("combobox", { name: "Field for filter 1" });
    expect(field).toHaveValue("Purpose");
    await user.clear(field);
    await user.type(field, "Bookable");

    expect(await screen.findByText("No matching field.")).toBeVisible();
    expect(screen.queryByRole("option", { name: "Bookable item" })).not.toBeInTheDocument();
  });
});
