import { HttpResponse, http, type RequestHandler } from "msw";
import { apiV2CollectionResponse, apiV2ListResult } from "@/__tests__/mocks/apiV2CollectionMock";
import { BOOKING_READ_FIELDS } from "@/modules/booking/domain/booking";
import { bookableItemFixtures, sampleBookingEvents } from "./bookableItemFixtures";
import { bookableItemsOpenApi } from "./bookableItemsOpenApi";

const detailBookingEvents = sampleBookingEvents.map((booking) => ({
  ...booking,
  canEdit: booking.privacy === "full",
  createdAt: "2026-08-01T09:00:00Z",
  updatedAt: "2026-08-01T09:00:00Z",
}));

export function collectionPage(docs: readonly unknown[], limit = 20, totals = true) {
  const result = apiV2ListResult(docs, docs.length, limit, 1);
  if (totals) return result;
  const { totalDocs: _totalDocs, totalPages: _totalPages, ...page } = result;
  return page;
}

const hazardClass = [
  {
    id: "SF152",
    selector: "customFields.SF152",
    label: "Hazard class",
    type: "text" as const,
    jsonType: "string" as const,
    operators: ["==", "!=", "=in=", "=out=", "=contains=", "=like=", "=exists="] as const,
    supportsWildcards: true,
    columnSelectable: true,
    sortable: false,
    source: { id: "IT102", label: "Cell line template" },
    options: [] as string[],
  },
];

const requiresTraining = [
  {
    id: "SF160",
    selector: "customFields.SF160",
    label: "Requires training",
    type: "radio" as const,
    jsonType: "string" as const,
    operators: ["==", "!=", "=in=", "=out=", "=exists="] as const,
    supportsWildcards: false,
    columnSelectable: true,
    sortable: false,
    source: { id: "IT102", label: "Cell line template" },
    options: ["yes", "no", "supervised only"],
  },
];

const definitions = [...hazardClass, ...requiresTraining];

export function bookableItemsHandlers(onCollectionRequest: (request: Request) => void): RequestHandler[] {
  return [
    http.get("/api/v2/openapi.json", () => HttpResponse.json(bookableItemsOpenApi)),
    http.get("/api/v2/booking-configurations", ({ request }) => {
      onCollectionRequest(request);
      const where = decodeURIComponent(new URL(request.url).searchParams.get("where") ?? "");
      const targetIds = where.match(/target=in=\(([^)]*)\)/)?.[1].split(",");
      const targetId = where.match(/^target==([^;]+)$/)?.[1].replaceAll('"', "");
      const docs = where.includes("id==-1")
        ? []
        : targetId
          ? bookableItemFixtures.filter((fixture) => fixture.target.globalId === targetId)
          : targetIds
            ? bookableItemFixtures.filter((fixture) => targetIds.includes(fixture.target.globalId))
            : bookableItemFixtures;
      return apiV2CollectionResponse("booking-configurations", request, docs);
    }),
    http.get("/api/v2/booking-catalogue", ({ request }) => {
      onCollectionRequest(request);
      const url = new URL(request.url);
      const query = (url.searchParams.get("q") ?? "").toLocaleLowerCase();
      const target = url.searchParams.get("target");
      const locations = url.searchParams.getAll("location");
      const page = Number(url.searchParams.get("page") ?? "1");
      const pageSize = Number(url.searchParams.get("limit") ?? "20");
      const matching = bookableItemFixtures.filter(
        (fixture) =>
          (!query ||
            fixture.target.value.name.toLocaleLowerCase().includes(query) ||
            fixture.target.globalId.toLocaleLowerCase().includes(query)) &&
          (!target || fixture.target.globalId === target) &&
          (locations.length === 0 ||
            (fixture.target.value.parentContainer?.globalId !== undefined &&
              locations.includes(fixture.target.value.parentContainer?.globalId))),
      );
      const start = (page - 1) * pageSize;
      return HttpResponse.json({
        items: matching.slice(start, start + pageSize).map((fixture) => ({
          configurationId: fixture.id,
          configurationVersion: fixture.configurationVersion,
          targetType: "INSTRUMENT",
          targetId: fixture.target.value.id,
          globalId: fixture.target.globalId,
          name: fixture.target.value.name,
          timezone: fixture.timezone,
          slotGranularityMinutes: fixture.slotGranularityMinutes,
          openingHours: fixture.openingHours,
          bufferBeforeMinutes: fixture.bufferBeforeMinutes,
          bufferAfterMinutes: fixture.bufferAfterMinutes,
          maxBookingDurationMinutes: fixture.maxBookingDurationMinutes,
          allowDoubleBooking: fixture.allowDoubleBooking,
          effectiveRole: fixture.effectiveRole,
          capabilities: fixture.capabilities,
          location:
            fixture.target.value.parentContainer?.value.name && fixture.target.value.parentContainer?.globalId
              ? {
                  name: fixture.target.value.parentContainer?.value.name,
                  globalId: fixture.target.value.parentContainer?.globalId,
                }
              : null,
        })),
        page,
        pageSize,
        total: matching.length,
        facets: { types: ["INSTRUMENT"] },
      });
    }),
    http.get("/api/v2/booking-catalogue/locations", ({ request }) => {
      const url = new URL(request.url);
      const page = Number(url.searchParams.get("page") ?? "1");
      const pageSize = Number(url.searchParams.get("limit") ?? "20");
      const locations = Array.from(
        new Map(
          bookableItemFixtures.flatMap((fixture) => {
            const globalId = fixture.target.value.parentContainer?.globalId;
            const name = fixture.target.value.parentContainer?.value.name;
            return globalId && name ? [[globalId, { globalId, name }] as const] : [];
          }),
        ).values(),
      );
      const start = (page - 1) * pageSize;
      return HttpResponse.json({
        items: locations.slice(start, start + pageSize),
        page,
        pageSize,
        total: locations.length,
      });
    }),
    http.get("/api/v2/instruments", () => HttpResponse.json(collectionPage([]))),
    http.get("/api/v2/instruments/fields/customFields", ({ request }) => {
      const url = new URL(request.url);
      const ids = url.searchParams.get("ids");
      const search = (url.searchParams.get("search") ?? "").toLowerCase();
      const matching =
        ids !== null
          ? definitions.filter((f) => ids.split(",").includes(f.id))
          : definitions.filter((f) => f.label.toLowerCase().includes(search));
      return HttpResponse.json({ fields: matching, totalFields: matching.length, hasMore: false, page: 1, limit: 20 });
    }),
    // The instrument target also publishes an Inventory extra-fields namespace; these items have none.
    http.get("/api/v2/instruments/fields/extraFields", () =>
      HttpResponse.json({ fields: [], totalFields: 0, hasMore: false, page: 1, limit: 20 }),
    ),
  ];
}

