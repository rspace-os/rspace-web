import { queryOptions, skipToken } from "@tanstack/react-query";
import * as v from "valibot";
import { schedulingSettingsEntries } from "@/modules/booking/configuration/schedulingSettings";
import { bookingCatalogueKeys, bookingConfigurationKeys, bookingKeys } from "@/modules/booking/domain/bookingKeys";
import { apiV2Headers } from "@/modules/common/api/apiV2Headers";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import type { BookableItemOption } from "../creation/bookableItemOption";
import type { BookingEventKind } from "./booking";

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

const BookingCatalogueLocationPageSchema = v.object({
  items: v.array(BookingCatalogueLocationSchema),
  page: v.number(),
  pageSize: v.number(),
  total: v.number(),
});

export type BookingCatalogueItem = v.InferOutput<typeof BookingCatalogueItemSchema>;
type BookingCataloguePage = v.InferOutput<typeof BookingCataloguePageSchema>;

export function catalogueItemAsConfiguration(item: BookingCatalogueItem) {
  return {
    id: item.configurationId,
    configurationVersion: item.configurationVersion,
    target: {
      relationTo: "instruments" as const,
      location: item.location,
      value: {
        id: item.targetId,
        name: item.name,
        deleted: false,
      },
      globalId: item.globalId,
    },
    enabled: true,
    state: "ACTIVE" as const,
    timezone: item.timezone,
    slotGranularityMinutes: item.slotGranularityMinutes,
    incrementUnit: item.incrementUnit,
    openingHours: item.openingHours,
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
  start?: string;
  end?: string;
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
  totals?: boolean;
};

export type CatalogueAvailability = "available-now" | "free-later-today";

/** Today's availability window `[start, end)` in the display time zone, and the instant to classify. */
export type CatalogueAvailabilityWindow = { start: string; end: string; now: string };

const BookingCatalogueAvailabilityCountsSchema = v.object({
  availableNow: v.number(),
  freeLaterToday: v.number(),
});

type BookingCatalogueAvailabilityCounts = v.InferOutput<typeof BookingCatalogueAvailabilityCountsSchema>;

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
  parameters.set("start", window.start);
  parameters.set("end", window.end);
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
  if (search.totals) parameters.set("totals", "true");
  appendItemFilters(parameters, search);
  if (search.availability && search.availabilityWindow) {
    parameters.set("availability", search.availability);
    appendAvailabilityWindow(parameters, search.availabilityWindow);
  }
  if (search.start) parameters.set("start", search.start);
  if (search.end) parameters.set("end", search.end);
  if (search.eventWhere) parameters.set("eventWhere", search.eventWhere);
  const endpoint = search.start ? "/api/v2/booking-catalogue/calendar" : "/api/v2/booking-catalogue";
  const response = await fetchWithUiToken(`${endpoint}?${parameters}`, {
    headers: apiV2Headers(token),
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
  const response = await fetchWithUiToken(`/api/v2/booking-catalogue/availability-counts?${parameters}`, {
    headers: apiV2Headers(token),
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
  const response = await fetchWithUiToken(`/api/v2/booking-catalogue/locations?${parameters}`, {
    headers: apiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking catalogue location request failed (${response.status})`);
  return parseOrThrow(BookingCatalogueLocationPageSchema, await response.json());
}

export async function loadBookableItems(
  search: { term?: string; target?: string; page?: number; eventKind?: BookingEventKind },
  token: string,
  signal: AbortSignal,
): Promise<{ options: readonly BookableItemOption[]; totalPages: number }> {
  const result = await fetchBookingCatalogue(
    {
      q: search.term,
      target: search.target,
      capability: search.eventKind === "MAINTENANCE" ? "CREATE_BLOCKOUT" : "CREATE_BOOKING",
      page: search.page,
      pageSize: 20,
    },
    token,
    signal,
  );
  return {
    options: result.items
      .filter((item) =>
        search.eventKind === "MAINTENANCE" ? item.capabilities.canCreateBlockout : item.capabilities.canCreateBooking,
      )
      .map((item) => ({
        configurationId: item.configurationId,
        targetId: item.targetId,
        globalId: item.globalId,
        name: item.name,
        timezone: item.timezone,
        slotGranularityMinutes: item.slotGranularityMinutes,
        incrementUnit: item.incrementUnit,
        openingHours: item.openingHours,
        bufferBeforeMinutes: item.bufferBeforeMinutes,
        bufferAfterMinutes: item.bufferAfterMinutes,
        maxBookingDurationMinutes: item.maxBookingDurationMinutes,
        allowDoubleBooking: item.allowDoubleBooking,
        capabilities: item.capabilities,
      })),
    totalPages: Math.max(1, Math.ceil(result.total / result.pageSize)),
  };
}

export const enabledBookingPickerTargetQuery = (globalId: string | undefined, token: string) =>
  queryOptions({
    queryKey: [...bookingConfigurationKeys.all, "enabled-picker", "target", globalId] as const,
    queryFn: !globalId ? skipToken : ({ signal }) => loadBookableItems({ target: globalId }, token, signal),
    retry: false,
  });

export const bookingCatalogueQuery = (
  scope: unknown,
  search: Parameters<typeof fetchBookingCatalogue>[0],
  token: string,
) =>
  queryOptions({
    queryKey: [
      ...bookingCatalogueKeys.allItemsPrefix(scope),
      search.q,
      search.types,
      search.mine,
      search.where,
      search.page,
      search.pageSize,
      ...(search.availability
        ? [
            search.availability,
            search.availabilityWindow?.start,
            search.availabilityWindow?.end,
            search.availabilityWindow?.now,
          ]
        : []),
    ] as const,
    queryFn: ({ signal }) => fetchBookingCatalogue(search, token, signal),
    staleTime: 30_000,
  });

export const bookingCatalogueAvailabilityCountsQuery = (
  scope: unknown,
  search: Parameters<typeof fetchBookingCatalogueAvailabilityCounts>[0],
  today: Parameters<typeof fetchBookingCatalogueAvailabilityCounts>[1],
  token: string,
) =>
  queryOptions({
    queryKey: [
      ...bookingCatalogueKeys.availabilityCountsPrefix(scope),
      search.q,
      search.types,
      search.mine,
      search.where,
      today.start,
      today.end,
      today.now,
    ] as const,
    queryFn: ({ signal }) => fetchBookingCatalogueAvailabilityCounts(search, today, token, signal),
    staleTime: 30_000,
  });

export const calendarBookingCatalogueQuery = (
  scope: unknown,
  parameters: unknown,
  search: Parameters<typeof fetchBookingCatalogue>[0],
  token: string,
) => {
  const queryFn = async ({ signal }: { signal: AbortSignal }) => {
    const result = await fetchBookingCatalogue(search, token, signal);
    return { rows: result.items.map(catalogueItemAsConfiguration), rowCount: result.total };
  };
  return {
    ...queryOptions({ queryKey: [...bookingKeys.catalogue(), "calendar", scope, parameters] as const, queryFn }),
    queryFn,
  };
};

export const bookableItemsPickerQuery = (term: string, page: number, eventKind: BookingEventKind, token: string) =>
  queryOptions({
    queryKey: [...bookingCatalogueKeys.pickers(), term.trim(), page, eventKind] as const,
    queryFn: ({ signal }) => loadBookableItems({ term, page, eventKind }, token, signal),
  });
