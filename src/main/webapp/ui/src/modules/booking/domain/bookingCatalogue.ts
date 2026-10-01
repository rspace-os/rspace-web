import * as v from "valibot";
import { schedulingSettingsEntries } from "@/modules/booking/configuration/schedulingSettings";
import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

export const BookingCatalogueLocationSchema = v.object({
  globalId: v.string(),
  name: v.string(),
});

const BookingCapabilitiesSchema = v.object({
  canEditConfiguration: v.boolean(),
  canViewAudit: v.boolean(),
  canViewAccess: v.boolean(),
  canManageAssignments: v.boolean(),
  canManageOwners: v.boolean(),
  canCreateBooking: v.boolean(),
  canManageOwnBookings: v.boolean(),
  canManageAllEvents: v.boolean(),
  canCreateBlockout: v.boolean(),
  canSubscribeCalendar: v.boolean(),
  canLeaveConfiguration: v.boolean(),
  canManageNotificationSubscription: v.optional(v.boolean(), false),
});

export const BookingCatalogueItemSchema = v.object({
  configurationId: v.number(),
  configurationVersion: v.number(),
  targetType: v.literal("INSTRUMENT"),
  targetId: v.number(),
  globalId: v.string(),
  name: v.string(),
  timezone: v.string(),
  ...schedulingSettingsEntries,
  effectiveRole: v.nullable(v.string()),
  capabilities: BookingCapabilitiesSchema,
  location: v.nullable(BookingCatalogueLocationSchema),
});

export const BookingCataloguePageSchema = v.object({
  items: v.array(BookingCatalogueItemSchema),
  page: v.number(),
  pageSize: v.number(),
  total: v.number(),
  facets: v.object({ types: v.array(v.string()) }),
});

export const BookingCatalogueLocationPageSchema = v.object({
  items: v.array(BookingCatalogueLocationSchema),
  page: v.number(),
  pageSize: v.number(),
  total: v.number(),
});

export type BookingCatalogueItem = v.InferOutput<typeof BookingCatalogueItemSchema>;
export type BookingCataloguePage = v.InferOutput<typeof BookingCataloguePageSchema>;
export type BookingCatalogueLocation = v.InferOutput<typeof BookingCatalogueLocationSchema>;

export function catalogueItemAsConfiguration(item: BookingCatalogueItem) {
  return {
    id: item.configurationId,
    configurationVersion: item.configurationVersion,
    target: {
      relationTo: "booking-instruments" as const,
      value: {
        id: item.targetId,
        name: item.name,
        deleted: false,
        parentContainerName: item.location?.name ?? null,
        parentContainerGlobalId: item.location?.globalId ?? null,
      },
      globalId: item.globalId,
    },
    enabled: true,
    state: "ACTIVE" as const,
    timezone: item.timezone,
    slotGranularityMinutes: item.slotGranularityMinutes,
    openingStart: item.openingStart,
    openingEnd: item.openingEnd,
    openDays: item.openDays,
    openingExceptions: item.openingExceptions,
    bufferBeforeMinutes: item.bufferBeforeMinutes,
    bufferAfterMinutes: item.bufferAfterMinutes,
    maxBookingDurationMinutes: item.maxBookingDurationMinutes,
    allowDoubleBooking: item.allowDoubleBooking,
    effectiveRole: item.effectiveRole,
    roleSources: [],
    capabilities: item.capabilities,
  };
}

type CatalogueSearch = {
  q?: string;
  calendarStart?: string;
  calendarEnd?: string;
  eventWhere?: string;
  where?: string;
  target?: string;
  capability?: "CREATE_BOOKING" | "CREATE_BLOCKOUT";
  mine?: boolean;
  types?: readonly string[];
  locations?: readonly string[];
  /** Keeps only items in this availability quick filter; needs `availabilityWindow`. */
  availability?: CatalogueAvailability;
  availabilityWindow?: CatalogueAvailabilityWindow;
  page?: number;
  pageSize?: number;
};

export type CatalogueAvailability = "available-now" | "free-later-today";

