import * as v from "valibot";
import { apiV2Headers, apiV2JsonHeaders } from "@/modules/common/api/apiV2Headers";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

const BookingTimestampEntries = {
  start: v.pipe(v.string(), v.isoTimestamp()),
  end: v.pipe(v.string(), v.isoTimestamp()),
};

export const BookingEventKindSchema = v.picklist(["BOOKING", "MAINTENANCE"]);
export type BookingEventKind = v.InferOutput<typeof BookingEventKindSchema>;

function endsAfterStart(booking: { start: string; end: string }): boolean {
  return Date.parse(booking.end) > Date.parse(booking.start);
}

type BookingTimeFields = { start: string; end: string };
type EndPath<TOutput extends BookingTimeFields> = Parameters<
  typeof v.forward<TOutput, v.CheckIssue<TOutput>, ["end"]>
>[1];

function withEndAfterStart<
  TInput extends BookingTimeFields,
  TOutput extends BookingTimeFields,
  TIssue extends v.BaseIssue<unknown>,
>(schema: v.BaseSchema<TInput, TOutput, TIssue>, path: EndPath<TOutput>) {
  return v.pipe(
    schema,
    v.forward(
      v.check((booking: TOutput) => endsAfterStart(booking)),
      path,
    ),
  );
}

const BookingTargetSchema = v.object({
  relationTo: v.literal("booking-instruments"),
  value: v.object({
    id: v.number(),
    name: v.string(),
    deleted: v.boolean(),
    parentContainerName: v.optional(v.nullable(v.string())),
    parentContainerGlobalId: v.optional(v.nullable(v.string())),
  }),
  globalId: v.string(),
});

// Mutation responses are serialized directly from the saved entity. Unlike reads, they do not
// expand relationships, even when the request includes depth=1.
const MutationTargetSchema = v.object({
  relationTo: v.literal("booking-instruments"),
  value: v.union([v.number(), BookingTargetSchema.entries.value]),
  globalId: v.string(),
});

function bookingIdentity<TTarget extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>>>(target: TTarget) {
  return {
    id: v.number(),
    version: v.number(),
    target,
    timezone: v.string(),
    ...BookingTimestampEntries,
    state: v.picklist(["CONFIRMED", "CANCELLED"]),
    kind: v.optional(BookingEventKindSchema, "BOOKING"),
  };
}

const BookingAvailabilityIdentitySchema = {
  id: v.number(),
  target: BookingTargetSchema,
  timezone: v.string(),
  ...BookingTimestampEntries,
  state: v.picklist(["CONFIRMED", "CANCELLED"]),
  kind: v.optional(BookingEventKindSchema, "BOOKING"),
};
const BookingFullPrivacyEntries = {
  purpose: v.nullable(v.string()),
  bookedBy: v.nullable(v.string()),
  createdBy: v.optional(v.nullable(v.string())),
};

const BookingFullManagementEntries = {
  canEdit: v.boolean(),
  canCancel: v.boolean(),
  createdAt: v.string(),
  updatedAt: v.string(),
};

function bookingSchema<TTarget extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>>>(target: TTarget) {
  const identity = bookingIdentity(target);
  return v.variant("privacy", [
    v.object({
      ...identity,
      privacy: v.literal("full"),
      ...BookingFullPrivacyEntries,
      ...BookingFullManagementEntries,
    }),
    v.object({
      ...identity,
      privacy: v.literal("busy"),
      purpose: v.null(),
      bookedBy: v.null(),
      createdBy: v.optional(v.nullable(v.string())),
      canEdit: v.literal(false),
      canCancel: v.literal(false),
      createdAt: v.string(),
      updatedAt: v.string(),
    }),
  ]);
}

const BookingIdentitySchema = bookingIdentity(BookingTargetSchema);

export const BookingSummarySchema = withEndAfterStart(v.object(BookingAvailabilityIdentitySchema), ["end"]);

export const BookingSchema = withEndAfterStart(bookingSchema(BookingTargetSchema), ["end"]);

export const BookingDetailsSchema = withEndAfterStart(
  v.object({
    ...BookingIdentitySchema,
    canViewConfiguration: v.boolean(),
    privacy: v.literal("full"),
    ...BookingFullPrivacyEntries,
    ...BookingFullManagementEntries,
  }),
  ["end"],
);

const BookingMutationSchema = withEndAfterStart(bookingSchema(MutationTargetSchema), ["end"]);

const BookingListDocumentObjectSchema = v.object({
  ...BookingIdentitySchema,
  canViewConfiguration: v.boolean(),
  requesterId: v.number(),
  ...BookingFullPrivacyEntries,
  privacy: v.picklist(["full", "busy"]),
  ...BookingFullManagementEntries,
});

