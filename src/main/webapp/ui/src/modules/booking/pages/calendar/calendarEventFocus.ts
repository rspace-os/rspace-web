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
  search.set("focusRequest", nextFocusRequest());
  search.delete("focusMode");

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

export function calendarCreatedEventFocusHref({
  id,
  start,
  timeZone,
  searchStr,
}: {
  id: number;
  start: string;
  timeZone: string;
  searchStr: string;
}): string | undefined {
  const search = new URLSearchParams(searchStr);
  const date = search.get("date") || currentWallClock(new Date().toISOString(), timeZone).date;
  if (currentWallClock(start, timeZone).date !== date) return undefined;

  search.set("focus", String(id));
  search.set("focusRequest", nextFocusRequest());
  search.set("focusMode", "created");
  return `/booking/calendar?${search.toString()}`;
}

function nextFocusRequest(): string {
  return `${Date.now().toString(36)}-${(++focusRequestSequence).toString(36)}`;
}
