import { useNavigate, useSearch } from "@tanstack/react-router";
import * as React from "react";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import { BookingCreationButtonGroup } from "@/modules/booking/creation/BookingCreationButtonGroup";
import { bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { addCalendarDays } from "@/modules/booking/domain/bookingTime";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { useApiV2TableList } from "@/modules/common/table-list/adapters/apiV2/useApiV2TableList";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import {
  type BookingConfiguration,
  type BookingConfigurationRow,
  BookingConfigurationSchema,
  bookingConfigurationConfig,
} from "../bookable-items/bookingConfiguration";
import { BookingEventsCalendar, type CalendarLayout, type CalendarView, calendarDates } from "./BookingEventsCalendar";
import { useCalendarEvents } from "./calendarEvents";

const calendarResourceConfig = {
  ...bookingConfigurationConfig,
  slug: "calendar-resources",
  defaultColumns: ["target"],
  pagination: { defaultLimit: 20, maximumLimit: 100, limits: [10, 20, 30, 40, 50] },
} as const;

const calendarResourceFilter = {
  kind: "and",
  children: [
    { kind: "comparison", field: "enabled", operator: "equals", value: true },
    { kind: "comparison", field: "target.deleted", operator: "equals", value: false },
  ],
} as const satisfies FilterExpression<BookingConfiguration>;

const calendarResourceProjection = {
  fixed: ["id", "target", "enabled", "timezone", ...schedulingSettingsFieldNames],
} as const;

export default function CalendarPage() {
  const { date } = useSearch({ from: "/booking/calendar" });
  const navigate = useNavigate({ from: "/booking/calendar" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  const [view, setView] = React.useState<CalendarView>("week");
  const [layout, setLayout] = React.useState<CalendarLayout>("time-grid");
  const preferences = useBookingDisplayPreferences();
  const beginCreation = useBookingCreationStore((state) => state.beginCreation);
  const creationActive = useBookingCreationStore((state) => state.activeCreation !== null);
  const selectedDate = date ?? todayInTimeZone(preferences.timeZone);
  const dates = calendarDates(selectedDate, view);
  const resourceRequest = React.useMemo(
    () => ({
      token,
      depth: 1,
      projection: calendarResourceProjection,
      baseFilter: calendarResourceFilter,
    }),
    [token],
  );
  const resourceTable = useApiV2TableList({
    resourceName: "booking-configurations",
    config: calendarResourceConfig,
    documentSchema: BookingConfigurationSchema,
    request: resourceRequest,
    query: { keepPreviousData: true },
    table: {
      initialState: { visibleFields: ["target"] },
      features: { filtering: false, sorting: false, columns: false },
      queryString: { parameterPrefix: "calendar-resources", tableId: "booking-calendar-resources" },
    },
  });
  const resourceConfigurations = React.useMemo(
    () =>
      (resourceTable.tableProps.rows as readonly BookingConfigurationRow[]).flatMap((row) => {
        const option = bookableItemOption(row);
        return option ? [option] : [];
      }),
    [resourceTable.tableProps.rows],
  );
  const resourceTargetIds = React.useMemo(
    () => resourceConfigurations.map((resource) => resource.globalId),
    [resourceConfigurations],
  );
  const events = useCalendarEvents(
    dates[0],
    dates.at(-1) ?? dates[0],
    preferences.timeZone,
    token,
    layout === "resources" ? resourceTargetIds : undefined,
    layout !== "resources" ||
      resourceTable.tableProps.status === "idle" ||
      resourceTable.tableProps.status === "refreshing",
  );
  const resourceTargets = (resourceTable.tableProps.rows as readonly BookingConfigurationRow[]).flatMap((row) =>
    row.target ? [row.target] : [],
  );

  const draftPart = (minute: number) => {
    const dayOffset = Math.floor(minute / (24 * 60));
    const minuteOfDay = ((minute % (24 * 60)) + 24 * 60) % (24 * 60);
    return {
      date: addCalendarDays(selectedDate, dayOffset),
      time: `${String(Math.floor(minuteOfDay / 60)).padStart(2, "0")}:${String(minuteOfDay % 60).padStart(2, "0")}`,
    };
  };

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
      resourceConfigurations={resourceConfigurations}
      resourceTableProps={resourceTable.tableProps}
      currentUserId={currentUser.id}
      isLoading={events.isPending}
      isError={events.isError}
      onRetry={() => void events.refetch()}
      onDateChange={(nextDate) =>
        void navigate({ search: (current) => ({ ...current, date: nextDate }), replace: true })
      }
      onViewChange={setView}
      onLayoutChange={setLayout}
      creationAction={<BookingCreationButtonGroup ownerId="calendar-toolbar" initialDate={selectedDate} />}
      creationDisabled={creationActive}
      onResourceRangeSelect={(resource, range, trigger) => {
        const start = draftPart(range.startMinute);
        const end = draftPart(range.endMinute);
        const ownerId = `calendar-resource-${resource.configurationId}`;
        const triggerId = `${ownerId}-${selectedDate}`;
        trigger.id = triggerId;
        beginCreation({
          ownerId,
          triggerId,
          eventKind: "BOOKING",
          target: resource,
          initialDate: selectedDate,
          window: {
            startDate: start.date,
            startTime: start.time,
            endDate: end.date,
            endTime: end.time,
          },
          lockTarget: true,
        });
      }}
    />
  );
}
