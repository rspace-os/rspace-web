import * as v from "valibot";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import { type ApiV2Selected, apiV2Fields, assertApiV2Selected } from "@/modules/common/api/v2/apiV2Selected";
import {
  createBooking as generatedCreateBooking,
  updateBooking as generatedUpdateBooking,
  getBooking,
} from "@/modules/common/api/v2/generated/bookings/bookings";
import type { BookingsCreate } from "@/modules/common/api/v2/generated/models/bookingsCreate";
import type { BookingsRead } from "@/modules/common/api/v2/generated/models/bookingsRead";
import type { BookingsUpdate } from "@/modules/common/api/v2/generated/models/bookingsUpdate";
import type { InstrumentsRead } from "@/modules/common/api/v2/generated/models/instrumentsRead";
import { BookingsReadSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { requireBookingValue as required } from "./bookingMapping";

export class BookingUnavailableError extends Error {}

export function checkBookingInterval(booking: { start: string; end: string }): void {
  if (
    !Number.isFinite(Date.parse(booking.start)) ||
    !Number.isFinite(Date.parse(booking.end)) ||
    Date.parse(booking.end) <= Date.parse(booking.start)
  )
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
  "canViewConfiguration",
  "timezone",
  "start",
  "end",
  "state",
  "kind",
  "purpose",
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

function mapIdentity<TTarget>(input: SelectedBooking, target: TTarget) {
  const identity = {
    id: input.id,
    version: input.version,
    target,
    timezone: required(input.timezone, "timezone"),
    start: input.start,
    end: input.end,
    state: input.state,
    kind: input.kind,
  };
  checkBookingInterval(identity);
  return identity;
}

export function mapBookingSummary(input: SelectedBooking) {
  const summary = {
    id: input.id,
    target: mapBookingTarget(input.target),
    timezone: required(input.timezone, "timezone"),
    start: input.start,
    end: input.end,
    state: input.state,
    kind: input.kind,
  };
  checkBookingInterval(summary);
  return summary;
}

function mapPrivacy(input: SelectedBooking) {
  const management: {
    createdAt: string;
    updatedAt: string;
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

export function mapBooking(input: SelectedBooking) {
  return { ...mapIdentity(input, mapBookingTarget(input.target)), ...mapPrivacy(input) };
}
export function mapBookingDetails(input: SelectedBooking) {
  const booking = mapBooking(input);
  if (booking.privacy !== "full") throw new BookingUnavailableError("Booking details are unavailable");
  return { ...booking, canViewConfiguration: input.canViewConfiguration };
}
export function mapBookingListDocument(input: SelectedBooking) {
  return {
    ...mapBooking(input),
    canViewConfiguration: input.canViewConfiguration,
    requester: input.requester ?? null,
  };
}
export function mapBookingMutation(input: SelectedBooking) {
  const target = required(input.target, "target");
  return {
    ...mapIdentity(input, typeof target.value === "number" ? target : mapBookingTarget(target)),
    ...mapPrivacy(input),
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
  documentSchema: BookingsReadSchema,
  validateRows: (rows: readonly unknown[]) => {
    for (const row of rows) {
      const interval = v.parse(v.required(v.pick(BookingsReadSchema, ["start", "end"])), row);
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

export async function fetchBooking(id: number, signal?: AbortSignal): Promise<BookingDetails> {
  const document = await getBooking(id, readParameters, { signal });
  assertApiV2Selected("bookings", document, BOOKING_READ_FIELDS);
  return mapBookingDetails(document);
}
/** Create and update responses return every readable field, so they carry the same guarantees as a full read. */
function selectedMutation(document: BookingsRead): BookingMutation {
  assertApiV2Selected("bookings", document, BOOKING_READ_FIELDS);
  return mapBookingMutation(document);
}
export async function createBooking(input: BookingsCreate): Promise<BookingMutation> {
  return selectedMutation(await generatedCreateBooking(input));
}
export async function updateBooking(id: number, version: number, input: BookingsUpdate): Promise<BookingMutation> {
  return selectedMutation(await generatedUpdateBooking(id, input, { "If-Match": `"${version}"` }));
}
export function cancelBooking(id: number, version: number): Promise<BookingMutation> {
  return updateBooking(id, version, { state: "CANCELLED" });
}
export function isBookingOverlapError(error: unknown): boolean {
  return error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.overlap";
}
