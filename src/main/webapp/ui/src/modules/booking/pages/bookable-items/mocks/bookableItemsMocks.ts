import { HttpResponse, http, type RequestHandler } from "msw";
import { DEFAULT_SCHEDULING_SETTINGS, schedulingSettingsFieldNames } from "../schedulingSettings";

export const bookableItemsOpenApi = {
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
                id: {
                  schema: { type: "integer", format: "int64" },
                  operators: ["=="],
                  wildcards: false,
                },
                target: {
                  schema: { type: "string" },
                  operators: ["=in="],
                  wildcards: false,
                },
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
              "booking-configurations": [
                "id",
                "target",
                "enabled",
                "timezone",
                ...schedulingSettingsFieldNames,
                "updatedAt",
              ],
            },
          },
        ],
      },
    },
  },
};

export const bookableItemFixtures = [
  {
    id: 7,
    target: {
      relationTo: "instruments",
      value: {
        id: 123,
        name: "Confocal microscope",
        deleted: false,
        parentContainerName: "Imaging lab",
        parentContainerGlobalId: "IC456",
      },
      globalId: "IN123",
    },
    enabled: true,
    timezone: "Europe/Berlin",
    ...DEFAULT_SCHEDULING_SETTINGS,
    updatedAt: "2026-08-10T10:00:00Z",
    "target.customFields": { SF152: "BSL-2", SF160: "yes" },
  },
  {
    id: 8,
    target: {
      relationTo: "instruments",
      value: {
        id: 124,
        name: "Electron microscope",
        deleted: false,
        parentContainerName: "Workbench",
        parentContainerGlobalId: "BE457",
      },
      globalId: "IN124",
    },
    enabled: true,
    timezone: "America/New_York",
    ...DEFAULT_SCHEDULING_SETTINGS,
    updatedAt: "2026-08-11T11:00:00Z",
  },
  {
    id: 9,
    target: {
      relationTo: "instruments",
      value: { id: 125, name: "Mass spectrometer", deleted: false },
      globalId: "IN125",
    },
    enabled: true,
    timezone: "UTC",
    ...DEFAULT_SCHEDULING_SETTINGS,
    updatedAt: "2026-08-12T12:00:00Z",
  },
  {
    id: 10,
    target: {
      relationTo: "instruments",
      value: {
        id: 126,
        name: "Flow cytometer",
        deleted: false,
        // The API uses the same null pair for an unreadable parent as it does
        // for a parent that has since been deleted.
        parentContainerName: null,
        parentContainerGlobalId: null,
      },
      globalId: "IN126",
    },
    enabled: true,
    timezone: "Asia/Singapore",
    ...DEFAULT_SCHEDULING_SETTINGS,
    updatedAt: "2026-08-13T13:00:00Z",
  },
  {
    id: 11,
    target: {
      relationTo: "instruments",
      value: {
        id: 127,
        name: "Microplate reader",
        deleted: false,
        parentContainerName: null,
        parentContainerGlobalId: null,
      },
      globalId: "IN127",
    },
    enabled: true,
    timezone: "Europe/Berlin",
    ...DEFAULT_SCHEDULING_SETTINGS,
    updatedAt: "2026-08-14T14:00:00Z",
  },
] as const;

export const sampleBookingEvents = [
  {
    id: 41,
    target: bookableItemFixtures[0].target,
    timezone: bookableItemFixtures[0].timezone,
    start: "2026-08-17T06:00:00Z",
    end: "2026-08-17T07:30:00Z",
    state: "CONFIRMED",
    privacy: "full",
    purpose: "Calibrate the objective",
    bookedBy: "Ada Lovelace (ada)",
  },
  {
    id: 42,
    target: bookableItemFixtures[0].target,
    timezone: bookableItemFixtures[0].timezone,
    start: "2026-08-17T12:00:00Z",
    end: "2026-08-17T13:00:00Z",
    state: "CONFIRMED",
    privacy: "busy",
    purpose: null,
    bookedBy: null,
  },
  {
    id: 43,
    target: bookableItemFixtures[1].target,
    timezone: bookableItemFixtures[1].timezone,
    start: "2026-08-17T00:00:00Z",
    end: "2026-08-17T01:00:00Z",
    state: "CONFIRMED",
    privacy: "full",
    purpose: "Cryo-grid screening",
    bookedBy: "Grace Hopper (grace)",
  },
  {
    id: 44,
    target: bookableItemFixtures[2].target,
    timezone: bookableItemFixtures[2].timezone,
    start: "2026-08-17T00:00:00Z",
    end: "2026-08-18T00:00:00Z",
    state: "CONFIRMED",
    privacy: "busy",
    purpose: null,
    bookedBy: null,
  },
  {
    id: 45,
    target: bookableItemFixtures[3].target,
    timezone: bookableItemFixtures[3].timezone,
    start: "2026-08-17T02:00:00Z",
    end: "2026-08-17T03:30:00Z",
    state: "CONFIRMED",
    privacy: "full",
    purpose: "Cell-cycle panel",
    bookedBy: "Katherine Johnson (katherine)",
  },
  {
    id: 46,
    target: bookableItemFixtures[0].target,
    timezone: bookableItemFixtures[0].timezone,
    start: "2026-08-16T21:30:00Z",
    end: "2026-08-17T00:30:00Z",
    state: "CONFIRMED",
    privacy: "full",
    purpose: "Overnight acquisition",
    bookedBy: "Marie Curie (marie)",
  },
] as const;

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
    http.get("/api/v2/openapi.json", () => HttpResponse.json(bookableItemsOpenApi)),
    http.get("/api/v2/booking-configurations", ({ request }) => {
      onCollectionRequest(request);
      const where = decodeURIComponent(new URL(request.url).searchParams.get("where") ?? "");
      const targetIds = where.match(/target=in=\(([^)]*)\)/)?.[1].split(",");
      const docs = where.includes("id==-1")
        ? []
        : targetIds
          ? bookableItemFixtures.filter((fixture) => targetIds.includes(fixture.target.globalId))
          : bookableItemFixtures;
      return HttpResponse.json(collectionPage(docs));
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
