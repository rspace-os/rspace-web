import { HttpResponse, http } from "msw";
import * as v from "valibot";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { createApiV2FilterFields } from "@/modules/common/table-list/api-v2/filterFields";
import { apiV2CollectionMetadataFromOpenApi } from "@/modules/common/table-list/api-v2/metadata";
import {
  type BookingConfiguration,
  BookingConfigurationInputSchema,
  BookingConfigurationUpdateInputSchema,
  bookingConfigurationConfig,
  bookingConfigurationFields,
  fetchBookingConfigurationByTarget,
  fetchBookingConfigurationDetailsByTarget,
} from "../bookingConfiguration";
import { bookableItemsOpenApi, collectionPage } from "../mocks/bookableItemsMocks";

it("offers no operators for openingHours through API metadata", () => {
  const metadata = apiV2CollectionMetadataFromOpenApi<BookingConfiguration>(
    bookableItemsOpenApi,
    "booking-configurations",
  );
  const filters = createApiV2FilterFields({ config: bookingConfigurationConfig, metadata });
  const hours = filters.sourceFields.find((field) => field.name === "openingHours");
  expect(hours).toMatchObject({
    type: "array",
    capabilities: { filterOperators: [], sortable: false },
  });
});

const configuration = {
  id: 7,
  configurationVersion: 0,
  state: "ACTIVE",
  target: {
    relationTo: "instruments",
    value: {
      id: 123,
      name: "Confocal microscope",
      deleted: false,
      parentContainer: {
        relationTo: "containers",
        globalId: "IC456",
        value: { id: 456, globalId: "IC456", name: "Imaging lab" },
      },
    },
    globalId: "IN123",
  },
  enabled: true,
  timezone: "Europe/Berlin",
  slotGranularityMinutes: 5,
  incrementUnit: "MINUTES",
  alignToIncrement: true,
  openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),

  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  updatedAt: "2026-08-10T12:00:00Z",
};

describe("booking configuration reads", () => {
  it("resolves exactly one configuration by target global id", async () => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations", ({ request: received }) => {
        request = received;
        return HttpResponse.json(collectionPage([configuration], 2, false));
      }),
    );

    await expect(fetchBookingConfigurationByTarget("IN123", "token")).resolves.toMatchObject(configuration);

    const url = new URL(request?.url ?? "http://localhost");
    expect(url.searchParams.get("where")).toBe("target==IN123");
    expect(url.searchParams.get("depth")).toBe("2");
    expect(url.searchParams.get("limit")).toBe("2");
  });

  it.each([configuration.target.value.parentContainer, null])(
    "reads expanded or unreadable parent in one request",
    async (parentContainer) => {
      const requests: URL[] = [];
      server.use(
        http.get("/api/v2/booking-configurations", ({ request }) => {
          requests.push(new URL(request.url));
          return HttpResponse.json(
            collectionPage(
              [
                {
                  ...configuration,
                  target: { ...configuration.target, value: { ...configuration.target.value, parentContainer } },
                },
              ],
              2,
            ),
          );
        }),
        http.get("/api/v2/instruments/123", () => {
          throw new Error("Configuration detail must use the expanded relationship");
        }),
      );
      await expect(fetchBookingConfigurationDetailsByTarget("IN123", "token")).resolves.toMatchObject({
        target: { value: { parentContainer } },
      });
      expect(requests).toHaveLength(1);
      expect(requests[0].searchParams.get("depth")).toBe("2");
      expect(requests[0].searchParams.get("fields[instruments]")).toBe("id,globalId,name,deleted,parentContainer");
      expect(requests[0].searchParams.get("fields[containers]")).toBe("id,globalId,name");
    },
  );

  it("escapes target values through the RSQL serializer", async () => {
    server.use(
      http.get("/api/v2/booking-configurations", ({ request }) => {
        expect(new URL(request.url).searchParams.get("where")).toBe('target=="IN1;enabled==false"');
        return HttpResponse.json(
          collectionPage(
            [
              {
                ...configuration,
                target: { ...configuration.target, globalId: "IN1;enabled==false" },
              },
            ],
            2,
          ),
        );
      }),
    );

    await fetchBookingConfigurationByTarget("IN1;enabled==false", "token");
  });

  it.each([
    ["zero results", []],
    ["duplicate results", [configuration, { ...configuration, id: 8 }]],
    ["a null target", [{ ...configuration, target: null }]],
    ["a mismatched target", [{ ...configuration, target: { ...configuration.target, globalId: "IN999" } }]],
  ])("rejects %s", async (_label, docs) => {
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage(docs, 2))));

    await expect(fetchBookingConfigurationByTarget("IN123", "token")).rejects.toThrow(
      "Expected exactly one booking configuration",
    );
  });

  it("rejects malformed and non-success responses", async () => {
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([{ id: 7 }], 2))));
    await expect(fetchBookingConfigurationByTarget("IN123", "token")).rejects.toThrow();

    server.use(http.get("/api/v2/booking-configurations", () => new HttpResponse(null, { status: 500 })));
    await expect(fetchBookingConfigurationByTarget("IN123", "token")).rejects.toThrow("status 500");
  });

  it("selects the opening policy and rejects a response without it", async () => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations", ({ request: received }) => {
        request = received;
        return HttpResponse.json(collectionPage([configuration]));
      }),
    );
    await fetchBookingConfigurationByTarget("IN123", "token");
    const fields = new URL(request?.url ?? "http://localhost").searchParams.get("fields[booking-configurations]");
    expect(fields?.split(",")).toEqual(expect.arrayContaining(["openingHours"]));
    expect(new URL(request?.url ?? "http://localhost").searchParams.get("where")).toBe("target==IN123");

    const { openingHours: _openingHours, ...withoutOpeningHours } = configuration;
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([withoutOpeningHours]))),
    );
    await expect(fetchBookingConfigurationByTarget("IN123", "token")).rejects.toThrow();
  });
});

