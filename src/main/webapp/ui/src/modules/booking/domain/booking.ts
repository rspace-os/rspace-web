import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

const BookingTargetSchema = v.object({
  relationTo: v.literal("instruments"),
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
  relationTo: v.literal("instruments"),
  value: v.union([v.number(), BookingTargetSchema.entries.value]),
  globalId: v.string(),
});

function bookingIdentity<TTarget extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>>>(target: TTarget) {
  return {
    id: v.number(),
    target,
    timezone: v.string(),
    start: v.string(),
    end: v.string(),
    state: v.picklist(["CONFIRMED", "CANCELLED"]),
  };
}

function bookingSchema<TTarget extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>>>(target: TTarget) {
  const identity = bookingIdentity(target);
  return v.variant("privacy", [
    v.object({
      ...identity,
      privacy: v.literal("full"),
      purpose: v.nullable(v.string()),
      bookedBy: v.string(),
      canEdit: v.boolean(),
      createdAt: v.string(),
      updatedAt: v.string(),
    }),
    v.object({
      ...identity,
      privacy: v.literal("busy"),
      purpose: v.null(),
      bookedBy: v.null(),
      canEdit: v.literal(false),
      createdAt: v.string(),
      updatedAt: v.string(),
    }),
  ]);
}

const BookingIdentitySchema = bookingIdentity(BookingTargetSchema);

export const BookingSummarySchema = v.object(BookingIdentitySchema);

export const BookingSchema = bookingSchema(BookingTargetSchema);

const BookingMutationSchema = bookingSchema(MutationTargetSchema);

export const BookingListDocumentSchema = v.object({
  ...BookingIdentitySchema,
  requesterId: v.number(),
  purpose: v.nullable(v.string()),
  bookedBy: v.nullable(v.string()),
  privacy: v.picklist(["full", "busy"]),
  canEdit: v.boolean(),
  createdAt: v.string(),
  updatedAt: v.string(),
});

export type BookingSummary = v.InferOutput<typeof BookingSummarySchema>;
export type Booking = v.InferOutput<typeof BookingSchema>;
export type BookingListDocument = v.InferOutput<typeof BookingListDocumentSchema>;
type BookingMutation = v.InferOutput<typeof BookingMutationSchema>;

export const BookingCreateSchema = v.object({
  target: v.object({ relationTo: v.literal("instruments"), value: v.number() }),
  start: v.string(),
  end: v.string(),
  purpose: v.optional(v.nullable(v.pipe(v.string(), v.maxLength(1000)))),
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

async function requestBooking<TSchema extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>>>(
  url: string,
  token: string,
  schema: TSchema,
  init?: RequestInit,
): Promise<v.InferOutput<TSchema>> {
  const response = await fetch(url, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-Requested-With": "XMLHttpRequest",
      Authorization: `Bearer ${token}`,
      ...init?.headers,
    },
  });
  if (!response.ok) throw await parseApiV2Problem(response);
  return parseOrThrow(schema, await response.json());
}

export function fetchBooking(id: number, token: string, signal?: AbortSignal): Promise<Booking> {
  const parameters = new URLSearchParams({
    depth: "1",
    "fields[bookings]": "id,target,timezone,start,end,state,purpose,bookedBy,privacy,canEdit,createdAt,updatedAt",
  });
  return requestBooking(`/api/v2/bookings/${id}?${parameters}`, token, BookingSchema, { signal });
}

export function createBooking(input: BookingCreate, token: string): Promise<BookingMutation> {
  return requestBooking("/api/v2/bookings?depth=1", token, BookingMutationSchema, {
    method: "POST",
    body: JSON.stringify(parseOrThrow(BookingCreateSchema, input)),
  });
}

export function updateBooking(id: number, input: BookingUpdate, token: string): Promise<BookingMutation> {
  return requestBooking(`/api/v2/bookings/${id}?depth=1`, token, BookingMutationSchema, {
    method: "PATCH",
    body: JSON.stringify(parseOrThrow(BookingUpdateSchema, input)),
  });
}

export function cancelBooking(id: number, token: string): Promise<BookingMutation> {
  return updateBooking(id, { state: "CANCELLED" }, token);
}
