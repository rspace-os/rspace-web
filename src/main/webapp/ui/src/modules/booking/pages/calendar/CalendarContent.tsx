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
    view,
    setView,
    layout,
    setLayout,
    setResettingControls,
    preferences,
    beginCreation,
    creationActive,
    selectedDate,
    resourceTable,
    resourceConfigurations,
    events,
    resourceTargets,
    blockingEvents,
    displayReady,
    eventConfig,
    eventExpression,
    eventIssue,
    eventRuntimeFields,
    filtersBlocked,
    itemExpression,
    itemIssue,
    itemRuntimeFields,
    mineOnly,
    onEventFiltersChange,
    onSearchChange,
    setEventWhere,
    setItemWhere,
    setMineOnly,
    location,
    myItemsOnly,
    setMyItemsOnly,
    targetItemExpression,
  } = useCalendarContentData();

  return (
    <BookingEventsCalendar
      date={selectedDate}
      view={view}
      layout={layout}
      timezone={preferences.timeZone}
      availabilityStartMinute={preferences.availabilityWindow.startMinute}
      availabilityEndMinute={preferences.availabilityWindow.endMinute}
      events={displayReady ? (events.data ?? []) : []}
      blockingEvents={blockingEvents.data ?? []}
      availabilityError={layout === "resources" && view === "day" && blockingEvents.isError}
      onRetryAvailability={() => void blockingEvents.refetch()}
      resources={filtersBlocked ? [] : resourceTargets}
      resourceConfigurations={resourceConfigurations}
      resourceTableProps={
        filtersBlocked ? { ...resourceTable.tableProps, rows: [], status: "idle" } : resourceTable.tableProps
      }
      eventFilterConfig={eventConfig}
      itemFilterExpression={itemExpression}
      eventFilterExpression={eventExpression}
      itemFilterIssue={itemIssue}
      eventFilterIssue={eventIssue}
      hasTargetFilter={targetItemExpression !== null}
      eventRuntimeFieldDefinitions={eventRuntimeFields.runtimeFields}
      eventRuntimeFieldAuthScope={eventRuntimeFields.scope}
      onSelectEventRuntimeField={eventRuntimeFields.selectRuntimeField}
      onEventFilterChange={onEventFiltersChange}
      searchControl={{
        value: calendarSearch,
        onChange: onSearchChange,
      }}
      mineOnly={mineOnly}
      onMineChange={(next) => {
        setMineOnly(next);
        resourceTable.setPage({ ...resourceTable.state.page, pageIndex: 0 });
      }}
      myItemsOnly={myItemsOnly}
      onMyItemsChange={(next) => {
        setMyItemsOnly(next);
        resourceTable.setPage({ ...resourceTable.state.page, pageIndex: 0 });
      }}
      isLoading={
        itemRuntimeFields.pending ||
        eventRuntimeFields.pending ||
        (displayReady && events.isPending && resourceTable.tableProps.status !== "error")
      }
      isError={events.isError || (layout === "resources" && resourceTable.tableProps.status === "error")}
      onRetry={() => {
        if (layout === "resources" && resourceTable.tableProps.status === "error") {
          void resourceTable.refetch();
        } else {
          void events.refetch();
        }
      }}
      onDateChange={(nextDate) => {
        const search = new URLSearchParams(location.searchStr);
        search.set("date", nextDate);
        void navigate({ to: `${location.pathname}?${search.toString()}`, replace: true });
      }}
      onControlsReset={async () => {
        // Date navigation is asynchronous; avoid fetching an intermediate date/period combination.
        setResettingControls(true);
        try {
          await navigate({ search: (current) => ({ ...current, date: undefined, target: undefined }), replace: true });
          await Promise.all([setCalendarSearch(null), setItemWhere(null), setEventWhere(null)]);
          setMineOnly(false);
          setMyItemsOnly(false);
          resourceTable.setPage({ ...resourceTable.state.page, pageIndex: 0 });
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
      creationDisabled={
        creationActive || !displayReady || (view === "day" && (blockingEvents.isPending || blockingEvents.isError))
      }
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
