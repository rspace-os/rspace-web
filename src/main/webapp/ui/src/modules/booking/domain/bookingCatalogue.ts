import * as v from "valibot";
import { SchedulingSettingsSchema } from "@/modules/booking/configuration/schedulingSettingsSchema";
import {
  getSearchBookingCatalogueQueryOptions,
  searchBookingCatalogue,
  searchBookingCatalogueLocations,
} from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import type { BookingCatalogueItemDocument } from "@/modules/common/api/v2/generated/models/bookingCatalogueItemDocument";
import type { BookingCataloguePageDocument } from "@/modules/common/api/v2/generated/models/bookingCataloguePageDocument";
import type { SearchBookingCatalogueParams } from "@/modules/common/api/v2/generated/models/searchBookingCatalogueParams";

export function mapBookingCatalogueItem(input: BookingCatalogueItemDocument) {
  const policy = v.parse(SchedulingSettingsSchema, input);
  if (input.targetType !== "INSTRUMENT") throw new Error("Unsupported booking target type");
  return {
    configurationId: input.configurationId,
    configurationVersion: input.configurationVersion,
    targetType: "INSTRUMENT" as const,
    targetId: input.targetId,
    globalId: input.globalId,
    name: input.name,
    timezone: input.timezone,
    effectiveRole: input.effectiveRole,
    capabilities: input.capabilities,
    location: input.location,
    ...policy,
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
export function bookingCatalogueQuery(search: CatalogueSearch) {
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
