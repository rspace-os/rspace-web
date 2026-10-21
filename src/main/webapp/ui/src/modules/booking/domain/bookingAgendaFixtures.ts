import type { BookingListDocument } from "./booking";

export function bookingListDocument(overrides: Partial<BookingListDocument> = {}): BookingListDocument {
  const start = overrides.start ?? "2026-09-24T08:00:00.000Z";
  const privacy = overrides.privacy ?? "full";
  return {
    id: 1,
    version: 1,
    target: null,
    timezone: "Europe/Berlin",
    start,
    end: "2026-09-24T09:00:00.000Z",
    state: "CONFIRMED",
    kind: "BOOKING",
    privacy,
    purpose: null,
    bookedBy: null,
    createdBy: null,
    canViewConfiguration: privacy === "full",
    requesterId: 7,
    canEdit: false,
    canCancel: false,
    createdAt: start,
    updatedAt: start,
    ...overrides,
  };
}
