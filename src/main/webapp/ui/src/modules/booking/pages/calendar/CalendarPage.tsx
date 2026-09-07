import { useNavigate, useSearch } from "@tanstack/react-router";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { catalogueItemAsConfiguration, fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { type BookingConfiguration, bookingConfigurationConfig } from "../bookable-items/bookingConfiguration";
import {
  BookingEventsCalendar,
  type CalendarLayout,
  type CalendarView,
  calendarDates,
  ResourceScheduleSkeleton,
} from "./BookingEventsCalendar";
import { useCalendarEvents } from "./calendarEvents";

const calendarResourceConfig = resolveCollectionConfig({
  ...bookingConfigurationConfig,
  slug: "calendar-resources",
  defaultColumns: ["target"],
  pagination: { defaultLimit: 20, limits: [10, 20, 30, 40, 50] },
} as const);

export default function CalendarPage() {
  return (
    <React.Suspense fallback={<CalendarSkeleton />}>
      <CalendarContent />
    </React.Suspense>
  );
}

function CalendarSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className="min-h-screen w-full min-w-0 space-y-5 overflow-hidden bg-background p-4 sm:p-8" aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true" className="space-y-5">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-24 w-full" />
        <ResourceScheduleSkeleton />
      </div>
    </main>
  );
}

function CalendarContent() {
  const { date } = useSearch({ from: "/booking/calendar" });
  const navigate = useNavigate({ from: "/booking/calendar" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
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
    token,
    layout === "resources" ? resourceTargetIds : undefined,
    !resettingControls &&
      (layout !== "resources" ||
        (resourceTable.tableProps.status !== "loading" && resourceTable.tableProps.status !== "refreshing")),
  );
  const resourceTargets = resourceTable.tableProps.rows.flatMap((row) => (row.target ? [row.target] : []));

  return (
    <BookingEventsCalendar
      date={selectedDate}
      view={view}
      layout={layout}
      timezone={preferences.timeZone}
      availabilityStartMinute={preferences.availabilityWindow.startMinute}
      availabilityEndMinute={preferences.availabilityWindow.endMinute}
      events={events.data ?? []}
      resources={resourceTargets}
      resourceTableProps={resourceTable.tableProps}
      currentUserId={currentUser.id}
      isLoading={events.isPending && resourceTable.tableProps.status !== "error"}
      isError={events.isError || (layout === "resources" && resourceTable.tableProps.status === "error")}
      onRetry={() => {
        if (layout === "resources" && resourceTable.tableProps.status === "error") {
          void resourceTable.refetch();
        } else {
          void events.refetch();
        }
      }}
      onDateChange={(nextDate) =>
        void navigate({ search: (current) => ({ ...current, date: nextDate }), replace: true })
      }
      onControlsReset={async () => {
        // Date navigation is asynchronous; avoid fetching an intermediate date/period combination.
        setResettingControls(true);
        try {
          await navigate({ search: (current) => ({ ...current, date: undefined }), replace: true });
          React.startTransition(() => {
            setView("day");
            setLayout("resources");
          });
        } finally {
          React.startTransition(() => setResettingControls(false));
        }
      }}
      onViewChange={setView}
      onLayoutChange={setLayout}
    />
  );
}
