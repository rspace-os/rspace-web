import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import {
  bookingTimeZoneOptions,
  fetchBookingDisplayPreferences,
  minuteOfDay,
  replaceBookingDisplayPreferences,
  resolveBookingDisplayPreferences,
  todayInTimeZone,
} from "../bookingDisplayPreferences";

const inherited = {
  availabilityWindowStart: "08:00",
  availabilityWindowEnd: "18:00",
  timezoneMode: "BROWSER" as const,
  customTimezone: null,
  institutionTimezone: "Europe/Berlin",
  overridden: false,
};

describe("Booking display preferences", () => {
  it("resolves browser, institution, and custom modes from one document shape", () => {
    expect(resolveBookingDisplayPreferences(inherited, "Pacific/Auckland")).toMatchObject({
      timeZone: "Pacific/Auckland",
      availabilityWindow: { startMinute: 480, endMinute: 1080 },
    });
    expect(
      resolveBookingDisplayPreferences({ ...inherited, timezoneMode: "INSTITUTION" }, "Pacific/Auckland"),
    ).toMatchObject({ timeZone: "Europe/Berlin" });
    expect(
      resolveBookingDisplayPreferences(
        { ...inherited, timezoneMode: "CUSTOM", customTimezone: "America/New_York", overridden: true },
        "Pacific/Auckland",
      ),
    ).toMatchObject({ timeZone: "America/New_York", overridden: true });
  });

  it("falls back to the institution zone when browser detection is missing or invalid", () => {
    expect(resolveBookingDisplayPreferences(inherited, null).timeZone).toBe("Europe/Berlin");
    expect(resolveBookingDisplayPreferences(inherited, "Not/A_Zone").timeZone).toBe("Europe/Berlin");
  });

  it("supports 24:00 and computes today in an explicit zone", () => {
    expect(minuteOfDay("24:00")).toBe(1440);
    expect(todayInTimeZone("Pacific/Auckland", new Date("2026-08-28T12:30:00Z"))).toBe("2026-08-29");
    expect(todayInTimeZone("America/New_York", new Date("2026-08-28T02:30:00Z"))).toBe("2026-08-27");
  });

  it("always includes required timezone identifiers in the options", () => {
    expect(bookingTimeZoneOptions("Etc/GMT+1", "Pacific/Auckland", "America/New_York")).toEqual(
      expect.arrayContaining(["UTC", "Etc/GMT+1", "Pacific/Auckland", "America/New_York"]),
    );
  });

  it("rejects malformed response documents", async () => {
    server.use(
      http.get("/api/v2/users/me/booking-preferences", () =>
        HttpResponse.json({ ...inherited, availabilityWindowEnd: "08:00" }),
      ),
    );

    await expect(fetchBookingDisplayPreferences("token")).rejects.toThrow();
  });

  it("sends only the strict replacement document", async () => {
    let body: unknown;
    server.use(
      http.put("/api/v2/users/me/booking-preferences", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({
          ...inherited,
          availabilityWindowStart: "09:00",
          availabilityWindowEnd: "17:00",
          timezoneMode: "INSTITUTION",
          overridden: true,
        });
      }),
    );

    await replaceBookingDisplayPreferences(
      {
        availabilityWindowStart: "09:00",
        availabilityWindowEnd: "17:00",
        timezoneMode: "INSTITUTION",
        customTimezone: null,
      },
      "token",
    );
    expect(body).toEqual({
      availabilityWindowStart: "09:00",
      availabilityWindowEnd: "17:00",
      timezoneMode: "INSTITUTION",
      customTimezone: null,
    });
  });
});
