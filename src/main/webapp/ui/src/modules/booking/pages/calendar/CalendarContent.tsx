import * as React from "react";
import { BookingCreationButtonGroup } from "@/modules/booking/creation/BookingCreationButtonGroup";
import { dayMinuteToZonedTime, wallClockDraftFromInstants } from "@/modules/booking/domain/bookingTime";
import { BookingEventsCalendar } from "./BookingEventsCalendar";
import { useCalendarContentData } from "./useCalendarContentData";

export function CalendarContent() {
  const {
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
  } = useCalendarContentData();

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
      searchControl={{
        value: calendarSearch,
        onChange: (search) => void setCalendarSearch(search || null),
      }}
      onEventScopeChange={setEventScopeIsFiltered}
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
      creationAction={
        <BookingCreationButtonGroup ownerId="calendar-toolbar" initialDate={selectedDate} size="default" />
      }
      creationDisabled={creationActive}
      onResourceRangeSelect={(resource, range, trigger) => {
        const window = wallClockDraftFromInstants(
          dayMinuteToZonedTime(selectedDate, preferences.timeZone, range.startMinute).toInstant().toString(),
          dayMinuteToZonedTime(selectedDate, preferences.timeZone, range.endMinute).toInstant().toString(),
          preferences.timeZone,
        );
        const ownerId = `calendar-resource-${resource.configurationId}`;
        const triggerId = `${ownerId}-${selectedDate}`;
        trigger.id = triggerId;
        beginCreation({
          ownerId,
          triggerId,
          eventKind: "BOOKING",
          target: resource,
          initialDate: selectedDate,
          window,
          lockTarget: true,
          timelineAdjustable: trigger.matches('[data-testid="day-timeline-scroller"]'),
        });
      }}
    />
  );
}