/** Today's availability window `[start, end)` in the display time zone, and the instant to classify. */
export type CatalogueAvailabilityWindow = { start: string; end: string; now: string };

export const BookingCatalogueAvailabilityCountsSchema = v.object({
  availableNow: v.number(),
  freeLaterToday: v.number(),
});

export type BookingCatalogueAvailabilityCounts = v.InferOutput<typeof BookingCatalogueAvailabilityCountsSchema>;

function appendAll(parameters: URLSearchParams, name: string, values: readonly string[] | undefined) {
  for (const value of values ?? []) parameters.append(name, value);
}

function appendItemFilters(parameters: URLSearchParams, search: CatalogueSearch) {
  if (search.q?.trim()) parameters.set("q", search.q.trim());
  if (search.where) parameters.set("where", search.where);
  if (search.target) parameters.set("target", search.target);
  if (search.capability) parameters.set("capability", search.capability);
  if (search.mine) parameters.set("mine", "true");
  appendAll(parameters, "type", search.types);
  appendAll(parameters, "location", search.locations);
}

function appendAvailabilityWindow(parameters: URLSearchParams, window: CatalogueAvailabilityWindow) {
  parameters.set("availabilityStart", window.start);
  parameters.set("availabilityEnd", window.end);
  parameters.set("now", window.now);
}

export async function fetchBookingCatalogue(
  search: CatalogueSearch,
  token: string,
  signal?: AbortSignal,
): Promise<BookingCataloguePage> {
  const parameters = new URLSearchParams({
    page: String(search.page ?? 1),
    limit: String(search.pageSize ?? 20),
  });
  appendItemFilters(parameters, search);
  if (search.availability && search.availabilityWindow) {
    parameters.set("availability", search.availability);
    appendAvailabilityWindow(parameters, search.availabilityWindow);
  }
  if (search.calendarStart) parameters.set("calendarStart", search.calendarStart);
  if (search.calendarEnd) parameters.set("calendarEnd", search.calendarEnd);
  if (search.eventWhere) parameters.set("eventWhere", search.eventWhere);
  const endpoint = search.calendarStart ? "/api/v2/booking-catalogue/calendar" : "/api/v2/booking-catalogue";
  const response = await fetch(`${endpoint}?${parameters}`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking catalogue request failed (${response.status})`);
  return parseOrThrow(BookingCataloguePageSchema, await response.json());
}

/** How many items the same catalogue filters match in each availability quick filter, without rows. */
export async function fetchBookingCatalogueAvailabilityCounts(
  search: Pick<CatalogueSearch, "q" | "where" | "target" | "mine" | "types" | "locations">,
  window: CatalogueAvailabilityWindow,
  token: string,
  signal?: AbortSignal,
): Promise<BookingCatalogueAvailabilityCounts> {
  const parameters = new URLSearchParams();
  appendItemFilters(parameters, search);
  appendAvailabilityWindow(parameters, window);
  const response = await fetch(`/api/v2/booking-catalogue/availability-counts?${parameters}`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking availability counts request failed (${response.status})`);
  return parseOrThrow(BookingCatalogueAvailabilityCountsSchema, await response.json());
}

/**
 * Readable parent Containers and workbenches that hold an active bookable item. A `q` that is an
 * `IC` or `BE` global ID matches that location; `globalIds` restores saved selections in one batch.
 */
export async function fetchBookingCatalogueLocations(
  search: Pick<CatalogueSearch, "q" | "types" | "page" | "pageSize"> & { globalIds?: readonly string[] },
  token: string,
  signal?: AbortSignal,
) {
  const parameters = new URLSearchParams({
    page: String(search.page ?? 1),
    limit: String(search.pageSize ?? 20),
  });
  if (search.q?.trim()) parameters.set("q", search.q.trim());
  appendAll(parameters, "type", search.types);
  appendAll(parameters, "globalId", search.globalIds);
  const response = await fetch(`/api/v2/booking-catalogue/locations?${parameters}`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking catalogue location request failed (${response.status})`);
  return parseOrThrow(BookingCatalogueLocationPageSchema, await response.json());
}
