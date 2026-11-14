import { useNavigate, useSearch } from "@tanstack/react-router";
import * as React from "react";
import {
  bookingCatalogueQuery,
  catalogueItemAsConfiguration,
  fetchBookingCatalogue,
} from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { useTableList } from "@/modules/common/table-list/hooks/state";
import { type BookingConfiguration, bookingConfigurationConfig } from "../bookable-items/bookingConfiguration";
import { type CalendarLayout, type CalendarView } from "./BookingEventsCalendar";
import { useCalendarEvents } from "./calendarEvents";
import { calendarDates } from "./calendarLayoutUtils";

export const calendarResourceConfig = resolveCollectionConfig({
  ...bookingConfigurationConfig,
  slug: "calendar-resources",
  defaultColumns: ["target"],
  pagination: { defaultLimit: 20, limits: [10, 20, 30, 40, 50] },
} as const);

export function useCalendarContentData() {
  const { date } = useSearch({ from: "/booking/calendar/" });
  const navigate = useNavigate({ from: "/booking/calendar/" });
  const { data: currentUser } = useCurrentUserQuery();
  const [view, setView] = React.useState<CalendarView>("day");
  const [layout, setLayout] = React.useState<CalendarLayout>("resources");
  const [resettingControls, setResettingControls] = React.useState(false);
  const preferences = useBookingDisplayPreferences();
  const selectedDate = date ?? todayInTimeZone(preferences.timeZone);
  const dates = calendarDates(selectedDate, view);
  const resourceTable = useTableList<BookingConfiguration>({
    config: calendarResourceConfig,
    dataSource: {
      type: "remote",
      queryKey: (state) => [
        ...bookingCatalogueQuery(undefined, {
          q: state.filters.search,
          page: state.page.pageIndex + 1,
          pageSize: state.page.pageSize,
        }).queryKey,
        "calendarResources",
      ],
      keepPreviousData: true,
      fetch: async (state, { signal }) => {
        const result = await fetchBookingCatalogue(
          {
            q: state.filters.search,
            page: state.page.pageIndex + 1,
            pageSize: state.page.pageSize,
          },

          signal,
        );
        return {
          rows: result.items.map(catalogueItemAsConfiguration),
          rowCount: result.total,
        };
      },
    },
    initialState: { visibleFields: ["target"] },
    features: { sorting: false, columns: false },
    queryString: { parameterPrefix: "calendar-resources", tableId: "booking-calendar-resources" },
  });
  const resourceTargetIds = React.useMemo(
    () => resourceTable.tableProps.rows.flatMap((row) => (row.target ? [row.target.globalId] : [])),
    [resourceTable.tableProps.rows],
  );
  const events = useCalendarEvents(
    dates[0],
    dates.at(-1) ?? dates[0],
    preferences.timeZone,

    layout === "resources" ? resourceTargetIds : undefined,
    !resettingControls &&
      (layout !== "resources" ||
        (resourceTable.tableProps.status !== "loading" && resourceTable.tableProps.status !== "refreshing")),
  );
  const resourceTargets = resourceTable.tableProps.rows.flatMap((row) => (row.target ? [row.target] : []));
  return {
    currentUser,
    events,
    layout,
    navigate,
    preferences,
    resourceTable,
    resourceTargets,
    selectedDate,
    setLayout,
    setResettingControls,
    setView,
    view,
  };
}