describe("booking configuration schedules", () => {
  const openingHours = [
    { dayOfWeek: 2, start: "10:00", end: "16:00" },
    { dayOfWeek: 1, start: "08:00", end: "24:00" },
  ];

  it("validates and canonicalizes the complete schedule on create and replacement update", () => {
    const input = { ...configuration, target: { relationTo: "instruments", value: 123 }, openingHours };
    expect(v.parse(BookingConfigurationInputSchema, input).openingHours).toEqual(openingHours.toReversed());
    expect(v.parse(BookingConfigurationUpdateInputSchema, input).openingHours).toEqual(openingHours.toReversed());
    expect(
      v.parse(BookingConfigurationUpdateInputSchema, { ...input, openingHours: [openingHours[1]] }).openingHours,
    ).toEqual([openingHours[1]]);
    expect(
      v.safeParse(BookingConfigurationUpdateInputSchema, { ...input, openingHours: [{ dayOfWeek: 1 }] }).success,
    ).toBe(false);
  });

  it("declares the required array children without exposing schedule filters or sorting", () => {
    const field = bookingConfigurationFields.find((candidate) => candidate.name === "openingHours");
    expect(field).toMatchObject({
      type: "array",
      minRows: 1,
      maxRows: 7,
      capabilities: { sortable: false, filterOperators: [] },
    });
    expect(field?.type === "array" ? field.fields : []).toMatchObject([
      { name: "dayOfWeek", required: true, writeConstraints: { integer: true, minimum: 1, maximum: 7 } },
      { name: "start", required: true, writeConstraints: { format: "time" } },
      { name: "end", required: true, writeConstraints: { format: "time", allow24HourEnd: true } },
    ]);
  });
});

describe("booking configuration Days maximum", () => {
  // The floor is the nominal day count in minutes, whatever the timezone's shortest day is.
  it.each(["UTC", "Australia/Lord_Howe", "Antarctica/Troll"])("applies the same floor in %s", (timezone) => {
    const create = (maxBookingDurationMinutes: number) =>
      v.safeParse(BookingConfigurationInputSchema, {
        target: { relationTo: "instruments", value: 123 },
        enabled: true,
        timezone,
        slotGranularityMinutes: 1440,
        incrementUnit: "DAYS",
        alignToIncrement: true,
        openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),

        bufferBeforeMinutes: 0,
        bufferAfterMinutes: 0,
        maxBookingDurationMinutes,
        allowDoubleBooking: false,
      }).success;
    expect([0, 1440, 1500].map(create)).toEqual([true, true, true]);
    expect([1320, 1380, 1410].map(create)).toEqual([false, false, false]);
  });
});
