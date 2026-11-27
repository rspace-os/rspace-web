import { Temporal } from "@js-temporal/polyfill";
import { queryOptions, skipToken } from "@tanstack/react-query";
import * as v from "valibot";
import {
  ApiV2Error,
  ApiV2SessionEndedError,
  ApiV2UnexpectedResponseError,
} from "@/modules/common/api/v2/ApiV2ProblemError";
import { type ApiV2Selected, apiV2Fields, assertApiV2Selected } from "@/modules/common/api/v2/apiV2Selected";
import {
  createBooking as generatedCreateBooking,
  updateBooking as generatedUpdateBooking,
  getBooking,
  getGetBookingQueryKey,
} from "@/modules/common/api/v2/generated/bookings/bookings";
import type { BookingsCreate } from "@/modules/common/api/v2/generated/models/bookingsCreate";
import type { BookingsRead } from "@/modules/common/api/v2/generated/models/bookingsRead";
import type { BookingsUpdate } from "@/modules/common/api/v2/generated/models/bookingsUpdate";
import type { InstrumentsRead } from "@/modules/common/api/v2/generated/models/instrumentsRead";
import { requireBookingValue as required } from "./bookingMapping";

export class BookingUnavailableError extends Error {}

export function checkBookingInterval(booking: { start: Temporal.Instant; end: Temporal.Instant }): void {
  if (Temporal.Instant.compare(booking.end, booking.start) <= 0)
    throw new BookingUnavailableError("Booking end must follow start");
}

function mapParentContainer(input: InstrumentsRead["parentContainer"]) {
  if (input == null) return input;
  const globalId =
    "globalId" in input ? input.globalId : typeof input.value === "number" ? `IC${input.value}` : input.value.globalId;
  if (typeof globalId !== "string") throw new BookingUnavailableError("Booking parent container ID is unavailable");
  if (typeof input.value === "number") return { ...input, globalId, value: input.value };
  return {
    ...input,
    globalId,
    value: {
      ...input.value,
      id: input.value.id,
      globalId: required(input.value.globalId, "parentContainer.globalId"),
      name: required(input.value.name, "parentContainer.name"),
    },
  };
}
type MappedInstrument = Omit<InstrumentsRead, "name" | "deleted" | "parentContainer"> & {
  name: string;
  deleted: boolean;
  parentContainer?: ReturnType<typeof mapParentContainer>;
};

export function mapBookingTarget(target: BookingsRead["target"]) {
  const reference = required(target, "target");
  if (typeof reference.value === "number") throw new BookingUnavailableError("Booking target is not expanded");
  const item = reference.value;
  const value: MappedInstrument = {
    ...item,
    id: item.id,
    name: required(item.name, "target.name"),
    deleted: required(item.deleted, "target.deleted"),
    parentContainer: mapParentContainer(item.parentContainer),
  };
  return { ...reference, relationTo: "instruments" as const, globalId: reference.globalId, value };
}

export const BOOKING_READ_FIELDS = [
  "id",
  "version",
  "target",
  "location",
  "canViewConfiguration",
  "timezone",
  "start",
  "end",
  "state",
  "kind",
  "purpose",
  "cancellationReason",
  "bookedBy",
  "createdBy",
  "privacy",
  "canEdit",
  "canCancel",
  "createdAt",
  "updatedAt",
] as const;
/** A booking read with `BOOKING_READ_FIELDS`, or a mutation response, after `assertApiV2Selected`. */
export type SelectedBooking = ApiV2Selected<"bookings", typeof BOOKING_READ_FIELDS>;
/**
 * The guaranteed fields every booking mapper reads. `kind` and `createdBy` stay optional, because the My Bookings table
 * selects neither; a booking without `kind` is an ordinary booking.
 */
export const BOOKING_MAPPED_FIELDS = [
  "id",
  "version",
  "target",
  "canViewConfiguration",
  "timezone",
  "start",
  "end",
  "state",
  "privacy",
  "purpose",
  "bookedBy",
  "canEdit",
  "canCancel",
  "createdAt",
  "updatedAt",
] as const;
type MappedBooking = ApiV2Selected<"bookings", typeof BOOKING_MAPPED_FIELDS>;

