import { currentWallClock } from "@/modules/booking/domain/bookingTime";

let focusRequestSequence = 0;

export function calendarEventFocusHref({
  id,
  start,
  targetGlobalId,
  timeZone,
  searchStr,
}: {
  id: number;
  start: string;
  targetGlobalId: string;
  timeZone: string;
  searchStr: string;
}): string {
  const search = new URLSearchParams(searchStr);
  search.set("date", currentWallClock(start, timeZone).date);
  search.set("target", targetGlobalId);
  search.set("focus", String(id));
  search.set("focusRequest", `${Date.now().toString(36)}-${(++focusRequestSequence).toString(36)}`);

  for (const key of [
    "calendar-resources.where",
    "calendar-resources.q",
    "calendar-events.where",
    "mineOnly",
    "myItemsOnly",
  ]) {
    search.delete(key);
  }

  return `/booking/calendar?${search.toString()}`;
}
