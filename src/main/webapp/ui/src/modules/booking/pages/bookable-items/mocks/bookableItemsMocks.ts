import { HttpResponse, http, type RequestHandler } from "msw";
import {
  DEFAULT_SCHEDULING_SETTINGS,
  schedulingSettingsFieldNames,
} from "@/modules/booking/configuration/schedulingSettings";
import { BOOKING_READ_FIELDS } from "@/modules/booking/domain/booking";

export const ownerBookingAccess = {
  effectiveRole: "OWNER",
  roleSources: [],
  capabilities: {
    canEditConfiguration: true,
    canArchiveConfiguration: true,
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
  ownerHealth: { hasEffectiveOwner: true },
} as const;

export const bookerBookingAccess = {
  effectiveRole: "BOOKER",
  roleSources: [],
  capabilities: {
    canEditConfiguration: false,
    canArchiveConfiguration: false,
    canViewAudit: false,
    canViewAccess: false,
    canManageAssignments: false,
    canManageOwners: false,
    canCreateBooking: true,
    canManageOwnBookings: true,
    canManageAllEvents: false,
    canCreateBlockout: false,
    canSubscribeCalendar: true,
    canLeaveConfiguration: false,
  },
} as const;

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
                viaResource: "booking-instruments",
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
                "effectiveRole",
                "roleSources",
                "capabilities",
                "ownerHealth",
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
    ...ownerBookingAccess,
    target: {
      relationTo: "booking-instruments",
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
    openingStart: "08:00",
    openingEnd: "17:00",
    updatedAt: "2026-08-10T10:00:00Z",
    "target.customFields": { SF152: "BSL-2", SF160: "yes" },
  },
  {
    id: 8,
    ...ownerBookingAccess,
    target: {
      relationTo: "booking-instruments",
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
    ...ownerBookingAccess,
    target: {
      relationTo: "booking-instruments",
      value: {
        id: 125,
        name: "Mass spectrometer",
        deleted: false,
        parentContainerName: "Mass spectrometry lab",
        parentContainerGlobalId: "IC458",
      },
      globalId: "IN125",
    },
    enabled: true,
    timezone: "UTC",
    ...DEFAULT_SCHEDULING_SETTINGS,
    updatedAt: "2026-08-12T12:00:00Z",
  },
  {
    id: 10,
    ...ownerBookingAccess,
    target: {
      relationTo: "booking-instruments",
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
    ...ownerBookingAccess,
    target: {
      relationTo: "booking-instruments",
      value: {
        id: 127,
        name: "Microplate reader",
        deleted: false,
        parentContainerName: "Screening lab",
        parentContainerGlobalId: "IC459",
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
    start: "2026-08-17T08:00:00Z",
    end: "2026-08-17T10:00:00Z",
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

const detailBookingEvents = sampleBookingEvents.map((booking) => ({
  ...booking,
  canEdit: booking.privacy === "full",
  createdAt: "2026-08-01T09:00:00Z",
  updatedAt: "2026-08-01T09:00:00Z",
}));

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
      const targetId = where.match(/^target==([^;]+)$/)?.[1].replaceAll('"', "");
      const docs = where.includes("id==-1")
        ? []
        : targetId
          ? bookableItemFixtures.filter((fixture) => fixture.target.globalId === targetId)
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

export function bookableItemDetailsHandlers(): RequestHandler[] {
  let accessVersion = 3;
  let accessAssignments = [
    {
      grantee: {
        kind: "USER",
        id: 1,
        key: "user:1",
        name: "Ada Lovelace",
        detail: "ada",
        available: true,
        effectiveRole: "OWNER",
        roleSources: [],
      },
      role: "OWNER",
    },
    {
      grantee: {
        kind: "AUDIENCE",
        id: "ALL_USERS",
        key: "audience:all-users",
        name: "All users",
        detail: null,
        available: true,
        effectiveRole: null,
        roleSources: [],
      },
      role: "BOOKER",
    },
  ];
  const accessDocument = () => ({
    scheme: "booking-configurations",
    version: accessVersion,
    assignments: accessAssignments,
    caller: {
      effectiveRole: "OWNER",
      roleSources: [],
      capabilities: { canManageAssignments: true, canManageOwners: true, canLeave: false },
    },
  });
  return [
    http.get("/api/v2/booking-configurations", ({ request }) => {
      const where = new URL(request.url).searchParams.get("where") ?? "";
      const targetId = where.match(/^target==([^;]+)$/)?.[1].replaceAll('"', "");
      if (!targetId) return undefined;
      return HttpResponse.json(
        collectionPage(bookableItemFixtures.filter((fixture) => fixture.target.globalId === targetId)),
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
    http.get("/api/v2/booking-configurations/7/access", () =>
      HttpResponse.json(accessDocument(), { headers: { ETag: `"${accessVersion}"` } }),
    ),
    http.get("/api/v2/booking-configurations/7/access/grantees", () =>
      HttpResponse.json([{ kind: "USER", id: 2, key: "user:2", name: "Grace Hopper", detail: "grace" }]),
    ),
    http.put("/api/v2/booking-configurations/7/access", async ({ request }) => {
      const body = (await request.json()) as {
        assignments: Array<{ granteeKey: string; role: string }>;
      };
      accessVersion += 1;
      accessAssignments = body.assignments.map(({ granteeKey, role }) => {
        if (granteeKey === "user:1") return { ...accessAssignments[0], role };
        if (granteeKey === "audience:all-users") return { ...accessAssignments[1], role };
        return {
          grantee: {
            kind: "USER",
            id: 2,
            key: "user:2",
            name: "Grace Hopper",
            detail: "grace",
            available: true,
            effectiveRole: role,
            roleSources: [],
          },
          role,
        };
      });
      return HttpResponse.json(accessDocument(), { headers: { ETag: `"${accessVersion}"` } });
    }),
  ];
}
