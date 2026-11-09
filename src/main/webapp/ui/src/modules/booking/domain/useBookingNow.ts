import { Temporal } from "@js-temporal/polyfill";
import { useSyncExternalStore } from "react";
import { todayInTimeZone } from "./bookingDisplayPreferences";
import { instantToDayMinute } from "./bookingTime";

let snapshot = Temporal.Now.instant();
let timer: ReturnType<typeof setTimeout> | undefined;
const listeners = new Set<() => void>();
function refresh() {
  const now = Temporal.Now.instant();
  if (Math.floor(snapshot.epochMilliseconds / 60000) !== Math.floor(now.epochMilliseconds / 60000)) {
    snapshot = now;
    for (const listener of listeners) listener();
  }
}
function tick() {
  refresh();
  timer = setTimeout(tick, 60000 - (Date.now() % 60000));
}
function subscribe(listener: () => void) {
  listeners.add(listener);
  if (listeners.size === 1) {
    tick();
    document.addEventListener("visibilitychange", refresh);
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) {
      clearTimeout(timer);
      document.removeEventListener("visibilitychange", refresh);
    }
  };
}
/** One minute-aligned clock shared by all mounted booking timelines. */
export function useBookingNow() {
  return useSyncExternalStore(subscribe, () => snapshot);
}
/** Elapsed coordinates include the shorter or longer day at clock changes. */
export function elapsedNowMinute(now: Temporal.Instant, date: string, timezone: string) {
  return instantToDayMinute(now.toString(), date, timezone);
}
/** Week columns use a fixed wall-clock axis rather than elapsed minutes. */
export function pastWallClockMinutes(now: Temporal.Instant, date: string, timezone: string) {
  const today = todayInTimeZone(timezone, new Date(now.epochMilliseconds));
  if (date < today) return 1440;
  if (date > today) return 0;
  const time = now.toZonedDateTimeISO(timezone);
  return time.hour * 60 + time.minute;
}
