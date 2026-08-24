import "@/__tests__/__mocks__/matchMedia";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import {
  busyBooking,
  collectionResponse,
  currentUser,
  otherBooking,
  ownBooking,
  renderCalendar,
} from "./calendarTestHarness";

const bookingFields =
  "id,target,requesterId,timezone,start,end,state,purpose,bookedBy,privacy,canEdit,createdAt,updatedAt";

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
    expect(requests[0].searchParams.get("fields[bookings]")).toBe(bookingFields);
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
        return HttpResponse.json(collectionResponse([ownBooking, otherBooking]));
      }),
    );
    const user = userEvent.setup();
    const { router } = await renderCalendar();
    await screen.findByRole("heading", { name: "Calendar" });
    await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });

    await user.click(screen.getByRole("button", { name: "Resources" }));
    expect(screen.getByRole("region", { name: "Resources" })).toBeVisible();
    expect(screen.getByRole("region", { name: "Resource booking schedule" })).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Day" }));
    await waitFor(() => expect(requests.length).toBeGreaterThan(1));
    expect(screen.getAllByTestId("day-timeline-scroller")).toHaveLength(2);

    await user.click(screen.getByRole("button", { name: "Agenda" }));
    expect(screen.getByRole("region", { name: "Booking agenda" })).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Next day" }));
    await waitFor(() => expect(router.state.location.search).toMatchObject({ date: "2026-08-18" }));
    await waitFor(() => expect(requests.length).toBeGreaterThan(2));
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

    expect(screen.getByLabelText("Date")).toBeVisible();
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
