import * as v from "valibot";
import { SchedulingSettingsSchema } from "@/modules/booking/configuration/schedulingSettingsSchema";
import {
  countBookingCatalogueAvailability,
  getCountBookingCatalogueAvailabilityQueryOptions,
  getSearchBookingCatalogueQueryOptions,
  getSearchCalendarCatalogueQueryOptions,
  searchBookingCatalogue,
  searchBookingCatalogueLocations,
  searchCalendarCatalogue,
} from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import type { BookingCatalogueAvailabilityCounts } from "@/modules/common/api/v2/generated/models/bookingCatalogueAvailabilityCounts";
import type { BookingCatalogueItemDocument } from "@/modules/common/api/v2/generated/models/bookingCatalogueItemDocument";
import type { BookingCataloguePageDocument } from "@/modules/common/api/v2/generated/models/bookingCataloguePageDocument";
import type { SearchBookingCatalogueParams } from "@/modules/common/api/v2/generated/models/searchBookingCatalogueParams";
import { requireBookingValue as required } from "./bookingMapping";

export function mapBookingCatalogueItem(input: BookingCatalogueItemDocument) {
  if (input.targetType !== "INSTRUMENT") throw new Error("Unsupported booking target type");
  const settings = v.parse(SchedulingSettingsSchema, input);
  return {
    configurationId: input.configurationId,
    configurationVersion: input.configurationVersion,
    targetType: "INSTRUMENT" as const,
    targetId: input.targetId,
    globalId: input.globalId,
    name: input.name,
    timezone: input.timezone,
    ...settings,
    effectiveRole: input.effectiveRole,
    capabilities: input.capabilities,
    location: input.location,
  };
}
export type BookingCatalogueItem = ReturnType<typeof mapBookingCatalogueItem>;
export function mapBookingCataloguePage(input: BookingCataloguePageDocument) {
  return {
    items: input.items.map(mapBookingCatalogueItem),
    page: input.page,
    pageSize: input.pageSize,
    total: input.total,
    facets: { types: input.facets.types },
  };
}
export type BookingCataloguePage = ReturnType<typeof mapBookingCataloguePage>;

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

type CatalogueSearch = Partial<
  Pick<SearchBookingCatalogueParams, "q" | "where" | "target" | "capability" | "page" | "mine" | "start" | "end">
> & {
  eventWhere?: string;
  types?: readonly string[];
  locations?: readonly string[];
  availability?: CatalogueAvailability;
  availabilityWindow?: CatalogueAvailabilityWindow;
  pageSize?: number;
  totals?: boolean;
};
export type CatalogueAvailability = "available-now" | "free-later-today";
export type CatalogueAvailabilityWindow = Required<Pick<SearchBookingCatalogueParams, "start" | "end" | "now">>;
function itemParameters(search: CatalogueSearch) {
  return {
    q: search.q?.trim() || undefined,
    where: search.where,
    target: search.target,
    capability: search.capability,
    mine: search.mine ?? false,
    type: search.types ? [...search.types] : undefined,
    location: search.locations ? [...search.locations] : undefined,
  };
}
function catalogueParameters(search: CatalogueSearch) {
  return {
    ...itemParameters(search),
    page: search.page ?? 1,
    limit: search.pageSize ?? 20,
    availability: search.availabilityWindow ? search.availability : undefined,
    start: search.start ?? (search.availability ? search.availabilityWindow?.start : undefined),
    end: search.end ?? (search.availability ? search.availabilityWindow?.end : undefined),
    now: search.availability ? search.availabilityWindow?.now : undefined,
  };
}
function calendarParameters(search: CatalogueSearch) {
  return {
    start: required(search.start, "start"),
    end: required(search.end, "end"),
    eventWhere: search.eventWhere,
    where: search.where,
    q: search.q?.trim() || undefined,
    mine: search.mine ?? false,
    page: search.page ?? 1,
    limit: search.pageSize ?? 20,
  };
}
export async function fetchBookingCatalogue(
  search: CatalogueSearch,
  signal?: AbortSignal,
): Promise<BookingCataloguePage> {
  return mapBookingCataloguePage(
    await (search.start
      ? searchCalendarCatalogue(calendarParameters(search), { signal })
      : searchBookingCatalogue(catalogueParameters(search), { signal })),
  );
}
export async function fetchBookingCatalogueAvailabilityCounts(
  search: Pick<CatalogueSearch, "q" | "where" | "target" | "mine" | "types" | "locations">,
  window: CatalogueAvailabilityWindow,
  signal?: AbortSignal,
): Promise<BookingCatalogueAvailabilityCounts> {
  return mapAvailabilityCounts(
    await countBookingCatalogueAvailability(
      { ...itemParameters(search), start: window.start, end: window.end, now: window.now },
      { signal },
    ),
  );
}
export async function fetchBookingCatalogueLocations(
  search: Pick<CatalogueSearch, "q" | "types" | "page" | "pageSize"> & { globalIds?: readonly string[] },
  signal?: AbortSignal,
) {
  const page = await searchBookingCatalogueLocations(
    {
      q: search.q?.trim() || undefined,
      type: search.types ? [...search.types] : undefined,
      globalId: search.globalIds ? [...search.globalIds] : undefined,
      page: search.page ?? 1,
      limit: search.pageSize ?? 20,
    },
    { signal },
  );
  return {
    ...page,
    items: required(page.items, "items").map((location) => ({
      ...location,
      name: required(location.name, "location.name"),
      globalId: required(location.globalId, "location.globalId"),
    })),
    page: required(page.page, "page"),
    pageSize: required(page.pageSize, "pageSize"),
    total: required(page.total, "total"),
  };
}
export function bookingCatalogueQuery(_scope: unknown, search: CatalogueSearch) {
  return getSearchBookingCatalogueQueryOptions<BookingCataloguePage>(catalogueParameters(search), {
    query: { select: mapBookingCataloguePage, staleTime: 30_000 },
  });
}
export function bookingCatalogueAvailabilityCountsQuery(
  _scope: unknown,
  search: Parameters<typeof fetchBookingCatalogueAvailabilityCounts>[0],
  today: CatalogueAvailabilityWindow,
) {
  return getCountBookingCatalogueAvailabilityQueryOptions<BookingCatalogueAvailabilityCounts>(
    { ...itemParameters(search), start: today.start, end: today.end, now: today.now },
    { query: { select: mapAvailabilityCounts, staleTime: 30_000 } },
  );
}
export function calendarBookingCatalogueQuery(_scope: unknown, _parameters: unknown, search: CatalogueSearch) {
  return getSearchCalendarCatalogueQueryOptions<BookingCataloguePage>(calendarParameters(search), {
    query: { select: mapBookingCataloguePage },
  });
}

function mapAvailabilityCounts(response: BookingCatalogueAvailabilityCounts) {
  return {
    availableNow: response.availableNow,
    freeLaterToday: response.freeLaterToday,
  };
}
