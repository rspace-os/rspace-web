import type { BookingListDocument } from "@/modules/booking/domain/booking";

const target = (id: number, name: string, location?: { name: string; globalId: string } | null) => ({
  relationTo: "booking-instruments" as const,
  globalId: `IN${id}`,
  value: {
    id,
    name,
    deleted: false,
    parentContainerName: location === null ? null : (location?.name ?? `${name} room`),
    parentContainerGlobalId: location === null ? null : (location?.globalId ?? `IC${id}`),
  },
});

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
  requesterId: 1,
  timezone: "Europe/Berlin",
  start: "2026-08-17T08:00:00Z",
  end: "2026-08-17T10:00:00Z",
  state: "CONFIRMED",
  purpose: "Cell imaging",
  bookedBy: "Ada Lovelace (ada)",
  privacy: "full",
  canEdit: true,
  ...timestamps,
};

export const otherBooking: BookingListDocument = {
  ...ownBooking,
  id: 42,
  target: target(124, "Electron microscope", { name: "Workbench", globalId: "BE124" }),
  requesterId: 2,
  start: "2026-08-19T12:00:00Z",
  end: "2026-08-19T13:30:00Z",
  purpose: "Cryo-grid screening",
  bookedBy: "Grace Hopper (grace)",
  canEdit: false,
};

export const busyBooking: BookingListDocument = {
  ...otherBooking,
  id: 43,
  requesterId: 3,
  start: "2026-08-21T09:00:00Z",
  end: "2026-08-21T11:00:00Z",
  purpose: null,
  bookedBy: null,
  privacy: "busy",
  canEdit: false,
};

export function collectionResponse(
  docs: readonly Record<string, unknown>[],
  options: { page?: number; totalDocs?: number; totalPages?: number } = {},
) {
  const page = options.page ?? 1;
  const totalDocs = options.totalDocs ?? docs.length;
  const totalPages = options.totalPages ?? (totalDocs ? 1 : 0);
  return {
    docs,
    totalDocs,
    limit: 100,
    page,
    pagingCounter: (page - 1) * 100 + 1,
    totalPages,
    hasPrevPage: page > 1,
    hasNextPage: page < totalPages,
    prevPage: page > 1 ? page - 1 : null,
    nextPage: page < totalPages ? page + 1 : null,
  };
}
