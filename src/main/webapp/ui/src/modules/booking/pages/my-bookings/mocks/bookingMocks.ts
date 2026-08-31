import { HttpResponse, http, type RequestHandler } from "msw";

export const bookingsOpenApi = {
  paths: {
    "/api/v2/bookings": {
      get: {
        parameters: [
          {
            name: "sort",
            "x-rspace-sort": {
              fields: ["id", "start", "end", "state", "createdAt", "updatedAt"],
              default: ["start", "id"],
              maximumFields: 5,
            },
          },
          {
            name: "where",
            schema: { type: "string", maxLength: 32768 },
            "x-rspace-filter": {
              maximumComparisons: 50,
              maximumLikeComparisons: 10,
              maximumNesting: 10,
              maximumArguments: 1000,
              selectors: {
                id: { schema: { type: "integer" }, operators: ["==", "!=", "=in="], wildcards: false },
                requesterId: {
                  schema: { type: "integer" },
                  operators: ["==", "!=", "=gt=", "=ge=", "=lt=", "=le=", "=in=", "=out="],
                  wildcards: false,
                },
                kind: {
                  schema: { type: "string", enum: ["BOOKING", "MAINTENANCE"] },
                  operators: ["==", "!=", "=in=", "=out="],
                  wildcards: false,
                },
                start: {
                  schema: { type: "string", format: "date-time" },
                  operators: ["==", "!=", "=gt=", "=ge=", "=lt=", "=le="],
                  wildcards: false,
                },
                end: {
                  schema: { type: "string", format: "date-time" },
                  operators: ["==", "!=", "=gt=", "=ge=", "=lt=", "=le="],
                  wildcards: false,
                },
                "target.name": {
                  schema: { type: "string" },
                  operators: ["==", "=contains=", "=like="],
                  wildcards: true,
                  title: "Instrument name",
                },
              },
            },
            "x-rspace-relationship-fields": {
              "target.name": {
                schema: { type: "string" },
                operators: ["==", "=contains=", "=like="],
                wildcards: true,
                title: "Instrument name",
              },
            },
          },
          { name: "limit", schema: { type: "integer", default: 20, maximum: 100 } },
          {
            name: "fields",
            "x-rspace-allowed-fields": {
              bookings: [
                "id",
                "requesterId",
                "kind",
                "target",
                "canViewConfiguration",
                "timezone",
                "start",
                "end",
                "state",
                "purpose",
                "bookedBy",
                "privacy",
                "canEdit",
                "createdAt",
                "updatedAt",
              ],
            },
          },
        ],
      },
    },
  },
};

const target = (id: number, name: string) => ({
  relationTo: "booking-instruments" as const,
  value: { id, name, deleted: false },
  globalId: `IN${id}`,
});

export const upcomingBooking = {
  id: 41,
  kind: "BOOKING",
  target: target(123, "Confocal microscope"),
  canViewConfiguration: true,
  timezone: "Pacific/Auckland",
  start: "2020-08-23T09:00:00Z",
  end: "2030-08-23T10:00:00Z",
  purpose: "Scope training",
};

export const pastBooking = {
  id: 42,
  kind: "BOOKING",
  target: target(124, "Electron microscope"),
  canViewConfiguration: true,
  timezone: "UTC",
  start: "2020-08-23T09:00:00Z",
  end: "2020-08-23T10:00:00Z",
  purpose: "Completed run",
};

export const roleLostBooking = {
  ...upcomingBooking,
  id: 43,
  canViewConfiguration: false,
  purpose: "Retained requester details",
};

function page(docs: readonly unknown[]) {
  return {
    docs,
    totalDocs: docs.length,
    limit: 20,
    page: 1,
    pagingCounter: 1,
    totalPages: docs.length === 0 ? 0 : 1,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}

function matchesEndFilter(document: unknown, where: string | null): boolean {
  const filter = where?.match(/end=(gt|le)=([^;)]+)/);
  if (!filter) return true;
  if (typeof document !== "object" || document === null || !("end" in document)) return false;
  const end = Date.parse(String(document.end));
  const boundary = Date.parse(filter[2]);
  return filter[1] === "gt" ? end > boundary : end <= boundary;
}

export function bookingHandlers(
  onListRequest: (url: URL) => void = () => undefined,
  onCountRequest: (url: URL) => void = () => undefined,
  docs?: readonly unknown[],
): RequestHandler[] {
  return [
    http.get("/api/v2/openapi.json", () => HttpResponse.json(bookingsOpenApi)),
    http.get("/api/v2/bookings", ({ request }) => {
      const url = new URL(request.url);
      onListRequest(url);
      const source = docs ?? [upcomingBooking, pastBooking];
      return HttpResponse.json(
        page(source.filter((document) => matchesEndFilter(document, url.searchParams.get("where")))),
      );
    }),
    http.get("/api/v2/bookings/count", ({ request }) => {
      onCountRequest(new URL(request.url));
      return HttpResponse.json({ totalDocs: 2 });
    }),
  ];
}
