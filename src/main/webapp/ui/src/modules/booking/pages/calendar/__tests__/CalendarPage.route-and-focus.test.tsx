import "@/__tests__/__mocks__/matchMedia";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { bookableItemsHandlers, bookableItemsOpenApi } from "../../bookable-items/mocks/bookableItemsMocks";
import { collectionResponse, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { bookingsOpenApi } from "../../my-bookings/mocks/bookingMocks";
import { restoreCalendarScrollTo, setupCalendarBookableItems, setupCalendarScrollTo } from "./calendarPageTestUtils";
import { renderCalendar } from "./calendarTestHarness";

type User = ReturnType<typeof userEvent.setup>;

/** Turns a quick filter on or off in the Filters menu, then closes it. */
async function toggleQuickFilter(user: User, name: string) {
  await user.click(await screen.findByRole("button", { name: /^Filters(?:$|,)/ }));
  await user.click(await screen.findByRole("switch", { name }));
  await user.keyboard("{Escape}");
  await waitFor(() => expect(screen.queryByRole("switch", { name })).not.toBeInTheDocument());
}

beforeAll(() => setupCalendarScrollTo());
beforeEach(() => setupCalendarBookableItems());
afterAll(() => restoreCalendarScrollTo());

describe("CalendarPage", () => {
  it("edits the item-owner preset in the existing panel without changing the requester scope", async () => {
    const user = userEvent.setup();
    await renderCalendar(
      "/booking/calendar?date=2026-08-17&calendar-events.where=requester.value%3D%3Dme&calendar-resources.where=targetOwner%3D%3Dme",
    );
    await user.click(await screen.findByRole("button", { name: "Filters 2 applied" }));
    await user.click(screen.getByRole("button", { name: "Edit filters" }));
    expect(await screen.findByRole("combobox", { name: "Field for filter 2" })).toHaveValue("Owner");
    expect(screen.getByRole("combobox", { name: "Value for filter 2" })).toHaveValue("booking:filters.me");
    await user.click(screen.getByRole("button", { name: "Remove filter 2" }));
    await user.click(screen.getByRole("button", { name: "Apply filters" }));
    await waitFor(() => expect(new URLSearchParams(window.location.search).get("calendar-resources.where")).toBeNull());
    expect(new URLSearchParams(window.location.search).get("calendar-events.where")).toBe("requester.value==me");
    expect(screen.queryByRole("button", { name: "Remove Owned Items filter" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Filters 1 applied" })).toBeVisible();
  });

  it("drops a literal requester rule from a shared URL and reports the removed rule", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-calendar/events", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    await renderCalendar("/booking/calendar?date=2026-08-17&calendar-events.where=requester.value%3D%3D42");
    expect(
      await screen.findByText("Some saved filter rules are no longer supported and were removed: requester.value."),
    ).toBeVisible();
    await waitFor(() => expect(requests.length).toBeGreaterThan(0));
    expect(requests.every((request) => !request.searchParams.get("where")?.includes("requester.value==42"))).toBe(true);
    expect(screen.queryByRole("button", { name: "Remove My Bookings filter" })).not.toBeInTheDocument();
  });

  it("preselects the filtered item for a new maintenance event", async () => {
    const user = userEvent.setup();
    await renderCalendar("/booking/calendar?date=2026-08-17&target=IN123");
    await screen.findByRole("region", { name: "Resource booking schedule" });
    await user.click(await screen.findByRole("button", { name: "More event creation options" }));
    await user.click(await screen.findByRole("menuitem", { name: "New Maintenance Event" }));
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByRole("combobox", { name: "Bookable item" })).toHaveValue(
      "Confocal microscope (IN123)",
    );
  });

  it("filters calendar resources and events to Owned Items", async () => {
    const catalogueRequests: URL[] = [];
    const eventRequests: URL[] = [];
    server.use(
      http.get("/api/v2/openapi.json", () =>
        HttpResponse.json({
          paths: { ...bookableItemsOpenApi.paths, ...bookingsOpenApi.paths },
          components: bookableItemsOpenApi.components,
        }),
      ),
      ...bookableItemsHandlers((request) => {
        const url = new URL(request.url);
        if (url.pathname === "/api/v2/booking-catalogue/calendar") catalogueRequests.push(url);
      }),
      http.get("/api/v2/booking-calendar/events", ({ request }) => {
        eventRequests.push(new URL(request.url));
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    const user = userEvent.setup();
    await renderCalendar();

    await toggleQuickFilter(user, "Owned Items");

    await waitFor(() => {
      expect(catalogueRequests.at(-1)?.searchParams.get("where")).toContain("targetOwner==me");
      expect(catalogueRequests.at(-1)?.searchParams.get("mine")).toBe("false");
      expect(eventRequests.some((request) => request.searchParams.get("where")?.includes("targetOwner==me"))).toBe(
        true,
      );
      expect(new URLSearchParams(window.location.search).get("calendar-resources.where")).toBe("targetOwner==me");
    });
  });

  it("shows every bookable item by default when the period has no bookings", async () => {
    server.use(http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))));

    await renderCalendar();

    expect(await screen.findByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    expect(screen.getByRole("button", { name: "View: By Item · Day" })).toBeVisible();
    expect(await screen.findByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
  });
});
