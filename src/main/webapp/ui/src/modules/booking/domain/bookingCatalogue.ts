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

export type BookingCatalogueSearch = {
  q?: string;
  calendarStart?: string;
  calendarEnd?: string;
  eventWhere?: string;
  where?: string;
  target?: string;
  capability?: "CREATE_BOOKING" | "CREATE_BLOCKOUT";
  types?: readonly string[];
  locations?: readonly string[];
  page?: number;
  pageSize?: number;
};

const CATALOGUE_PAGE_SIZE = 100;
const MAX_SAFE_REQUEST_BYTES = 6000;

function appendAll(parameters: URLSearchParams, name: string, values: readonly string[] | undefined) {
  for (const value of values ?? []) parameters.append(name, value);
}

function buildBookingCatalogueRequest(search: BookingCatalogueSearch, token: string) {
  const parameters = new URLSearchParams({
    page: String(search.page ?? 1),
    limit: String(search.pageSize ?? 20),
  });
  if (search.q?.trim()) parameters.set("q", search.q.trim());
  if (search.where) parameters.set("where", search.where);
  if (search.target) parameters.set("target", search.target);
  if (search.capability) parameters.set("capability", search.capability);
  appendAll(parameters, "type", search.types);
  appendAll(parameters, "location", search.locations);
  if (search.calendarStart) parameters.set("calendarStart", search.calendarStart);
  if (search.calendarEnd) parameters.set("calendarEnd", search.calendarEnd);
  if (search.eventWhere) parameters.set("eventWhere", search.eventWhere);
  const endpoint = search.calendarStart ? "/api/v2/booking-catalogue/calendar" : "/api/v2/booking-catalogue";
  return {
    url: `${endpoint}?${parameters.toString()}`,
    headers: bookingApiV2Headers(token),
  };
}

function serializedRequestBytes(url: string, headers: Headers): number {
  const encoder = new TextEncoder();
  let bytes = encoder.encode(`GET ${url} HTTP/1.1\r\n`).length + encoder.encode("\r\n").length;
  headers.forEach((value, name) => {
    bytes += encoder.encode(`${name}: ${value}\r\n`).length;
  });
  return bytes;
}

/** Leaves room below Jetty's 8 KiB limit for browser/proxy headers not visible to the application. */
export function bookingCatalogueRequestFits(search: BookingCatalogueSearch, token: string): boolean {
  const request = buildBookingCatalogueRequest(search, token);
  return serializedRequestBytes(request.url, request.headers) <= MAX_SAFE_REQUEST_BYTES;
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
  token: string,
  signal?: AbortSignal,
  allowRequestSizeRetry = false,
): Promise<BookingCataloguePage> {
  const request = buildBookingCatalogueRequest(search, token);
  const response = await fetch(request.url, {
    headers: request.headers,
    signal,
  });
  if (allowRequestSizeRetry && (response.status === 414 || response.status === 431)) {
    throw new BookingCatalogueRequestTooLargeError();
  }
  if (!response.ok) throw new Error(`Booking catalogue request failed (${response.status})`);
  return parseOrThrow(BookingCataloguePageSchema, await response.json());
}

export async function fetchBookingCatalogue(
  search: BookingCatalogueSearch,
  token: string,
  signal?: AbortSignal,
): Promise<BookingCataloguePage> {
  return requestBookingCatalogue(search, token, signal);
}

export async function fetchBookingCataloguePageWithinSafeBudget(
  search: BookingCatalogueSearch,
  token: string,
  signal: AbortSignal,
): Promise<BookingCataloguePage> {
  throwIfAborted(signal);
  if (!bookingCatalogueRequestFits(search, token)) throw new BookingCatalogueRequestTooLargeError();
  const result = await requestBookingCatalogue(search, token, signal, true);
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
  token: string,
  signal: AbortSignal,
  maximumItems: number,
): Promise<BookingCataloguePage> {
  if (!Number.isSafeInteger(maximumItems) || maximumItems < 0) {
    throw new Error("Booking catalogue page bound must be a non-negative integer");
  }
  const first = await fetchBookingCataloguePageWithinSafeBudget(
    { ...search, page: 1, pageSize: CATALOGUE_PAGE_SIZE },
    token,
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
      token,
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
