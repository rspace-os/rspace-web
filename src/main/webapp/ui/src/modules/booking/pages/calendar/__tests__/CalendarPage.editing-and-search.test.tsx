import "@/__tests__/__mocks__/matchMedia";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { collectionResponse, ownBooking } from "../../mocks/bookingCalendarFixtures";
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
  it("keeps bookable items and calendar controls when booking search has no matches", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });

    await user.type(screen.getByRole("textbox", { name: "Search Calendar" }), "no matching booking");

    expect(screen.getByRole("button", { name: "Jump to date" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Time grid" })).toBeVisible();
    expect(screen.getByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    expect(screen.getByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
  });
});
