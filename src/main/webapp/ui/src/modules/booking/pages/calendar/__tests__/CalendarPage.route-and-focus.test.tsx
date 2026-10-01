import "@/__tests__/__mocks__/matchMedia";
import { screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { collectionResponse } from "../../mocks/bookingCalendarFixtures";
import {
  renderCalendar,
  restoreCalendarScrollTo,
  setupCalendarBookableItems,
  setupCalendarScrollTo,
} from "./calendarPageTestUtils";

beforeAll(() => setupCalendarScrollTo());
beforeEach(() => setupCalendarBookableItems());
afterAll(() => restoreCalendarScrollTo());

describe("CalendarPage", () => {
  it("shows every bookable item by default when the period has no bookings", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))),
    );

    await renderCalendar();

    expect(await screen.findByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Resources" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Day" })).toHaveAttribute("aria-pressed", "true");
    expect(await screen.findByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
  });
});
