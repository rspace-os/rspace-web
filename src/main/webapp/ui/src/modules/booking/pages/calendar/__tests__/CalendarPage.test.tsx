import "@/__tests__/__mocks__/matchMedia";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
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

describe("CalendarPage", () => {
  it("keeps the list when a row opens and renders private-safe detail, agenda, and edit actions", async () => {
    const detailRequests: URL[] = [];
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(bookingConfigurationOpenApi)),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionResponse([configuration]))),
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        if (url.searchParams.get("fields[bookings]")?.includes("privacy")) {
          detailRequests.push(url);
          return HttpResponse.json(collectionResponse([fullDetail, busyDetail]));
        }
        return HttpResponse.json(collectionResponse([bookingSummary]));
      }),
    );
    const { router } = await renderCalendar();
    const row = await screen.findByRole("row", { name: /Confocal microscope/ });

    fireEvent.click(within(row).getByRole("button", { name: /Confocal microscope/ }));

    await waitFor(() => expect(router.state.location.search).toMatchObject({ target: "IN123" }));
    expect(await screen.findByRole("region", { name: "Bookings for Confocal microscope" })).toBeVisible();
    expect(screen.getByRole("table", { name: /Bookable items/ })).toBeVisible();
    expect(screen.getAllByText("Ada Lovelace (ada)")).not.toHaveLength(0);
    expect(screen.getAllByText("Image plate 4")).not.toHaveLength(0);
    expect(screen.getAllByText("Busy")).not.toHaveLength(0);
    expect(screen.queryByText("private server detail")).not.toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Edit" })).toHaveLength(1);
    expect(screen.getByRole("link", { name: "Edit" })).toHaveAttribute(
      "href",
      "/booking/calendar/bookings/41?date=2026-08-17&target=IN123",
    );
    expect(detailRequests).toHaveLength(1);
    expect(detailRequests[0].searchParams.get("fields[bookings]")).toBe(
      "id,target,timezone,start,end,state,privacy,purpose,bookedBy,canEdit",
    );
    expect(router.state.location.search).toMatchObject({ date: "2026-08-17", target: "IN123" });
  });

  it("uses typed date controls and updates query bounds", async () => {
    const user = userEvent.setup();
    const availabilityWhere: string[] = [];
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(bookingConfigurationOpenApi)),
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionResponse([configuration]))),
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        if (!url.searchParams.get("fields[bookings]")?.includes("privacy")) {
          availabilityWhere.push(url.searchParams.get("where") ?? "");
        }
        return HttpResponse.json(collectionResponse([]));
      }),
    );
    const { router } = await renderCalendar();
    await screen.findByText("Confocal microscope");

    await user.click(screen.getByRole("button", { name: "Next day" }));

    await waitFor(() => expect(router.state.location.search).toMatchObject({ date: "2026-08-18" }));
    await waitFor(() => expect(availabilityWhere).toHaveLength(2));
    expect(availabilityWhere[1]).toContain("start<2026-08-18T22:00:00Z");
    expect(availabilityWhere[1]).toContain("end>2026-08-17T22:00:00Z");

    await user.click(screen.getByRole("button", { name: "Previous day" }));
    await waitFor(() => expect(router.state.location.search).toMatchObject({ date: "2026-08-17" }));
  });

  it("resolves selected detail outside the visible table page without changing the page", async () => {
    const configurationWhere: string[] = [];
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/openapi.json", () => HttpResponse.json(bookingConfigurationOpenApi)),
      http.get("/api/v2/booking-configurations", ({ request }) => {
        const where = new URL(request.url).searchParams.get("where") ?? "";
        configurationWhere.push(where);
        return HttpResponse.json(
          collectionResponse(where.includes("target==IN123") ? [configuration] : [secondConfiguration]),
        );
      }),
      http.get("/api/v2/bookings", ({ request }) => {
        const fields = new URL(request.url).searchParams.get("fields[bookings]") ?? "";
        return HttpResponse.json(collectionResponse(fields.includes("privacy") ? [fullDetail] : []));
      }),
    );
    await renderCalendar("/booking/calendar?date=2026-08-17&target=IN123");

    expect(await screen.findByText("Electron microscope")).toBeVisible();
    expect(await screen.findByRole("region", { name: "Bookings for Confocal microscope" })).toBeVisible();
    expect(screen.getByText("Electron microscope")).toBeVisible();
    expect(configurationWhere).toEqual(expect.arrayContaining([expect.stringContaining("target==IN123")]));
  });
});
