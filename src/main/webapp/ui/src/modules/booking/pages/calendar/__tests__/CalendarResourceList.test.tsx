import "@/__tests__/__mocks__/matchMedia";
import { screen, waitFor, within } from "@testing-library/react";
import { delay, HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import {
  bookingConfigurationOpenApi,
  bookingSummary,
  collectionResponse,
  configuration,
  renderCalendar,
  secondConfiguration,
} from "./calendarTestHarness";

describe("Calendar resource list", () => {
  it("keeps a fixed enabled filter and supplies distinct row bars from one minimal booking request", async () => {
    const bookingRequests: URL[] = [];
    let configurationRequest: URL | undefined;
    const secondBooking = {
      ...bookingSummary,
      id: 42,
      target: secondConfiguration.target,
      timezone: secondConfiguration.timezone,
      start: "2026-08-17T13:00:00Z",
      end: "2026-08-17T14:00:00Z",
    };
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(bookingConfigurationOpenApi)),
      http.get("/api/v2/booking-configurations", ({ request }) => {
        configurationRequest = new URL(request.url);
        return HttpResponse.json(collectionResponse([configuration, secondConfiguration]));
      }),
      http.get("/api/v2/bookings", ({ request }) => {
        bookingRequests.push(new URL(request.url));
        return HttpResponse.json(collectionResponse([bookingSummary, secondBooking]));
      }),
    );
    const { container } = await renderCalendar();

    const confocalBar = await screen.findByRole("img", { name: "Confocal microscope availability" });
    const electronBar = screen.getByRole("img", { name: "Electron microscope availability" });
    expect(confocalBar).toHaveAccessibleDescription(/Booked:/);
    expect(electronBar).toHaveAccessibleDescription(/Booked:/);
    expect(bookingRequests).toHaveLength(1);
    expect(bookingRequests[0].searchParams.get("fields[bookings]")).toBe("id,target,timezone,start,end,state");
    expect(bookingRequests[0].searchParams.get("where")).toContain("target=in=(IN123,IN124)");
    expect(bookingRequests[0].searchParams.get("where")).not.toContain("purpose");
    expect(configurationRequest?.searchParams.get("where")).toBe("enabled==true");
    expect(configurationRequest?.searchParams.get("fields[booking-configurations]")).toBe("id,target,enabled,timezone");
    const row = screen.getByRole("row", { name: /Confocal microscope/ });
    expect(within(row).getByRole("link", { name: "Book" })).toHaveAttribute(
      "href",
      "/booking/calendar/bookings/add?date=2026-08-17&target=IN123",
    );
    await expectAccessible(container);
  });

  it("does not claim availability while the batch is loading or after it fails", async () => {
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(bookingConfigurationOpenApi)),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionResponse([configuration]))),
      http.get("/api/v2/bookings", async () => {
        await delay(1000);
        return new HttpResponse(null, { status: 500 });
      }),
    );
    await renderCalendar();

    expect(await screen.findByText("Loading availability")).toBeVisible();
    expect(screen.queryByRole("img", { name: "Confocal microscope availability" })).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("Availability unavailable")).toBeVisible(), { timeout: 2000 });
    expect(screen.queryByRole("img", { name: "Confocal microscope availability" })).not.toBeInTheDocument();
  });
});
