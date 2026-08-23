import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import {
  bookingConfigurationOpenApi,
  bookingSummary,
  collectionResponse,
  configuration,
  secondConfiguration,
} from "./__tests__/calendarTestHarness";
import { CalendarPageStory } from "./CalendarPage.story";
import { CalendarPage } from "./pageObjects/CalendarPage";

const calendar = new CalendarPage();
const massSpectrometer = {
  ...configuration,
  id: 9,
  target: {
    ...configuration.target,
    value: { id: 125, name: "Mass spectrometer", deleted: false },
    globalId: "IN125",
  },
  timezone: "UTC",
};
const fullDetail = {
  ...bookingSummary,
  start: "2026-08-16T21:30:00Z",
  end: "2026-08-17T00:30:00Z",
  privacy: "full",
  purpose: "Image plate 4",
  bookedBy: "Ada Lovelace (ada)",
  canEdit: true,
};
const busyDetail = {
  ...bookingSummary,
  id: 42,
  start: "2026-08-17T10:00:00Z",
  end: "2026-08-17T11:00:00Z",
  privacy: "busy",
  purpose: null,
  bookedBy: null,
  canEdit: false,
};

let configurationRequests: URL[];
let availabilityRequests: URL[];
let detailRequests: URL[];

beforeEach(() => {
  configurationRequests = [];
  availabilityRequests = [];
  detailRequests = [];
  window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
  worker.use(
    oauthTokenHandler(),
    http.get("/api/v2/openapi.json", () => HttpResponse.json(bookingConfigurationOpenApi)),
    http.get("/api/v2/booking-configurations", ({ request }) => {
      const url = new URL(request.url);
      configurationRequests.push(url);
      const requestedPage = Number(url.searchParams.get("page") ?? "1");
      return HttpResponse.json(
        collectionResponse(requestedPage === 2 ? [massSpectrometer] : [configuration, secondConfiguration], {
          page: requestedPage,
          totalDocs: 21,
          totalPages: 2,
        }),
      );
    }),
    http.get("/api/v2/bookings", ({ request }) => {
      const url = new URL(request.url);
      const fields = url.searchParams.get("fields[bookings]") ?? "";
      if (fields.includes("privacy")) {
        detailRequests.push(url);
        return HttpResponse.json(collectionResponse([fullDetail, busyDetail]));
      }
      availabilityRequests.push(url);
      const where = url.searchParams.get("where") ?? "";
      const documents = where.includes("IN125")
        ? [{ ...bookingSummary, id: 43, target: massSpectrometer.target, timezone: "UTC" }]
        : [
            bookingSummary,
            {
              ...bookingSummary,
              id: 44,
              target: secondConfiguration.target,
              timezone: secondConfiguration.timezone,
            },
          ];
      return HttpResponse.json(collectionResponse(documents));
    }),
  );
});

afterEach(() => {
  cleanup();
  window.history.replaceState({}, "", "/");
});

describe("Calendar page", () => {
  test("batches real row bars, preserves private detail, changes dates, and batches the next page", async () => {
    render(<CalendarPageStory />);

    await expect.element(calendar.heading).toBeVisible();
    await expect.element(calendar.availability("Confocal microscope")).toBeVisible();
    await expect.element(calendar.availability("Electron microscope")).toBeVisible();
    expect(availabilityRequests).toHaveLength(1);
    expect(availabilityRequests[0].searchParams.get("fields[bookings]")).toBe("id,target,timezone,start,end,state");
    expect(availabilityRequests[0].searchParams.get("where")).toContain("target=in=(IN123,IN124)");
    expect(configurationRequests[0].searchParams.get("where")).toBe("enabled==true");

    await calendar.openItem("Confocal microscope");

    await expect.element(calendar.table).toBeVisible();
    await expect.element(calendar.detail("Confocal microscope")).toBeVisible();
    await expect.element(page.getByRole("article", { name: /Ada Lovelace.*23:30.*02:30/ })).toBeVisible();
    await expect.element(calendar.busy).toBeVisible();
    await expect.element(page.getByText("busy private purpose", { exact: true })).not.toBeInTheDocument();
    expect(detailRequests).toHaveLength(1);
    expect(detailRequests[0].searchParams.get("fields[bookings]")).toBe(
      "id,target,timezone,start,end,state,privacy,purpose,bookedBy,canEdit",
    );
    expect(window.location.search).toContain("date=2026-08-17");
    expect(window.location.search).toContain("target=IN123");

    await calendar.previousDay.click();
    await expect.poll(() => window.location.search).toContain("date=2026-08-16");
    await expect.poll(() => availabilityRequests.length).toBe(2);
    expect(availabilityRequests.at(-1)?.searchParams.get("where")).toContain("end>2026-08-15T22:00:00Z");

    await calendar.today.click();
    await expect.poll(() => window.location.search).toContain("date=2026-08-17");
    await calendar.nextDay.click();
    await expect.poll(() => window.location.search).toContain("date=2026-08-18");
    await expect.poll(() => availabilityRequests.length).toBe(4);
    expect(availabilityRequests.at(-1)?.searchParams.get("where")).toContain("start<2026-08-19T04:00:00Z");

    await calendar.nextPage.click();

    await expect.element(calendar.availability("Mass spectrometer")).toBeVisible();
    await expect.poll(() => configurationRequests.some((url) => url.searchParams.get("page") === "2")).toBe(true);
    expect(availabilityRequests.at(-1)?.searchParams.get("where")).toContain("target=in=(IN125)");
  });
});
