import { useState } from "react";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import type { CalendarLayout, CalendarView } from "@/modules/booking/pages/calendar/calendarLayoutUtils";
import { type AdvancedFilter, EVENTS, ITEMS, MOCK_FILTERS } from "./calendarToolbarPrototypeFixtures";

export const TIME_ZONE = "Europe/Berlin";
export const LAYOUT_LABELS: Record<CalendarLayout, string> = {
  "time-grid": "Time grid",
  resources: "Resources",
  agenda: "Agenda",
};
export const PERIOD_LABELS: Record<CalendarView, string> = { day: "Day", week: "Week", month: "Month" };
export const MINE_LABEL = "My Bookings";
export const MY_ITEMS_LABEL = "Owned Items";

/** The state every variant shares, with the production rules: Resources has no Month, and Reset restores the defaults. */
export function useCalendarState(appliedFilters: number) {
  const today = todayInTimeZone(TIME_ZONE);
  const [date, setDate] = useState(today);
  const [layout, setLayoutState] = useState<CalendarLayout>("resources");
  const [period, setPeriod] = useState<CalendarView>("day");
  const [mine, setMine] = useState(false);
  const [myItems, setMyItems] = useState(false);
  const [search, setSearch] = useState("");
  const [filters, setFilters] = useState<readonly AdvancedFilter[]>(MOCK_FILTERS.slice(0, appliedFilters));
  const setLayout = (next: CalendarLayout) => {
    if (next === "resources" && period === "month") setPeriod("week");
    setLayoutState(next);
  };
  const hasChanges =
    date !== today ||
    layout !== "resources" ||
    period !== "day" ||
    mine ||
    myItems ||
    search !== "" ||
    filters.length > 0;
  return {
    today,
    date,
    setDate,
    layout,
    setLayout,
    period,
    setPeriod,
    mine,
    setMine,
    myItems,
    setMyItems,
    search,
    setSearch,
    filters,
    addFilter: () => {
      const next = MOCK_FILTERS.find((filter) => !filters.some(({ id }) => id === filter.id));
      if (next) setFilters([...filters, next]);
    },
    removeFilter: (id: string) => setFilters(filters.filter((filter) => filter.id !== id)),
    hasChanges,
    reset: () => {
      setDate(today);
      setLayoutState("resources");
      setPeriod("day");
      setMine(false);
      setMyItems(false);
      setSearch("");
      setFilters([]);
    },
  };
}

export type CalendarState = ReturnType<typeof useCalendarState>;

export function viewSummary(calendar: CalendarState) {
  return `${LAYOUT_LABELS[calendar.layout]} · ${PERIOD_LABELS[calendar.period]}`;
}

export function showSummary(calendar: CalendarState) {
  if (calendar.mine && calendar.myItems) return "Mine on owned items";
  if (calendar.mine) return MINE_LABEL;
  if (calendar.myItems) return MY_ITEMS_LABEL;
  return "All bookings";
}

export function dayLabel(date: string, offset: number) {
  const day = new Date(`${date}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() + offset);
  return new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", timeZone: "UTC" }).format(
    day,
  );
}

export function visibleEvents(calendar: CalendarState) {
  const days = calendar.period === "day" ? 1 : 7;
  const search = calendar.search.trim().toLocaleLowerCase();
  const owned = new Set<string>(ITEMS.filter((item) => item.owned).map((item) => item.name));
  return EVENTS.filter(
    (event) =>
      event.day < days &&
      (!calendar.mine || event.mine) &&
      (!calendar.myItems || owned.has(event.item)) &&
      // Any advanced filter hides every third event, just so applying one visibly changes the calendar.
      (calendar.filters.length === 0 || event.id % 3 !== 0) &&
      (search === "" || `${event.item} ${event.purpose} ${event.bookedBy}`.toLocaleLowerCase().includes(search)),
  );
}
