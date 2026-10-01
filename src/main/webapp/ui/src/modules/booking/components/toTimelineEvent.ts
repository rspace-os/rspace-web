import type { Booking, BookingListDocument } from "@/modules/booking/domain/booking";
import { sliceAcrossZonedDay } from "@/modules/booking/domain/bookingTime";
import i18n from "@/modules/common/i18n";
import type { DayTimelineEvent } from "./DayTimelineEvent";

export function toTimelineEvent(
  event: Booking | BookingListDocument,
  date: string,
  timezone: string,
): DayTimelineEvent {
  const slice = sliceAcrossZonedDay(event.start, event.end, date, timezone);
  const instrumentTime = {
    startInstant: event.start,
    endInstant: event.end,
    instrumentTimeZone: event.timezone,
  };
  if (event.privacy === "busy") {
    return { id: String(event.id), kind: "booking", privacy: "busy", ...slice, ...instrumentTime };
  }
  const target = event.target;
  const itemName = target?.value.name ?? i18n.t("common:values.unknownItem");
  const itemGlobalId = target?.globalId ?? null;
  const location =
    target?.value.parentContainerName != null && target.value.parentContainerGlobalId != null
      ? {
          name: target.value.parentContainerName,
          globalId: target.value.parentContainerGlobalId,
        }
      : undefined;
  if (event.kind === "MAINTENANCE") {
    return {
      id: String(event.id),
      kind: "blockout",
      title: i18n.t("booking:bookings.maintenanceLabel"),
      item: { name: itemName, globalId: itemGlobalId, location },
      createdBy: event.createdBy ?? undefined,
      notes: event.purpose ?? undefined,
      ...slice,
      ...instrumentTime,
    };
  }
  return {
    id: String(event.id),
    kind: "booking",
    privacy: "full",
    title: itemName,
    bookedBy: event.bookedBy ?? "",
    item: {
      name: itemName,
      globalId: itemGlobalId,
      location,
    },
    notes: event.purpose ?? undefined,
    canEdit: event.canEdit,
    ...slice,
    ...instrumentTime,
  };
}