export function bookableItemDetailsHandlers(): RequestHandler[] {
  return [
    http.get("/api/v2/booking-configurations", ({ request }) => {
      const where = new URL(request.url).searchParams.get("where") ?? "";
      const targetId = where.match(/^target==([^;]+)$/)?.[1].replaceAll('"', "");
      if (!targetId) return undefined;
      return HttpResponse.json(
        collectionPage(
          bookableItemFixtures.filter((fixture) => fixture.target.globalId === targetId),
          20,
          new URL(request.url).searchParams.get("totals") === "true",
        ),
      );
    }),
    http.get("/api/v2/bookings", ({ request }) => {
      const url = new URL(request.url);
      if (url.searchParams.get("fields[bookings]") !== BOOKING_READ_FIELDS) return undefined;
      const where = url.searchParams.get("where") ?? "";
      const targetId = where.match(/target==([^;]+)/)?.[1].replaceAll('"', "");
      if (!targetId) return undefined;
      const docs = detailBookingEvents.filter((booking) => booking.target.globalId === targetId);
      return HttpResponse.json(collectionPage(docs));
    }),
    http.get("/api/v2/booking-configurations/7/audit", () =>
      HttpResponse.json({
        ...collectionPage([
          {
            eventId: "a".repeat(64),
            timestamp: "2026-08-25T10:42:18Z",
            username: "ada",
            fullName: "Ada Lovelace",
            domain: "RECORD",
            action: "WRITE",
            description: "Updated booking configuration IN123",
            payload: { maxBookingDurationMinutes: 60 },
          },
        ]),
        snapshotDate: "2026-08-25",
        snapshotFingerprint: "b".repeat(64),
      }),
    ),
    http.get("/api/v2/booking-configurations/7/calendar-subscription", () =>
      HttpResponse.json({ active: false, updatedAt: null, subscriptionUrl: null }),
    ),
    http.post("/api/v2/booking-configurations/7/calendar-subscription", () =>
      HttpResponse.json({
        active: true,
        updatedAt: "2026-08-27T12:00:00.000Z",
        subscriptionUrl: `https://rspace.example/public/booking/calendars/feed.ics?token=${"c".repeat(43)}`,
      }),
    ),
  ];
}