export const BookingListDocumentSchema = withEndAfterStart(BookingListDocumentObjectSchema, ["end"]);

const BookingIntervalSchema = withEndAfterStart(v.object(BookingTimestampEntries), ["end"]);

function validateBookingIntervals(rows: readonly unknown[]): void {
  parseOrThrow(v.array(BookingIntervalSchema), rows);
}

export const BookingListDocumentTableValidation = {
  documentSchema: BookingListDocumentObjectSchema,
  validateRows: validateBookingIntervals,
};

export type BookingSummary = v.InferOutput<typeof BookingSummarySchema>;
export type Booking = v.InferOutput<typeof BookingSchema>;
export type BookingDetails = v.InferOutput<typeof BookingDetailsSchema>;
export type BookingListDocument = v.InferOutput<typeof BookingListDocumentSchema>;
type BookingMutation = v.InferOutput<typeof BookingMutationSchema>;

export const BookingCreateSchema = v.object({
  target: v.object({ relationTo: v.literal("booking-instruments"), value: v.number() }),
  start: v.string(),
  end: v.string(),
  purpose: v.optional(v.nullable(v.pipe(v.string(), v.maxLength(1000)))),
  kind: v.optional(BookingEventKindSchema),
});

export const BookingUpdateSchema = v.partial(
  v.object({
    start: v.string(),
    end: v.string(),
    purpose: v.nullable(v.pipe(v.string(), v.maxLength(1000))),
    state: v.literal("CANCELLED"),
  }),
);

export type BookingCreate = v.InferOutput<typeof BookingCreateSchema>;
export type BookingUpdate = v.InferOutput<typeof BookingUpdateSchema>;

export class ApiV2ProblemError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    message: string,
  ) {
    super(message);
  }
}

export class BookingUnavailableError extends Error {}

export function isBookingOverlapError(error: unknown): boolean {
  return error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.overlap";
}

export const BOOKING_READ_FIELDS =
  "id,version,target,canViewConfiguration,timezone,start,end,state,kind,purpose,bookedBy,createdBy,privacy,canEdit,canCancel,createdAt,updatedAt";
export async function parseApiV2Problem(response: Response): Promise<ApiV2ProblemError> {
  const body: unknown = await response.json().catch(() => null);
  if (body && typeof body === "object") {
    const record = body as Record<string, unknown>;
    return new ApiV2ProblemError(
      typeof record.status === "number" ? record.status : response.status,
      typeof record.code === "string" ? record.code : undefined,
      typeof record.detail === "string"
        ? record.detail
        : typeof record.message === "string"
          ? record.message
          : `Request failed (${response.status})`,
    );
  }
  return new ApiV2ProblemError(response.status, undefined, `Request failed (${response.status})`);
}

export async function requireSuccess(response: Response): Promise<Response> {
  if (!response.ok) throw await parseApiV2Problem(response);
  return response;
}

export async function requestBooking<TSchema extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>>>(
  url: string,
  token: string,
  schema: TSchema,
  init?: RequestInit,
): Promise<v.InferOutput<TSchema>> {
  const response = await requireSuccess(
    await fetch(url, {
      ...init,
      headers: init?.body ? apiV2JsonHeaders(token, init.headers) : apiV2Headers(token, init?.headers),
    }),
  );
  return parseOrThrow(schema, await response.json());
}

export async function fetchBooking(id: number, token: string, signal?: AbortSignal): Promise<BookingDetails> {
  const parameters = new URLSearchParams({
    depth: "1",
    "fields[bookings]": BOOKING_READ_FIELDS,
  });
  try {
    return await requestBooking(`/api/v2/bookings/${id}?${parameters}`, token, BookingDetailsSchema, {
      signal,
    });
  } catch (error) {
    if (error instanceof ApiV2ProblemError || error instanceof TypeError || error instanceof DOMException) throw error;
    throw new BookingUnavailableError("Booking details are unavailable");
  }
}

export function createBooking(input: BookingCreate, token: string): Promise<BookingMutation> {
  return requestBooking("/api/v2/bookings?depth=1", token, BookingMutationSchema, {
    method: "POST",
    body: JSON.stringify(parseOrThrow(BookingCreateSchema, input)),
  });
}

export function updateBooking(
  id: number,
  version: number,
  input: BookingUpdate,
  token: string,
): Promise<BookingMutation> {
  return requestBooking(`/api/v2/bookings/${id}?depth=1`, token, BookingMutationSchema, {
    method: "PATCH",
    headers: { "If-Match": `"${version}"` },
    body: JSON.stringify(parseOrThrow(BookingUpdateSchema, input)),
  });
}

export function cancelBooking(id: number, version: number, token: string): Promise<BookingMutation> {
  return updateBooking(id, version, { state: "CANCELLED" }, token);
}
