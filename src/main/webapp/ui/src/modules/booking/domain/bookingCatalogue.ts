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
  target?: string;
  types?: readonly string[];
  locations?: readonly string[];
  page?: number;
  pageSize?: number;
};

function appendAll(parameters: URLSearchParams, name: string, values: readonly string[] | undefined) {
  for (const value of values ?? []) parameters.append(name, value);
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
  if (search.q?.trim()) parameters.set("q", search.q.trim());
  if (search.target) parameters.set("target", search.target);
  appendAll(parameters, "type", search.types);
  appendAll(parameters, "location", search.locations);
  const response = await fetch(`/api/v2/booking-catalogue?${parameters}`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking catalogue request failed (${response.status})`);
  return parseOrThrow(BookingCataloguePageSchema, await response.json());
}

export async function fetchBookingCatalogueLocations(
  search: Pick<CatalogueSearch, "q" | "types" | "page" | "pageSize">,
  token: string,
  signal?: AbortSignal,
) {
  const parameters = new URLSearchParams({
    page: String(search.page ?? 1),
    limit: String(search.pageSize ?? 20),
  });
  if (search.q?.trim()) parameters.set("q", search.q.trim());
  appendAll(parameters, "type", search.types);
  const response = await fetch(`/api/v2/booking-catalogue/locations?${parameters}`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking catalogue location request failed (${response.status})`);
  return parseOrThrow(BookingCatalogueLocationPageSchema, await response.json());
}