function mapBookingLocation(location: BookingsRead["location"]) {
  if (location == null) return location;
  if (typeof location.value === "number") throw new BookingUnavailableError("Booking location is not expanded");
  return {
    ...location,
    value: {
      ...location.value,
      id: location.value.id,
      globalId: required(location.value.globalId, "location.globalId"),
      name: required(location.value.name, "location.name"),
    },
  };
}
function mapMutationLocation(location: BookingsRead["location"]) {
  return location == null || typeof location.value === "number" ? location : mapBookingLocation(location);
}

function mapIdentity<TTarget, TLocation>(input: MappedBooking, target: TTarget, location: TLocation) {
  const identity = {
    id: input.id,
    version: input.version,
    target,
    ...(location === undefined ? {} : { location }),
    timezone: input.timezone,
    start: input.start,
    end: input.end,
    state: input.state,
    kind: input.kind ?? "BOOKING",
  };
  checkBookingInterval(identity);
  return identity;
}

export function mapBookingSummary(input: MappedBooking) {
  const summary = {
    id: input.id,
    target: input.target === null ? null : mapBookingTarget(input.target),
    ...(input.location === undefined ? {} : { location: mapBookingLocation(input.location) }),
    timezone: input.timezone,
    start: input.start,
    end: input.end,
    state: input.state,
    kind: input.kind ?? "BOOKING",
  };
  checkBookingInterval(summary);
  return summary;
}

function mapListPrivacy(input: MappedBooking) {
  const management: {
    createdAt: Temporal.Instant;
    updatedAt: Temporal.Instant;
    createdBy?: string | null;
    canViewConfiguration?: boolean;
  } = {
    createdAt: input.createdAt,
    updatedAt: input.updatedAt,
    createdBy: input.createdBy,
    canViewConfiguration: input.canViewConfiguration,
  };
  if (input.privacy === "busy") {
    if (input.purpose !== null || input.bookedBy !== null || input.canEdit !== false || input.canCancel !== false)
      throw new BookingUnavailableError("Invalid busy booking");
    return {
      ...management,
      privacy: "busy" as const,
      purpose: null,
      bookedBy: null,
      canEdit: false as const,
      canCancel: false as const,
    };
  }
  if (input.privacy !== "full") throw new BookingUnavailableError("Booking details are unavailable");
  return {
    ...management,
    privacy: "full" as const,
    purpose: input.purpose,
    bookedBy: input.bookedBy,
    canEdit: input.canEdit,
    canCancel: input.canCancel,
  };
}

function mapFullPrivacy(input: MappedBooking) {
  const privacy = mapListPrivacy(input);
  if (input.cancellationReason === undefined || (privacy.privacy === "busy" && input.cancellationReason !== null))
    throw new BookingUnavailableError("Booking cancellation reason is unavailable");
  return { ...privacy, cancellationReason: input.cancellationReason };
}

export function mapBooking(input: MappedBooking) {
  return {
    ...mapIdentity(input, mapBookingTarget(input.target), mapBookingLocation(input.location)),
    ...mapFullPrivacy(input),
  };
}
function mapReadIdentity(input: MappedBooking) {
  const identity = {
    id: input.id,
    version: input.version,
    target: input.target === null ? null : mapBookingTarget(input.target),
    ...(input.location === undefined ? {} : { location: mapBookingLocation(input.location) }),
    timezone: input.timezone,
    start: input.start,
    end: input.end,
    state: input.state,
    kind: input.kind ?? "BOOKING",
  };
  checkBookingInterval(identity);
  return identity;
}
export function mapBookingDetails(input: MappedBooking) {
  try {
    const privacy = mapFullPrivacy(input);
    if (privacy.privacy !== "full") throw new BookingUnavailableError("Booking details are unavailable");
    return {
      ...mapReadIdentity(input),
      ...privacy,
      canViewConfiguration: input.canViewConfiguration,
    };
  } catch (error) {
    if (error instanceof BookingUnavailableError) throw error;
    throw new BookingUnavailableError("Booking details are unavailable", { cause: error });
  }
}
export function mapBookingListDocument(input: MappedBooking) {
  if (input.requester === undefined) throw new BookingUnavailableError("Booking requester is unavailable");
  const { purpose, bookedBy, privacy, canEdit, canCancel, ...metadata } = mapListPrivacy(input);
  return {
    ...mapReadIdentity(input),
    ...metadata,
    purpose,
    bookedBy,
    privacy,
    canEdit,
    canCancel,
    canViewConfiguration: input.canViewConfiguration,
    requester: input.requester,
  };
}
export function mapBookingMutation(input: MappedBooking) {
  const target = required(input.target, "target");
  return {
    ...mapIdentity(
      input,
      typeof target.value === "number" ? target : mapBookingTarget(target),
      mapMutationLocation(input.location),
    ),
    ...mapFullPrivacy(input),
  };
}

