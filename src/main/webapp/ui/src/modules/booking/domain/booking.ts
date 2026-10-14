import * as v from "valibot";
import { apiV2Headers, apiV2JsonHeaders } from "@/modules/common/api/apiV2Headers";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";

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
  relationTo: v.literal("instruments"),
  value: v.object({
    id: v.number(),
    name: v.string(),
    deleted: v.boolean(),
    parentContainer: v.optional(
      v.nullable(
        v.object({
          relationTo: v.literal("containers"),
          value: v.union([v.number(), v.object({ id: v.number(), globalId: v.string(), name: v.string() })]),
          globalId: v.string(),
        }),
      ),
    ),
  }),
  globalId: v.string(),
});

const BookingLocationValueSchema = v.object({ id: v.number(), globalId: v.string(), name: v.string() });

/** The bookable item's parent Container or workbench; null where the caller may not read it. */
const BookingLocationSchema = v.optional(
  v.nullable(
    v.object({
      relationTo: v.literal("containers"),
      value: BookingLocationValueSchema,
      globalId: v.string(),
    }),
  ),
);

const BookingReadTargetSchema = v.nullable(BookingTargetSchema);

// Mutation responses are serialized directly from the saved entity. Unlike reads, they do not
// expand relationships, even when the request includes depth=1.
const MutationTargetSchema = v.object({
  relationTo: v.literal("instruments"),
  value: v.union([v.number(), BookingTargetSchema.entries.value]),
  globalId: v.string(),
});

const MutationLocationSchema = v.optional(
  v.nullable(
    v.object({
      relationTo: v.literal("containers"),
      value: v.union([v.number(), BookingLocationValueSchema]),
      globalId: v.string(),
    }),
  ),
);

function bookingIdentity<
  TTarget extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>>,
  TLocation extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>> = typeof BookingLocationSchema,
>(target: TTarget, location: TLocation = BookingLocationSchema as unknown as TLocation) {
  return {
    id: v.number(),
    version: v.number(),
    target,
    location,
    timezone: v.nullable(v.string()),
    ...BookingTimestampEntries,
    state: v.picklist(["CONFIRMED", "CANCELLED"]),
    kind: v.optional(BookingEventKindSchema, "BOOKING"),
  };
}

const BookingAvailabilityIdentitySchema = {
  id: v.number(),
  target: BookingReadTargetSchema,
  location: BookingLocationSchema,
  timezone: v.nullable(v.string()),
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

function bookingSchema<
  TTarget extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>>,
  TLocation extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>> = typeof BookingLocationSchema,
>(target: TTarget, location: TLocation = BookingLocationSchema as unknown as TLocation) {
  const identity = bookingIdentity(target, location);
  return v.variant("privacy", [
    v.object({
      ...identity,
      privacy: v.literal("full"),
      ...BookingFullPrivacyEntries,
      cancellationReason: v.nullable(v.string()),
      ...BookingFullManagementEntries,
    }),
    v.object({
      ...identity,
      privacy: v.literal("busy"),
      purpose: v.null(),
      cancellationReason: v.null(),
      bookedBy: v.null(),
      createdBy: v.optional(v.nullable(v.string())),
      canEdit: v.literal(false),
      canCancel: v.literal(false),
      createdAt: v.string(),
      updatedAt: v.string(),
    }),
  ]);
}

const BookingReadIdentitySchema = {
  ...bookingIdentity(v.nullable(BookingTargetSchema)),
  timezone: v.nullable(v.string()),
};

export const BookingSummarySchema = withEndAfterStart(v.object(BookingAvailabilityIdentitySchema), ["end"]);

export const BookingSchema = withEndAfterStart(bookingSchema(BookingTargetSchema), ["end"]);

export const BookingDetailsSchema = withEndAfterStart(
  v.object({
    ...BookingReadIdentitySchema,
    canViewConfiguration: v.boolean(),
    privacy: v.literal("full"),
    ...BookingFullPrivacyEntries,
    cancellationReason: v.nullable(v.string()),
    ...BookingFullManagementEntries,
  }),
  ["end"],
);

const BookingMutationSchema = withEndAfterStart(bookingSchema(MutationTargetSchema, MutationLocationSchema), ["end"]);

const BookingListDocumentObjectSchema = v.object({
  ...BookingReadIdentitySchema,
  canViewConfiguration: v.boolean(),
  requesterId: v.number(),
  ...BookingFullPrivacyEntries,
  privacy: v.picklist(["full", "busy"]),
  cancellationReason: v.nullable(v.string()),
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
export type BookingMutation = v.InferOutput<typeof BookingMutationSchema>;

export const BookingCreateSchema = v.object({
  target: v.object({ relationTo: v.literal("instruments"), value: v.number() }),
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
    cancellationReason: v.pipe(v.string(), v.maxLength(500)),
    // CONFIRMED only restores a cancelled booking; the server rejects it together with other changes.
    state: v.picklist(["CANCELLED", "CONFIRMED"]),
  }),
);

export type BookingCreate = v.InferOutput<typeof BookingCreateSchema>;
export type BookingUpdate = v.InferOutput<typeof BookingUpdateSchema>;

export class ApiV2ProblemError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    message: string,
    /** The raw problem body, for extension members such as a booking `conflict`. */
    readonly problem?: Record<string, unknown>,
  ) {
    super(message);
  }
}

export class BookingUnavailableError extends Error {}

export const BOOKING_READ_FIELDS =
  "id,version,target,location,canViewConfiguration,timezone,start,end,state,kind,purpose,cancellationReason,bookedBy,createdBy,privacy,canEdit,canCancel,createdAt,updatedAt";
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
      record,
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
    await fetchWithUiToken(url, {
      ...init,
      headers: init?.body ? apiV2JsonHeaders(token, init.headers) : apiV2Headers(token, init?.headers),
    }),
  );
  return parseOrThrow(schema, await response.json());
}

export async function fetchBooking(id: number, token: string, signal?: AbortSignal): Promise<BookingDetails> {
  const parameters = new URLSearchParams({
    depth: "2",
    "fields[bookings]": BOOKING_READ_FIELDS,
    "fields[instruments]": "id,globalId,name,deleted,parentContainer",
    "fields[containers]": "id,globalId,name",
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

export function cancelBooking(id: number, version: number, token: string, reason?: string): Promise<BookingMutation> {
  const cancellationReason = reason?.trim();
  return updateBooking(
    id,
    version,
    cancellationReason ? { state: "CANCELLED", cancellationReason } : { state: "CANCELLED" },
    token,
  );
}

/** Returns a cancelled booking to its slot, if it has not started and the slot is still free. */
export function restoreBooking(id: number, version: number, token: string): Promise<BookingMutation> {
  return updateBooking(id, version, { state: "CONFIRMED" }, token);
}
