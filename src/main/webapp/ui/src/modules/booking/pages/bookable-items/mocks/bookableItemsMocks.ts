import { HttpResponse, http, type RequestHandler } from "msw";

const openApi = {
  paths: {
    "/api/v2/booking-configurations": {
      get: {
        parameters: [
          {
            name: "sort",
            "x-rspace-sort": {
              fields: ["id", "enabled", "timezone", "updatedAt"],
              default: ["id"],
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
                enabled: { schema: { type: "boolean" }, operators: ["==", "!=", "=out="], wildcards: false },
                timezone: {
                  schema: { type: "string" },
                  operators: ["==", "!=", "=contains="],
                  wildcards: true,
                },
                updatedAt: {
                  schema: { type: "string", format: "date-time" },
                  operators: ["==", "=gt=", "=lt="],
                  wildcards: false,
                },
                "target.id": {
                  schema: { type: "integer", format: "int64" },
                  operators: ["=="],
                  wildcards: false,
                  title: "Instrument ID",
                },
                "target.name": {
                  schema: { type: "string" },
                  operators: ["==", "=contains=", "=like="],
                  wildcards: true,
                  title: "Instrument name",
                },
                "target.deleted": {
                  schema: { type: "boolean" },
                  operators: ["=="],
                  wildcards: false,
                  title: "Deleted",
                },
              },
            },
          },
          { name: "limit", schema: { type: "integer", default: 20, maximum: 100 } },
          {
            name: "fields",
            "x-rspace-allowed-fields": {
              "booking-configurations": ["id", "target", "enabled", "timezone", "updatedAt"],
            },
          },
        ],
      },
    },
  },
};

const bookingConfiguration = {
  id: 7,
  target: {
    relationTo: "instruments",
    value: { id: 123, name: "Confocal microscope", deleted: false },
    globalId: "IN123",
  },
  enabled: true,
  timezone: "Europe/Berlin",
  updatedAt: "2026-08-10T10:00:00Z",
};

function collectionPage(docs: readonly unknown[]) {
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

export function bookableItemsHandlers(onCollectionRequest: (request: Request) => void): RequestHandler[] {
  return [
    http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
    http.get("/api/v2/booking-configurations", ({ request }) => {
      onCollectionRequest(request);
      return HttpResponse.json(collectionPage([bookingConfiguration]));
    }),
    http.get("/api/v2/instruments", () => HttpResponse.json(collectionPage([]))),
  ];
}