export type Booking = ReturnType<typeof mapBooking>;

/** Asserts each document of a `BOOKING_READ_FIELDS` list read at the fetch boundary, then maps it. */
export function mapSelectedBookings(documents: readonly BookingsRead[]): Booking[] {
  return documents.map((document) => {
    assertApiV2Selected("bookings", document, BOOKING_READ_FIELDS);
    return mapBooking(document);
  });
}
export type BookingSummary = ReturnType<typeof mapBookingSummary>;
export type BookingDetails = ReturnType<typeof mapBookingDetails>;
export type BookingListDocument = ReturnType<typeof mapBookingListDocument>;
export type BookingMutation = ReturnType<typeof mapBookingMutation>;

export const BookingListDocumentTableValidation = {
  validateRows: (rows: readonly unknown[]) => {
    for (const row of rows) {
      const interval = v.parse(
        v.object({ start: v.instance(Temporal.Instant), end: v.instance(Temporal.Instant) }),
        row,
      );
      checkBookingInterval(interval);
    }
  },
};

const readParameters = {
  depth: 2,
  fields: {
    bookings: apiV2Fields(BOOKING_READ_FIELDS),
    instruments: "id,globalId,name,deleted,parentContainer",
    containers: "id,globalId,name",
  },
};

/**
 * Reads a booking with `BOOKING_READ_FIELDS`, asserting its guaranteed fields at the fetch boundary. A body that fails
 * its schema or is not JSON means the details are unavailable, which is not retried; every other failure is rethrown.
 */
async function requestBookingDetails(id: number, signal?: AbortSignal): Promise<SelectedBooking> {
  try {
    const document = await getBooking(id, readParameters, { signal });
    assertApiV2Selected("bookings", document, BOOKING_READ_FIELDS);
    return document;
  } catch (error) {
    if (
      error instanceof ApiV2UnexpectedResponseError &&
      (error.reason === "invalidBody" || error.reason === "malformedJson")
    )
      throw new BookingUnavailableError("Booking details are unavailable", { cause: error });
    throw error;
  }
}
export async function fetchBooking(id: number, signal?: AbortSignal): Promise<BookingDetails> {
  return mapBookingDetails(await requestBookingDetails(id, signal));
}
/** Retries a booking read twice, but never a 4xx answer, an ended session or a booking the caller cannot see. */
export function retryBookingRead(failureCount: number, error: unknown): boolean {
  if (error instanceof BookingUnavailableError || error instanceof ApiV2SessionEndedError) return false;
  const status = error instanceof ApiV2Error ? error.status : undefined;
  return failureCount < 2 && !(status !== undefined && status >= 400 && status < 500);
}
/** Create and update responses return every readable field, so they carry the same guarantees as a full read. */
function selectedMutation(document: BookingsRead): BookingMutation {
  assertApiV2Selected("bookings", document, BOOKING_READ_FIELDS);
  return mapBookingMutation(document);
}
export function bookingQueryOptions(id: number | undefined) {
  return queryOptions({
    queryKey: getGetBookingQueryKey(id ?? 0, readParameters),
    queryFn: id === undefined ? skipToken : ({ signal }) => requestBookingDetails(id, signal),
    enabled: id !== undefined,
    select: mapBookingDetails,
    retry: retryBookingRead,
  });
}
export async function createBooking(input: BookingsCreate, idempotencyKey?: string): Promise<BookingMutation> {
  return selectedMutation(
    await generatedCreateBooking(input, idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined),
  );
}
export async function updateBooking(id: number, version: number, input: BookingsUpdate): Promise<BookingMutation> {
  return selectedMutation(await generatedUpdateBooking(id, input, { "If-Match": `"${version}"` }));
}
export function cancelBooking(id: number, version: number, reason?: string): Promise<BookingMutation> {
  const cancellationReason = reason?.trim();
  return updateBooking(
    id,
    version,
    cancellationReason ? { state: "CANCELLED", cancellationReason } : { state: "CANCELLED" },
  );
}

/** Returns a cancelled booking to its slot, if it has not started and the slot is still free. */
export function restoreBooking(id: number, version: number): Promise<BookingMutation> {
  return updateBooking(id, version, { state: "CONFIRMED" });
}
