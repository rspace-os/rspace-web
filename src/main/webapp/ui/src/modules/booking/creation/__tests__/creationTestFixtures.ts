import { DEFAULT_SCHEDULING_SETTINGS } from "@/modules/booking/configuration/schedulingSettings";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { ALL_ISO_WEEKDAYS, type OpeningHoursEntry } from "@/modules/booking/domain/bookingOpeningHours";
import type { EditableBooking } from "../BookingForm";

const bookerCapabilities = {
  canEditConfiguration: false,
  canViewAudit: false,
  canViewAccess: false,
  canManageAssignments: false,
  canManageOwners: false,
  canCreateBooking: true,
  canManageOwnBookings: true,
  canManageAllEvents: false,
  canCreateBlockout: false,
  canSubscribeCalendar: true,
  canLeaveConfiguration: false,
};

type CreationTestOption = BookableItemOption & {
  capabilities: typeof bookerCapabilities;
};

export const confocalTarget: BookableItemOption = {
  configurationId: 7,
  targetId: 123,
  globalId: "IN123",
  name: "Confocal microscope",
  timezone: "Europe/Berlin",
  slotGranularityMinutes: 5,
  openingHours: ALL_ISO_WEEKDAYS.map(
    (dayOfWeek): OpeningHoursEntry => ({
      dayOfWeek,
      start: "00:00",
      end: "24:00",
    }),
  ),
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};

export const editableBooking = {
  id: 41,
  version: 0,
  target: {
    relationTo: "booking-instruments",
    globalId: "IN123",
    value: { id: 123, name: "Confocal microscope", deleted: false },
  },
  timezone: "Europe/Berlin",
  start: "2026-10-25T01:30:00Z",
  end: "2026-10-25T02:30:00Z",
  state: "CONFIRMED",
  kind: "BOOKING",
  privacy: "full",
  purpose: "Imaging",
  bookedBy: "Ada Lovelace (ada)",
  canEdit: true,
  canCancel: true,
  createdAt: "2026-08-12T10:00:00Z",
  updatedAt: "2026-08-12T10:00:00Z",
} satisfies EditableBooking;

export function configuration(id: number, name: string): CreationTestOption {
  return {
    configurationId: id,
    targetId: id,
    globalId: `IN${id}`,
    name,
    timezone: "Europe/Berlin",
    ...DEFAULT_SCHEDULING_SETTINGS,
    capabilities: { ...bookerCapabilities },
  };
}

function catalogueConfiguration(option: BookableItemOption) {
  return {
    ...option,
    configurationVersion: 0,
    targetType: "INSTRUMENT" as const,
    effectiveRole: "BOOKER" as const,
    capabilities: { ...bookerCapabilities, ...option.capabilities },
    location: null,
  };
}

export function cataloguePage(options: readonly BookableItemOption[], page = 1, total = options.length) {
  return {
    items: options.map(catalogueConfiguration),
    page,
    pageSize: 20,
    total,
    facets: { types: ["INSTRUMENT"] },
  };
}
