import "@/__tests__/__mocks__/matchMedia";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { collectionResponse, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { setupCalendarBookableItems } from "./calendarPageTestUtils";
import { renderCalendar } from "./calendarTestHarness";

beforeEach(() => {
  setupCalendarBookableItems();
  server.use(
    http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))),
    http.get("/api/v2/booking-catalogue/availability-counts", () =>
      HttpResponse.json({ availableNow: 2, freeLaterToday: 1 }),
    ),
  );
});

describe("Calendar Availability", () => {
  it("coerces Week to Day and explains disabled periods", async () => {
    const user = userEvent.setup();
    await renderCalendar("/booking/calendar?layout=availability&view=week");
    await user.click(await screen.findByRole("button", { name: "View: Availability · Day" }));
    expect(await screen.findByRole("menuitemradio", { name: "Week" })).toHaveAttribute("aria-disabled", "true");
    expect(await screen.findByRole("menuitemradio", { name: "Month" })).toHaveAccessibleDescription(
      "Availability is available for Day only.",
    );
  });
  it("keeps the requester rule while hiding its quick filter", async () => {
    const { router } = await renderCalendar(
      "/booking/calendar?layout=availability&calendar-events.where=requester.value%3D%3Dme",
    );
    expect(await screen.findByRole("button", { name: "Available now" })).toBeVisible();
    expect(screen.queryByRole("switch", { name: "My Bookings" })).not.toBeInTheDocument();
    expect(new URLSearchParams(router.state.location.searchStr).get("calendar-events.where")).toBe(
      "requester.value==me",
    );
  });
  it("removes availability in the layout write and keeps owner and search", async () => {
    const user = userEvent.setup();
    const { router } = await renderCalendar(
      "/booking/calendar?layout=availability&calendar-resources.q=scope&calendar-resources.where=targetOwner%3D%3Dme%3Bavailability%3D%3Davailable-now",
    );
    await screen.findByRole("button", { name: "View: Availability · Day" });
    const writes = vi.spyOn(router, "navigate");
    const invalidRequests: string[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue/calendar", ({ request }) => {
        if (new URL(request.url).searchParams.get("where")?.includes("availability")) invalidRequests.push(request.url);
        return undefined;
      }),
    );
    await user.click(screen.getByRole("button", { name: "View: Availability · Day" }));
    await user.click(await screen.findByRole("menuitemradio", { name: "By Item" }));
    await user.keyboard("{Escape}");
    await waitFor(() => expect(new URLSearchParams(router.state.location.searchStr).get("layout")).toBe("resources"));
    const search = new URLSearchParams(router.state.location.searchStr);
    expect(search.get("calendar-resources.where")).toBe("targetOwner==me");
    expect(search.get("calendar-resources.q")).toBe("scope");
    expect(writes).toHaveBeenCalledOnce();
    expect(invalidRequests).toEqual([]);
    writes.mockRestore();
  });
  it("runs only the Availability catalogue and bar queries", async () => {
    const hidden: string[] = [];
    const catalogue: URL[] = [];
    server.use(
      http.get("/api/v2/booking-calendar/events", ({ request }) => {
        hidden.push(request.url);
        return HttpResponse.json(collectionResponse([]));
      }),
      http.get("/api/v2/booking-catalogue/calendar", ({ request }) => {
        hidden.push(request.url);
        return HttpResponse.json(collectionResponse([]));
      }),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        catalogue.push(new URL(request.url));
        return undefined;
      }),
    );
    await renderCalendar("/booking/calendar?layout=availability&date=2026-08-17");
    await waitFor(() =>
      expect(screen.getAllByRole("img", { name: "Confocal microscope availability" })[0]).toBeVisible(),
    );
    expect(hidden).toEqual([]);
    expect(catalogue.length).toBeGreaterThan(0);
    expect(catalogue.every((url) => !url.searchParams.has("eventWhere") && !url.searchParams.has("start"))).toBe(true);
  });

  it("keeps owner scope in counts and selects an exclusive preset for today", async () => {
    const user = userEvent.setup();
    const counts: URL[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue/availability-counts", ({ request }) => {
        counts.push(new URL(request.url));
        return HttpResponse.json({ availableNow: 2, freeLaterToday: 1 });
      }),
    );
    const { router } = await renderCalendar(
      "/booking/calendar?layout=availability&date=2026-08-17&calendar-resources.where=targetOwner%3D%3Dme",
    );
    const available = await screen.findByRole("button", { name: "Available now" });
    await waitFor(() => expect(available).toHaveTextContent("Available now2"));
    await user.click(available);
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.searchStr).get("calendar-resources.where")).toContain(
        "availability==available-now",
      ),
    );
    expect(new URLSearchParams(router.state.location.searchStr).has("date")).toBe(false);
    await user.click(screen.getByRole("button", { name: "Free later" }));
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.searchStr).get("calendar-resources.where")).toBe(
        "targetOwner==me;availability==free-later-today",
      ),
    );
    expect(counts.every((url) => url.searchParams.get("where") === "targetOwner==me")).toBe(true);
  });

  it("drops stale availability in Resources without blocking its queries", async () => {
    await renderCalendar("/booking/calendar?calendar-resources.where=availability%3D%3Davailable-now");
    expect(
      await screen.findByText("Some saved filter rules are no longer supported and were removed: availability."),
    ).toBeVisible();
    expect(await screen.findByRole("region", { name: "Resource booking schedule" })).toBeVisible();
  });

  it("clears item rules and requester rules in one reset", async () => {
    const user = userEvent.setup();
    const { router } = await renderCalendar(
      "/booking/calendar?layout=availability&calendar-resources.where=targetOwner%3D%3Dme%3Bavailability%3D%3Davailable-now&calendar-events.where=requester.value%3D%3Dme",
    );
    await user.click(await screen.findByRole("button", { name: "Reset filters, sorting, and columns to defaults" }));
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.searchStr).get("calendar-resources.where")).toBeNull(),
    );
    expect(new URLSearchParams(router.state.location.searchStr).get("calendar-events.where")).toBeNull();
    expect(await screen.findByRole("button", { name: "View: By Item · Day" })).toBeVisible();
  });

  it("offers Details and Book with the displayed day and no lifecycle controls", async () => {
    await renderCalendar("/booking/calendar?layout=availability&date=2026-08-17");
    const books = await screen.findAllByRole("link", { name: "Book" });
    expect(books[0]).toHaveAttribute("href", "/booking/calendar/bookings/add?date=2026-08-17&target=IN123");
    expect(screen.queryByRole("checkbox", { name: "Select Confocal microscope" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Settings" })).not.toBeInTheDocument();
  });
  it("retries failed counts without blocking rows", async () => {
    const user = userEvent.setup();
    let failed = true;
    server.use(
      http.get("/api/v2/booking-catalogue/availability-counts", () =>
        failed ? new HttpResponse(null, { status: 503 }) : HttpResponse.json({ availableNow: 3, freeLaterToday: 1 }),
      ),
    );
    await renderCalendar("/booking/calendar?layout=availability&date=2026-08-17");
    await waitFor(() =>
      expect(screen.getAllByRole("img", { name: "Confocal microscope availability" })[0]).toBeVisible(),
    );
    expect(await screen.findByText("Could not find available items.")).toBeVisible();
    failed = false;
    await user.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Available now" })).toHaveTextContent("Available now3"),
    );
  });

  it("blocks an availability rule inside OR until it is explicitly removed", async () => {
    let hits = 0;
    server.use(
      http.get("/api/v2/booking-catalogue", () => {
        hits += 1;
        return undefined;
      }),
    );
    await renderCalendar(
      "/booking/calendar?layout=availability&calendar-resources.where=availability%3D%3Davailable-now%2Ctarget%3D%3DIN123",
    );
    expect(await screen.findByText(/invalid or unavailable field/)).toBeVisible();
    expect(hits).toBe(0);
    expect(screen.queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
    expect(screen.queryByText("Finding bookable items…")).not.toBeInTheDocument();
  });
  it("opens an event focus link in Resources and removes its availability preset", async () => {
    server.use(http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))));
    const { router } = await renderCalendar(
      "/booking/calendar?layout=availability&date=2026-08-17&target=IN123&focus=41&focusRequest=availability-focus&calendar-resources.where=availability%3D%3Davailable-now",
    );
    expect(await screen.findByRole("button", { name: "View: By Item · Day" })).toBeVisible();
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.searchStr).get("calendar-resources.where")).toBeNull(),
    );
  });
});
