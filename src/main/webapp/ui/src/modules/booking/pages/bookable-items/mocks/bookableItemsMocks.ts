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
            "x-rspace-relationship-fields": {
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
              "target.globalId": {
                schema: { type: "string" },
                operators: [],
                wildcards: false,
                title: "Global ID",
              },
              "target.deleted": {
                schema: { type: "boolean" },
                operators: ["=="],
                wildcards: false,
                title: "Deleted",
              },
            },
            "x-rspace-runtime-fields": [
              {
                namespace: "target.customFields",
                catalog: "/api/v2/instruments/fields/customFields",
                responseField: "target.customFields",
                via: "target",
                viaResource: "instruments",
                filterable: true,
                columnSelectable: true,
                sortable: false,
                maximumProjections: 50,
                catalogDefaultLimit: 50,
                catalogMaximumLimit: 200,
              },
            ],
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
  "target.customFields": { SF152: "BSL-2", SF160: "yes" },
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

const hazardClass = [
  {
    id: "SF152",
    selector: "customFields.SF152",
    label: "Hazard class",
    type: "text" as const,
    jsonType: "string" as const,
    operators: ["==", "!=", "=contains=", "=exists="] as const,
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
    operators: ["==", "!=", "=in=", "=exists="] as const,
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
    http.get("/api/v2/openapi.json", () => HttpResponse.json(openApi)),
    http.get("/api/v2/booking-configurations", ({ request }) => {
      onCollectionRequest(request);
      return HttpResponse.json(collectionPage([bookingConfiguration]));
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
  ];
}
