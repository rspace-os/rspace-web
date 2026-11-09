import { bookingFormSearch } from "../bookingFormSearch";
import { calendarLayouts } from "./calendarLayoutUtils";

/** Typed navigation into the Calendar's item scope; nuqs owns subsequent control edits. */
export function calendarSearch(search: Record<string, unknown>) {
  const layout = calendarLayouts.find((layout) => layout === search.layout);
  return {
    ...bookingFormSearch(search),
    ...(layout ? { layout } : {}),
    ...(typeof search["calendar-resources.q"] === "string"
      ? { "calendar-resources.q": search["calendar-resources.q"] }
      : {}),
    ...(typeof search["calendar-resources.where"] === "string"
      ? { "calendar-resources.where": search["calendar-resources.where"] }
      : {}),
  };
}
