import * as v from "valibot";
import { SchedulingSettingsSchema } from "@/modules/booking/configuration/schedulingSettingsSchema";
import {
  getSearchBookingCatalogueQueryOptions,
  searchBookingCatalogue,
  searchBookingCatalogueLocations,
} from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import type { BookingCatalogueManagerItem } from "@/modules/common/api/v2/generated/models/bookingCatalogueManagerItem";
import type { BookingCatalogueManagerPage } from "@/modules/common/api/v2/generated/models/bookingCatalogueManagerPage";
import type { SearchBookingCatalogueParams } from "@/modules/common/api/v2/generated/models/searchBookingCatalogueParams";
import { requireBookingValue as required } from "./bookingMapping";

export function mapBookingCatalogueItem(input: BookingCatalogueManagerItem) {
  const policy = v.parse(SchedulingSettingsSchema, input);
  if (input.targetType !== "INSTRUMENT") throw new Error("Unsupported booking target type");
  const capabilities = required(input.capabilities, "capabilities");
  return {
    configurationId: required(input.configurationId, "configurationId"),
    configurationVersion: required(input.configurationVersion, "configurationVersion"),
    targetType: "INSTRUMENT" as const,
    targetId: required(input.targetId, "targetId"),
    globalId: required(input.globalId, "globalId"),
    name: required(input.name, "name"),
    timezone: required(input.timezone, "timezone"),
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
    },
    location:
      input.location == null
        ? null
        : {
            globalId: required(input.location.globalId, "location.globalId"),
            name: required(input.location.name, "location.name"),
          },
    ...policy,
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

type CatalogueSearch = Partial<Pick<SearchBookingCatalogueParams, "q" | "target" | "page" | "capability">> & {
  types?: readonly string[];
  locations?: readonly string[];
  pageSize?: number;
};
function parameters(search: CatalogueSearch) {
  return {
    capability: search.capability,
    q: search.q?.trim() || undefined,
    target: search.target,
    type: search.types ? [...search.types] : undefined,
    location: search.locations ? [...search.locations] : undefined,
    mine: false,
    page: search.page ?? 1,
    limit: search.pageSize ?? 20,
  };
}
export async function fetchBookingCatalogue(
  search: CatalogueSearch,
  signal?: AbortSignal,
): Promise<BookingCataloguePage> {
  return mapBookingCataloguePage(await searchBookingCatalogue(parameters(search), { signal }));
}
export function bookingCatalogueQuery(_scope: unknown, search: CatalogueSearch) {
  return getSearchBookingCatalogueQueryOptions<BookingCataloguePage>(parameters(search), {
    query: { select: mapBookingCataloguePage, staleTime: 30_000 },
  });
}
export async function fetchBookingCatalogueLocations(
  search: Pick<CatalogueSearch, "q" | "types" | "page" | "pageSize">,
  signal?: AbortSignal,
) {
  return searchBookingCatalogueLocations(parameters(search), { signal });
}

function mapCapability(value: unknown, fallback?: boolean): boolean {
  if (value === undefined && fallback !== undefined) return fallback;
  if (typeof value !== "boolean") throw new Error("Invalid booking capability");
  return value;
}
