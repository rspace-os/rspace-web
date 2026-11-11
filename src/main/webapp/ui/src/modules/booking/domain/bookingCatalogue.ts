import { skipToken } from "@tanstack/react-query";
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
import type { BookingCatalogueManagerAvailabilityCounts } from "@/modules/common/api/v2/generated/models/bookingCatalogueManagerAvailabilityCounts";
import type { BookingCatalogueManagerItem } from "@/modules/common/api/v2/generated/models/bookingCatalogueManagerItem";
import type { BookingCatalogueManagerPage } from "@/modules/common/api/v2/generated/models/bookingCatalogueManagerPage";
import type { BookingsReadKind } from "@/modules/common/api/v2/generated/models/bookingsReadKind";
import type { SearchBookingCatalogueParams } from "@/modules/common/api/v2/generated/models/searchBookingCatalogueParams";
import type { BookableItemOption } from "../creation/bookableItemOption";
import { requireBookingValue as required } from "./bookingMapping";

function mapCapability(value: unknown, fallback?: boolean): boolean {
  if (value === undefined && fallback !== undefined) return fallback;
  if (typeof value !== "boolean") throw new Error("Invalid booking capability");
  return value;
}

export function mapBookingCatalogueItem(input: BookingCatalogueManagerItem) {
  if (input.targetType !== "INSTRUMENT") throw new Error("Unsupported booking target type");
  const settings = v.parse(SchedulingSettingsSchema, input);
  const capabilities = required(input.capabilities, "capabilities");
  return {
    configurationId: required(input.configurationId, "configurationId"),
    configurationVersion: required(input.configurationVersion, "configurationVersion"),
    targetType: "INSTRUMENT" as const,
    targetId: required(input.targetId, "targetId"),
    globalId: required(input.globalId, "globalId"),
    name: required(input.name, "name"),
    timezone: required(input.timezone, "timezone"),
    ...settings,
    effectiveRole: input.effectiveRole ?? null,
    capabilities: {
      canEditConfiguration: mapCapability(capabilities.canEditConfiguration),
      canViewAudit: mapCapability(capabilities.canViewAudit),
      canViewAccess: mapCapability(capabilities.canViewAccess),
      canManageAssignments: mapCapability(capabilities.canManageAssignments),
      canManageOwners: mapCapability(capabilities.canManageOwners),
      canCreateBooking: mapCapability(capabilities.canCreateBooking),
      canManageOwnBookings: mapCapability(capabilities.canManageOwnBookings),
      canManageAllEvents: mapCapability(capabilities.canManageAllEvents),
      canCreateBlockout: mapCapability(capabilities.canCreateBlockout),
      canSubscribeCalendar: mapCapability(capabilities.canSubscribeCalendar),
      canLeaveConfiguration: mapCapability(capabilities.canLeaveConfiguration),
      canManageNotificationSubscription: mapCapability(capabilities.canManageNotificationSubscription, false),
    },
    location:
      input.location == null
        ? null
        : {
            globalId: required(input.location.globalId, "location.globalId"),
            name: required(input.location.name, "location.name"),
          },
  };
}
export type BookingCatalogueItem = ReturnType<typeof mapBookingCatalogueItem>;
export function mapBookingCataloguePage(input: BookingCatalogueManagerPage) {
  return {
    items: required(input.items, "items").map(mapBookingCatalogueItem),
    page: required(input.page, "page"),
    pageSize: required(input.pageSize, "pageSize"),
    total: required(input.total, "total"),
    facets: { types: required(input.facets?.types, "facets.types") },
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
): Promise<BookingCatalogueManagerAvailabilityCounts> {
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
  return getCountBookingCatalogueAvailabilityQueryOptions<BookingCatalogueManagerAvailabilityCounts>(
    { ...itemParameters(search), start: today.start, end: today.end, now: today.now },
    { query: { select: mapAvailabilityCounts, staleTime: 30_000 } },
  );
}
export function calendarBookingCatalogueQuery(_scope: unknown, _parameters: unknown, search: CatalogueSearch) {
  return getSearchCalendarCatalogueQueryOptions<BookingCataloguePage>(calendarParameters(search), {
    query: { select: mapBookingCataloguePage },
  });
}

function mapAvailabilityCounts(response: BookingCatalogueManagerAvailabilityCounts) {
  return {
    availableNow: required(response.availableNow, "availableNow"),
    freeLaterToday: required(response.freeLaterToday, "freeLaterToday"),
  };
}

type BookableItemSearch = { term?: string; target?: string; page?: number; eventKind?: BookingsReadKind };
function pickerParameters(search: BookableItemSearch) {
  return catalogueParameters({
    q: search.term,
    target: search.target,
    capability: search.eventKind === "MAINTENANCE" ? "CREATE_BLOCKOUT" : "CREATE_BOOKING",
    page: search.page,
    pageSize: 20,
  });
}
function mapBookableItems(
  page: BookingCatalogueManagerPage,
  search: BookableItemSearch,
): { options: readonly BookableItemOption[]; totalPages: number } {
  const result = mapBookingCataloguePage(page);
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
export async function loadBookableItems(
  search: BookableItemSearch,
  signal: AbortSignal,
): Promise<{ options: readonly BookableItemOption[]; totalPages: number }> {
  return mapBookableItems(await searchBookingCatalogue(pickerParameters(search), { signal }), search);
}
export function enabledBookingPickerTargetQuery(globalId: string | undefined) {
  const search = { target: globalId };
  return getSearchBookingCatalogueQueryOptions(pickerParameters(search), {
    query: {
      ...(!globalId ? { queryFn: skipToken } : {}),
      enabled: Boolean(globalId),
      select: (page) => mapBookableItems(page, search),
      retry: false,
    },
  });
}
export function bookableItemsPickerQuery(term: string, page: number, eventKind: BookingsReadKind) {
  const search = { term, page, eventKind };
  return getSearchBookingCatalogueQueryOptions(pickerParameters(search), {
    query: { select: (result) => mapBookableItems(result, search) },
  });
}
