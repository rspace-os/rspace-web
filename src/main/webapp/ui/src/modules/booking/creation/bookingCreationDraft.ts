import { type BookingWindowDraft, isPlainDate } from "@/modules/booking/domain/bookingTime";

export type BookingCreationDraft = {
  targetGlobalId?: string;
  window: BookingWindowDraft;
  purpose: string;
};

function isRecord(value: unknown): value is Record<PropertyKey, unknown> {
  return typeof value === "object" && value !== null;
}

function isDate(value: unknown): value is string {
  return typeof value === "string" && (value === "" || isPlainDate(value));
}

function isTime(value: unknown): value is string {
  return typeof value === "string" && (value === "" || /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value));
}

function isOccurrence(value: unknown): value is "earlier" | "later" | undefined {
  return value === undefined || value === "earlier" || value === "later";
}

function parseBookingCreationDraft(value: unknown): BookingCreationDraft | undefined {
  if (!isRecord(value) || !isRecord(value.window)) return undefined;
  const window = value.window;
  if (
    (value.targetGlobalId !== undefined &&
      (typeof value.targetGlobalId !== "string" || !/^IN\d+$/.test(value.targetGlobalId))) ||
    typeof value.purpose !== "string" ||
    value.purpose.length > 1000 ||
    !isDate(window.startDate) ||
    !isTime(window.startTime) ||
    !isOccurrence(window.startOccurrence) ||
    !isDate(window.endDate) ||
    !isTime(window.endTime) ||
    !isOccurrence(window.endOccurrence)
  ) {
    return undefined;
  }
  return {
    ...(value.targetGlobalId ? { targetGlobalId: value.targetGlobalId } : {}),
    window: {
      startDate: window.startDate,
      startTime: window.startTime,
      ...(window.startOccurrence ? { startOccurrence: window.startOccurrence } : {}),
      endDate: window.endDate,
      endTime: window.endTime,
      ...(window.endOccurrence ? { endOccurrence: window.endOccurrence } : {}),
    },
    purpose: value.purpose,
  };
}

export function bookingCreationDraftFromHistoryState(
  state: unknown,
  targetGlobalId: string | undefined,
): BookingCreationDraft | undefined {
  if (!isRecord(state)) return undefined;
  const draft = parseBookingCreationDraft(state.bookingCreationDraft);
  return draft?.targetGlobalId === targetGlobalId ? draft : undefined;
}
