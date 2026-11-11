import type { BookingListDocument } from "@/modules/booking/domain/booking";

export function bookingTarget(id: number, name: string, location?: { name: string; globalId: string } | null) {
  const base = {
    relationTo: "instruments" as const,
    value: { id, name, deleted: false },
    globalId: `IN${id}`,
  };
  if (location === undefined) return base;
  return {
    ...base,
    value: {
      ...base.value,
      parentContainer:
        location === null
          ? null
          : {
              relationTo: "containers" as const,
              globalId: location.globalId,
              value: { id, name: location.name, globalId: location.globalId },
            },
    },
  };
}

const target = (id: number, name: string, location?: { name: string; globalId: string } | null) =>
  bookingTarget(id, name, location === undefined ? { name: `${name} room`, globalId: `IC${id}` } : location);

const timestamps = {
  version: 0,
  kind: "BOOKING" as const,
  createdAt: "2026-08-01T09:00:00Z",
  updatedAt: "2026-08-01T09:00:00Z",
  canCancel: false,
};

export const ownBooking: BookingListDocument = {
  id: 41,
  target: target(123, "Confocal microscope"),
  canViewConfiguration: true,
  requester: { relationTo: "users", value: { id: 1 } },
  timezone: "Europe/Berlin",
  start: "2026-08-17T08:00:00Z",
  end: "2026-08-17T10:00:00Z",
  state: "CONFIRMED",
  purpose: "Cell imaging",
  bookedBy: "Ada Lovelace",
  privacy: "full",
  canEdit: true,
  ...timestamps,
};

export const otherBooking: BookingListDocument = {
  ...ownBooking,
  id: 42,
  target: target(124, "Electron microscope", { name: "Workbench", globalId: "BE124" }),
  requester: { relationTo: "users", value: 2 },
  start: "2026-08-19T12:00:00Z",
  end: "2026-08-19T13:30:00Z",
  purpose: "Cryo-grid screening",
  bookedBy: "Grace Hopper (grace)",
  canEdit: false,
};

export const busyBooking: BookingListDocument = {
  ...otherBooking,
  id: 43,
  requester: null,
  start: "2026-08-21T09:00:00Z",
  end: "2026-08-21T11:00:00Z",
  purpose: null,
  bookedBy: null,
  privacy: "busy",
  canEdit: false,
  canCancel: false,
};

export function collectionResponse(
  docs: readonly unknown[],
  options: { page?: number; limit?: number; totalDocs?: number } = {},
) {
  const page = options.page ?? 1;
  const limit = options.limit ?? 100;
  const totalDocs = options.totalDocs ?? docs.length;
  const totalPages = limit > 0 && totalDocs > 0 ? Math.ceil(totalDocs / limit) : 0;
  return {
    docs,
    totalDocs,
    limit,
    page,
    hasNextPage: page < totalPages,
  };
}
