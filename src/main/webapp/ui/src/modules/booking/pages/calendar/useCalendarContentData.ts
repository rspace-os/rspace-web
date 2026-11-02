import { useNavigate, useSearch } from "@tanstack/react-router";
import { parseAsString, useQueryState } from "nuqs";
import * as React from "react";
import { bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";
import { catalogueItemAsConfiguration, fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { useTableList } from "@/modules/common/table-list/hooks/state";
import { type BookingConfiguration, bookingConfigurationConfig } from "../bookable-items/bookingConfiguration";
import { type CalendarLayout, type CalendarView, calendarDates } from "./BookingEventsCalendar";
import { useCalendarEvents } from "./calendarEvents";

const calendarResourceConfig = resolveCollectionConfig({
  ...bookingConfigurationConfig,
  slug: "calendar-resources",
  defaultColumns: ["target"],
  pagination: { defaultLimit: 20, limits: [10, 20, 30, 40, 50] },
} as const);

const calendarSearchParser = parseAsString.withDefault("").withOptions({ history: "replace", clearOnDefault: true });

export function useCalendarContentData() {
  const { date } = useSearch({ from: "/booking/calendar/" });
  const navigate = useNavigate({ from: "/booking/calendar/" });
  const [calendarSearch, setCalendarSearch] = useQueryState("calendar-resources.q", calendarSearchParser);
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  const [view, setView] = React.useState<CalendarView>("day");
  const [layout, setLayout] = React.useState<CalendarLayout>("resources");
  const [resettingControls, setResettingControls] = React.useState(false);
  const [eventScopeIsFiltered, setEventScopeIsFiltered] = React.useState(() => calendarSearch.trim() !== "");
  const preferences = useBookingDisplayPreferences();
  const beginCreation = useBookingCreationStore((state) => state.beginCreation);
  const creationActive = useBookingCreationStore((state) => state.activeCreation !== null);
  const selectedDate = date ?? todayInTimeZone(preferences.timeZone);
  const dates = calendarDates(selectedDate, view);
  const resourceTable = useTableList<BookingConfiguration>({
    config: calendarResourceConfig,
    dataSource: {
      type: "remote",
      queryKey: (state) => ["api-v2", "booking-catalogue", "calendar", token, state],
      keepPreviousData: true,
      fetch: async (state, { signal }) => {
        const result = await fetchBookingCatalogue(
          {
            q: state.filters.search,
            page: state.page.pageIndex + 1,
            pageSize: state.page.pageSize,
          },
          token,
          signal,
        );
        return {
          rows: result.items.map(catalogueItemAsConfiguration),
          rowCount: result.total,
        };
      },
    },
    initialState: { filters: { search: calendarSearch, expression: null }, visibleFields: ["target"] },
    features: { sorting: false, columns: false },
    queryString: false,
  });
  React.useEffect(() => {
    if (resourceTable.state.filters.search === calendarSearch) return;
    resourceTable.setFilters({ ...resourceTable.state.filters, search: calendarSearch });
  }, [calendarSearch, resourceTable.setFilters, resourceTable.state.filters]);
  const resourceConfigurations = React.useMemo(
    () =>
      resourceTable.tableProps.rows.flatMap((row) => {
        if (!row.capabilities.canCreateBooking && !row.capabilities.canEditConfiguration) return [];
        const option = bookableItemOption(row);
        return option ? [option] : [];
      }),
    [resourceTable.tableProps.rows],
  );
  const resourceTargetIds = React.useMemo(
    () => resourceTable.tableProps.rows.flatMap((row) => (row.target ? [row.target.globalId] : [])),
    [resourceTable.tableProps.rows],
  );
  const events = useCalendarEvents(
    dates[0],
    dates.at(-1) ?? dates[0],
    preferences.timeZone,
    token,
    layout === "resources" && !eventScopeIsFiltered ? resourceTargetIds : undefined,
    !resettingControls &&
      (layout !== "resources" ||
        (resourceTable.tableProps.status !== "loading" && resourceTable.tableProps.status !== "refreshing")),
  );
  const resourceTargets = resourceTable.tableProps.rows.flatMap((row) => (row.target ? [row.target] : []));

  return {
    navigate,
    calendarSearch,
    setCalendarSearch,
    currentUser,
    view,
    setView,
    layout,
    setLayout,
    setResettingControls,
    setEventScopeIsFiltered,
    preferences,
    beginCreation,
    creationActive,
    selectedDate,
    resourceTable,
    resourceConfigurations,
    events,
    resourceTargets,
  };
}
