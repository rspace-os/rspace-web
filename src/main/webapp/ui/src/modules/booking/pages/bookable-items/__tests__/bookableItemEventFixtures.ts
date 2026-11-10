export const booking = {
  id: 41,
  version: 0,
  target: {
    relationTo: "instruments",
    value: { id: 12, name: "Scope", deleted: false },
    globalId: "IN12",
  },
  timezone: "Europe/Berlin",
  start: "2026-08-25T09:00:00Z",
  end: "2026-08-25T10:00:00Z",
  state: "CONFIRMED",
  kind: "BOOKING",
  privacy: "full",
  purpose: null,
  bookedBy: "Ada Lovelace",
  canEdit: true,
  canCancel: true,
  createdAt: "2026-08-17T00:00:00Z",
  updatedAt: "2026-08-17T00:00:00Z",
};

export const busyBooking = {
  ...booking,
  id: 42,
  privacy: "busy",
  purpose: null,
  bookedBy: null,
  canEdit: false,
  canCancel: false,
};

export const maintenanceBooking = {
  ...booking,
  id: 43,
  kind: "MAINTENANCE",
  purpose: "Laser alignment",
  bookedBy: null,
  createdBy: "Morgan Ellis (mellis)",
  canEdit: false,
};

export function bookableItemEventResponse(
  docs: readonly unknown[],
  options: { page?: number; hasNextPage?: boolean; totalDocs?: number } = {},
) {
  const page = options.page ?? 1;
  const hasNextPage = options.hasNextPage ?? false;
  return {
    docs,
    totalDocs: options.totalDocs ?? docs.length,
    limit: 10,
    page,
    hasNextPage,
  };
}
