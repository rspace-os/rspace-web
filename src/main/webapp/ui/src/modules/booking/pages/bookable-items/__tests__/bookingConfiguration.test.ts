import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import {
  fetchBookingConfiguration,
  fetchBookingConfigurationByTarget,
  fetchBookingOwnershipCandidates,
} from "../bookingConfiguration";

const configuration = {
  id: 7,
  configurationVersion: 0,
  state: "ACTIVE",
  target: {
    relationTo: "booking-instruments",
    value: { id: 123, name: "Confocal microscope", deleted: false },
    globalId: "IN123",
  },
  enabled: true,
  timezone: "Europe/Berlin",
  slotGranularityMinutes: 5,
  openingStart: "00:00",
  openingEnd: "24:00",
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  updatedAt: "2026-08-10T12:00:00Z",
};

function envelope(docs: unknown[]) {
  return {
    docs,
    totalDocs: docs.length,
    limit: 2,
    page: 1,
    pagingCounter: 1,
    totalPages: docs.length === 0 ? 0 : 1,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}

describe("booking configuration reads", () => {
  it("reads a configuration by numeric id with the complete projection", async () => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations/7", ({ request: received }) => {
        request = received;
        return HttpResponse.json(configuration);
      }),
    );

    await expect(fetchBookingConfiguration(7, "token", new AbortController().signal)).resolves.toMatchObject(
      configuration,
    );

    const url = new URL(request?.url ?? "http://localhost");
    expect(url.searchParams.get("depth")).toBe("1");
    expect(request?.headers.get("Authorization")).toBe("Bearer token");
  });

  it("resolves exactly one configuration by target global id", async () => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations", ({ request: received }) => {
        request = received;
        return HttpResponse.json(envelope([configuration]));
      }),
    );

    await expect(fetchBookingConfigurationByTarget("IN123", "token")).resolves.toMatchObject(configuration);

    const url = new URL(request?.url ?? "http://localhost");
    expect(url.searchParams.get("where")).toBe("target==IN123");
    expect(url.searchParams.get("depth")).toBe("1");
    expect(url.searchParams.get("limit")).toBe("2");
  });

  it("escapes target values through the RSQL serializer", async () => {
    server.use(
      http.get("/api/v2/booking-configurations", ({ request }) => {
        expect(new URL(request.url).searchParams.get("where")).toBe('target=="IN1;enabled==false"');
        return HttpResponse.json(
          envelope([
            {
              ...configuration,
              target: { ...configuration.target, globalId: "IN1;enabled==false" },
            },
          ]),
        );
      }),
    );

    await fetchBookingConfigurationByTarget("IN1;enabled==false", "token");
  });

  it("loads bounded ownership candidates through a single escaped target query", async () => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations", ({ request: received }) => {
        request = received;
        return HttpResponse.json(
          envelope([
            {
              ...configuration,
              capabilities: {
                canEditConfiguration: true,
                canViewAudit: true,
                canViewAccess: true,
                canManageAssignments: true,
                canManageOwners: true,
                canCreateBooking: true,
                canManageOwnBookings: true,
                canManageAllEvents: true,
                canCreateBlockout: true,
                canSubscribeCalendar: true,
                canLeaveConfiguration: false,
              },
            },
          ]),
        );
      }),
    );

    const result = await fetchBookingOwnershipCandidates(["IN123", "IN456"], "token");

    expect(result).toHaveLength(1);
    expect(result[0].capabilities.canManageOwners).toBe(true);
    const url = new URL(request?.url ?? "http://localhost");
    expect(url.searchParams.get("where")).toBe("target=in=(IN123,IN456)");
    expect(url.searchParams.get("limit")).toBe("2");
  });

  it.each([
    ["zero results", []],
    ["duplicate results", [configuration, { ...configuration, id: 8 }]],
    ["a null target", [{ ...configuration, target: null }]],
    ["a mismatched target", [{ ...configuration, target: { ...configuration.target, globalId: "IN999" } }]],
  ])("rejects %s", async (_label, docs) => {
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope(docs))));

    await expect(fetchBookingConfigurationByTarget("IN123", "token")).rejects.toThrow(
      "Expected exactly one booking configuration",
    );
  });

  it("rejects malformed and non-success responses", async () => {
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([{ id: 7 }]))));
    await expect(fetchBookingConfigurationByTarget("IN123", "token")).rejects.toThrow();

    server.use(http.get("/api/v2/booking-configurations", () => new HttpResponse(null, { status: 500 })));
    await expect(fetchBookingConfigurationByTarget("IN123", "token")).rejects.toThrow("status 500");
  });
});
