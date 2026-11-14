import * as v from "valibot";
import { SchedulingSettingsSchema } from "@/modules/booking/configuration/schedulingSettingsSchema";
import { ApiV2ProblemError } from "@/modules/common/api/v2";
import {
  getCountBookingCatalogueAvailabilityQueryOptions,
  getSearchBookingCatalogueQueryOptions,
  getSearchBookingCatalogueUrl,
  getSearchCalendarCatalogueQueryOptions,
  getSearchCalendarCatalogueUrl,
  searchBookingCatalogue,
  searchBookingCatalogueLocations,
  searchCalendarCatalogue,
} from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import type { BookingCatalogueManagerAvailabilityCounts } from "@/modules/common/api/v2/generated/models/bookingCatalogueManagerAvailabilityCounts";
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

export type BookingCatalogueSearch = Partial<
  Pick<SearchBookingCatalogueParams, "q" | "where" | "target" | "capability" | "page" | "mine">
> & {
  start?: string;
  end?: string;
  eventWhere?: string;
  types?: readonly string[];
  locations?: readonly string[];
  pageSize?: number;
};

const CATALOGUE_PAGE_SIZE = 100;
const MAX_SAFE_REQUEST_BYTES = 6000;

function parameters(search: BookingCatalogueSearch) {
  return {
    q: search.q?.trim() || undefined,
    where: search.where,
    target: search.target,
    capability: search.capability,
    type: search.types ? [...search.types] : undefined,
    location: search.locations ? [...search.locations] : undefined,
    mine: search.mine ?? false,
    page: search.page ?? 1,
    limit: search.pageSize ?? 20,
  };
}
function calendarParameters(search: BookingCatalogueSearch) {
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
function buildBookingCatalogueRequest(search: BookingCatalogueSearch) {
  return {
    url: search.start
      ? getSearchCalendarCatalogueUrl(calendarParameters(search))
      : getSearchBookingCatalogueUrl(parameters(search)),
  };
}
function serializedRequestBytes(url: string): number {
  const encoder = new TextEncoder();
  const bytes = encoder.encode(`GET ${url} HTTP/1.1\r\n`).length + encoder.encode("\r\n").length;
  return bytes;
}

/** Leaves room below Jetty's 8 KiB limit for browser/proxy headers not visible to the application. */
export function bookingCatalogueRequestFits(search: BookingCatalogueSearch): boolean {
  const request = buildBookingCatalogueRequest(search);
  return serializedRequestBytes(request.url) <= MAX_SAFE_REQUEST_BYTES;
}

export class BookingCatalogueRequestTooLargeError extends Error {
  constructor() {
    super("Booking catalogue request exceeds the safe request budget");
    this.name = "BookingCatalogueRequestTooLargeError";
  }
}

function throwIfAborted(signal: AbortSignal): void {
  if (signal.aborted) throw signal.reason ?? new DOMException("The operation was aborted", "AbortError");
}

async function requestBookingCatalogue(
  search: BookingCatalogueSearch,

  signal?: AbortSignal,
  allowRequestSizeRetry = false,
): Promise<BookingCataloguePage> {
  try {
    return mapBookingCataloguePage(
      await (search.start
        ? searchCalendarCatalogue(calendarParameters(search), { signal })
        : searchBookingCatalogue(parameters(search), { signal })),
    );
  } catch (error) {
    if (allowRequestSizeRetry && error instanceof ApiV2ProblemError && (error.status === 414 || error.status === 431))
      throw new BookingCatalogueRequestTooLargeError();
    throw error;
  }
}

export async function fetchBookingCatalogue(
  search: BookingCatalogueSearch,

  signal?: AbortSignal,
): Promise<BookingCataloguePage> {
  return requestBookingCatalogue(search, signal);
}

export async function fetchBookingCataloguePageWithinSafeBudget(
  search: BookingCatalogueSearch,

  signal: AbortSignal,
): Promise<BookingCataloguePage> {
  throwIfAborted(signal);
  if (!bookingCatalogueRequestFits(search)) throw new BookingCatalogueRequestTooLargeError();
  const result = await requestBookingCatalogue(search, signal, true);
  throwIfAborted(signal);
  return result;
}

export function assertBookingCataloguePage(
  page: BookingCataloguePage,
  requestedPage: number,
  expectedTotal?: number,
): void {
  if (
    !Number.isSafeInteger(page.page) ||
    page.page !== requestedPage ||
    page.pageSize !== CATALOGUE_PAGE_SIZE ||
    !Number.isSafeInteger(page.total) ||
    page.total < 0 ||
    (expectedTotal !== undefined && page.total !== expectedTotal)
  ) {
    throw new Error("Booking catalogue returned inconsistent pagination metadata");
  }
  const expectedItemCount = Math.min(
    CATALOGUE_PAGE_SIZE,
    Math.max(0, page.total - (requestedPage - 1) * CATALOGUE_PAGE_SIZE),
  );
  if (page.items.length !== expectedItemCount) {
    throw new Error("Booking catalogue pagination returned an incomplete page");
  }
}

export async function fetchAllBookingCataloguePages(
  search: BookingCatalogueSearch,

  signal: AbortSignal,
  maximumItems: number,
): Promise<BookingCataloguePage> {
  if (!Number.isSafeInteger(maximumItems) || maximumItems < 0) {
    throw new Error("Booking catalogue page bound must be a non-negative integer");
  }
  const first = await fetchBookingCataloguePageWithinSafeBudget(
    { ...search, page: 1, pageSize: CATALOGUE_PAGE_SIZE },

    signal,
  );
  assertBookingCataloguePage(first, 1);
  if (first.total > maximumItems) {
    throw new Error("Booking catalogue returned more rows than the scoped target set");
  }
  const items = [...first.items];
  const totalPages = Math.ceil(first.total / CATALOGUE_PAGE_SIZE);
  for (let page = 2; page <= totalPages; page += 1) {
    throwIfAborted(signal);
    const next = await fetchBookingCataloguePageWithinSafeBudget(
      { ...search, page, pageSize: CATALOGUE_PAGE_SIZE },

      signal,
    );
    assertBookingCataloguePage(next, page, first.total);
    items.push(...next.items);
  }
  if (items.length !== first.total) {
    throw new Error("Booking catalogue pagination returned an incomplete result");
  }
  return { ...first, items };
}

export async function fetchBookingCatalogueLocations(
  search: Pick<BookingCatalogueSearch, "q" | "types" | "page" | "pageSize">,

  signal?: AbortSignal,
) {
  return searchBookingCatalogueLocations(
    {
      q: search.q?.trim() || undefined,
      type: search.types ? [...search.types] : undefined,
      page: search.page ?? 1,
      limit: search.pageSize ?? 20,
    },
    { signal },
  );
}
export function bookingCatalogueQuery(_scope: unknown, search: BookingCatalogueSearch) {
  return getSearchBookingCatalogueQueryOptions<BookingCataloguePage>(parameters(search), {
    query: { select: mapBookingCataloguePage, staleTime: 30_000 },
  });
}

export function calendarBookingCatalogueQuery(_scope: unknown, _parameters: unknown, search: BookingCatalogueSearch) {
  return getSearchCalendarCatalogueQueryOptions<BookingCataloguePage>(calendarParameters(search), {
    query: { select: mapBookingCataloguePage },
  });
}

function mapCapability(value: unknown, fallback?: boolean): boolean {
  if (value === undefined && fallback !== undefined) return fallback;
  if (typeof value !== "boolean") throw new Error("Invalid booking capability");
  return value;
}

export type CatalogueAvailabilityWindow = { start: string; end: string; now?: string };
export function bookingCatalogueAvailabilityCountsQuery(
  _scope: unknown,
  search: { q?: string; where?: string },
  today: CatalogueAvailabilityWindow,
) {
  return getCountBookingCatalogueAvailabilityQueryOptions<BookingCatalogueManagerAvailabilityCounts>(
    {
      q: search.q?.trim() || undefined,
      where: search.where,
      mine: false,
      start: today.start,
      end: today.end,
      now: today.now,
    },
    { query: { select: mapAvailabilityCounts, staleTime: 30_000 } },
  );
}

function mapAvailabilityCounts(response: BookingCatalogueManagerAvailabilityCounts) {
  return {
    availableNow: required(response.availableNow, "availableNow"),
    freeLaterToday: required(response.freeLaterToday, "freeLaterToday"),
  };
}
