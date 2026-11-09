import { skipToken } from "@tanstack/react-query";
import * as v from "valibot";
import { ApiV2ProblemError } from "@/modules/common/api/v2";
import {
  createBooking as generatedCreateBooking,
  updateBooking as generatedUpdateBooking,
  getBooking,
  getGetBookingQueryOptions,
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
type MappedInstrument = Omit<Partial<InstrumentsRead>, "id" | "name" | "deleted" | "parentContainer"> & {
  id: number;
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

function mapIdentity<TTarget>(input: BookingsRead, target: TTarget) {
  const identity = {
    id: input.id,
    version: required(input.version, "version"),
    target,
    timezone: required(input.timezone, "timezone"),
    start: required(input.start, "start"),
    end: required(input.end, "end"),
    state: required(input.state, "state"),
    kind: input.kind ?? "BOOKING",
  };
  checkBookingInterval(identity);
  return identity;
}

export function mapBookingSummary(input: BookingsRead) {
  const summary = {
    id: input.id,
    target: mapBookingTarget(input.target),
    timezone: required(input.timezone, "timezone"),
    start: required(input.start, "start"),
    end: required(input.end, "end"),
    state: required(input.state, "state"),
    kind: input.kind ?? "BOOKING",
  };
  checkBookingInterval(summary);
  return summary;
}

function mapPrivacy(input: BookingsRead) {
  const management: { createdAt: string; updatedAt: string; createdBy?: string | null } = {
    createdAt: required(input.createdAt, "createdAt"),
    updatedAt: required(input.updatedAt, "updatedAt"),
    createdBy: input.createdBy,
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
  if (input.privacy !== "full" || input.purpose === undefined || input.bookedBy === undefined)
    throw new BookingUnavailableError("Booking details are unavailable");
  return {
    ...management,
    privacy: "full" as const,
    purpose: input.purpose,
    bookedBy: input.bookedBy,
    canEdit: required(input.canEdit, "canEdit"),
    canCancel: required(input.canCancel, "canCancel"),
  };
}

export function mapBooking(input: BookingsRead) {
  return { ...mapIdentity(input, mapBookingTarget(input.target)), ...mapPrivacy(input) };
}
export function mapBookingDetails(input: BookingsRead) {
  const booking = mapBooking(input);
  if (booking.privacy !== "full") throw new BookingUnavailableError("Booking details are unavailable");
  return { ...booking, canViewConfiguration: required(input.canViewConfiguration, "canViewConfiguration") };
}
export function mapBookingListDocument(input: BookingsRead) {
  return {
    ...mapBooking(input),
    canViewConfiguration: required(input.canViewConfiguration, "canViewConfiguration"),
    requester: input.requester ?? null,
  };
}
export function mapBookingMutation(input: BookingsRead) {
  const target = required(input.target, "target");
  return {
    ...mapIdentity(input, typeof target.value === "number" ? target : mapBookingTarget(target)),
    ...mapPrivacy(input),
  };
}

export type Booking = ReturnType<typeof mapBooking>;
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

export const BOOKING_READ_FIELDS =
  "id,version,target,canViewConfiguration,timezone,start,end,state,kind,purpose,bookedBy,createdBy,privacy,canEdit,canCancel,createdAt,updatedAt";
const readParameters = {
  depth: 2,
  fields: {
    bookings: BOOKING_READ_FIELDS,
    instruments: "id,globalId,name,deleted,parentContainer",
    containers: "id,globalId,name",
  },
};

export async function fetchBooking(id: number, signal?: AbortSignal): Promise<BookingDetails> {
  return mapBookingDetails(await getBooking(id, readParameters, { signal }));
}
export function bookingQueryOptions(id: number | undefined) {
  return getGetBookingQueryOptions<BookingDetails>(id ?? 0, readParameters, {
    query: {
      queryFn: id === undefined ? skipToken : ({ signal }) => getBooking(id, readParameters, { signal }),
      enabled: id !== undefined,
      select: mapBookingDetails,
      retry: (failureCount, error) =>
        failureCount < 2 &&
        !(
          error instanceof BookingUnavailableError ||
          (error instanceof ApiV2ProblemError && error.status >= 400 && error.status < 500)
        ),
    },
  });
}
export async function createBooking(input: BookingsCreate): Promise<BookingMutation> {
  return mapBookingMutation(await generatedCreateBooking(input));
}
export async function updateBooking(id: number, version: number, input: BookingsUpdate): Promise<BookingMutation> {
  return mapBookingMutation(await generatedUpdateBooking(id, input, { "If-Match": `"${version}"` }));
}
export function cancelBooking(id: number, version: number): Promise<BookingMutation> {
  return updateBooking(id, version, { state: "CANCELLED" });
}
export function isBookingOverlapError(error: unknown): boolean {
  return error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.overlap";
}
