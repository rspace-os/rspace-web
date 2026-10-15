import * as React from "react";
import { BookingCreationButtonGroup } from "@/modules/booking/creation/BookingCreationButtonGroup";
import { dayMinuteToZonedTime, wallClockDraftFromInstants } from "@/modules/booking/domain/bookingTime";
import { BookingEventsCalendar } from "./BookingEventsCalendar";
import { calendarEventFocusRetryChanges } from "./calendarEventFocus";
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
    myItemsOnly,
    setMyItemsOnly,
    target,
    targetName,
    replaceSearch,
    targetOption,
    focusFallback,
    focusUnavailableRequest,
    setFocusUnavailableRequest,
  } = useCalendarContentData();

  return (
    <BookingEventsCalendar
      date={selectedDate}
      view={view}
      layout={layout}
      timezone={preferences.timeZone}
      focusUnavailable={focusUnavailableRequest !== undefined}
      focusFallback={focusFallback}
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
      targetFilter={
        target
          ? {
              globalId: target,
              name: targetName,
              loading: targetName === undefined && targetOption?.restoreStatus === "loading",
              unresolvedLabel:
                targetName === undefined && targetOption?.restoreStatus !== "loading" ? targetOption?.label : undefined,
              onRemove: () => replaceSearch((search) => search.delete("target")),
            }
          : undefined
      }
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
        void setMineOnly(next);
        resourceTable.setPage({ ...resourceTable.state.page, pageIndex: 0 });
      }}
      myItemsOnly={myItemsOnly}
      onMyItemsChange={(next) => {
        void setMyItemsOnly(next);
        resourceTable.setPage({ ...resourceTable.state.page, pageIndex: 0 });
      }}
      isLoading={
        itemRuntimeFields.pending ||
        eventRuntimeFields.pending ||
        (displayReady && events.isPending && resourceTable.tableProps.status !== "error")
      }
      isError={events.isError || (layout === "resources" && resourceTable.tableProps.status === "error")}
      onRetry={() => {
        if (focusUnavailableRequest) {
          setFocusUnavailableRequest(undefined);
          replaceSearch((search) => calendarEventFocusRetryChanges(search, focusUnavailableRequest), false);
        }
        if (layout === "resources" && resourceTable.tableProps.status === "error") {
          void resourceTable.refetch();
        } else {
          void events.refetch();
        }
      }}
      onDateChange={(nextDate) => replaceSearch((search) => search.set("date", nextDate))}
      onControlsReset={async () => {
        // Date navigation is asynchronous; avoid fetching an intermediate date/period combination.
        setResettingControls(true);
        try {
          await navigate({ search: (current) => ({ ...current, date: undefined, target: undefined }), replace: true });
          await Promise.all([
            setCalendarSearch(null),
            setItemWhere(null),
            setEventWhere(null),
            setMineOnly(false),
            setMyItemsOnly(false),
          ]);
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
        creationActive ||
        !displayReady ||
        // Only the Resources day view loads the blocking events, which propose a row's free window. A range chosen
        // on the Time grid needs no proposal, and there the disabled query would read as pending forever.
        (layout === "resources" && view === "day" && (blockingEvents.isPending || blockingEvents.isError))
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
