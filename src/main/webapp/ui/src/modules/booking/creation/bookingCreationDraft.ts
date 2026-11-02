import * as v from "valibot";
import { WALL_TIME } from "@/modules/booking/domain/bookingOpeningHours";
import { type BookingWindowDraft, isPlainDate } from "@/modules/booking/domain/bookingTime";

export type BookingCreationDraft = {
  targetGlobalId?: string;
  window: BookingWindowDraft;
  purpose: string;
};

const dateSchema = v.union([v.literal(""), v.pipe(v.string(), v.check(isPlainDate))]);
const timeSchema = v.union([v.literal(""), v.pipe(v.string(), v.regex(WALL_TIME))]);
const occurrenceSchema = v.optional(v.union([v.literal("earlier"), v.literal("later")]));

const bookingCreationDraftSchema = v.pipe(
  v.object({
    targetGlobalId: v.optional(v.pipe(v.string(), v.regex(/^IN\d+$/))),
    window: v.object({
      startDate: dateSchema,
      startTime: timeSchema,
      startOccurrence: occurrenceSchema,
      endDate: dateSchema,
      endTime: timeSchema,
      endOccurrence: occurrenceSchema,
    }),
    purpose: v.pipe(v.string(), v.maxLength(1000)),
  }),
  v.transform(
    (draft): BookingCreationDraft => ({
      ...(draft.targetGlobalId ? { targetGlobalId: draft.targetGlobalId } : {}),
      window: {
        startDate: draft.window.startDate,
        startTime: draft.window.startTime,
        ...(draft.window.startOccurrence ? { startOccurrence: draft.window.startOccurrence } : {}),
        endDate: draft.window.endDate,
        endTime: draft.window.endTime,
        ...(draft.window.endOccurrence ? { endOccurrence: draft.window.endOccurrence } : {}),
      },
      purpose: draft.purpose,
    }),
  ),
);

const bookingHistoryStateSchema = v.object({
  bookingCreationDraft: v.optional(bookingCreationDraftSchema),
});

export function bookingCreationDraftFromHistoryState(
  state: unknown,
  targetGlobalId: string | undefined,
): BookingCreationDraft | undefined {
  const result = v.safeParse(bookingHistoryStateSchema, state);
  if (!result.success) return undefined;
  const draft = result.output.bookingCreationDraft;
  return draft?.targetGlobalId === targetGlobalId ? draft : undefined;
}
