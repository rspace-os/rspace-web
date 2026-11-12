import { BookingCreationButtonGroup } from "@/modules/booking/creation/BookingCreationButtonGroup";
import { dayMinuteToZonedTime, wallClockDraftFromInstants } from "@/modules/booking/domain/bookingTime";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import { DroppedFilterNotice } from "../DroppedFilterNotice";
import { hasAvailabilityFilter, withAvailability } from "./availability/availabilityQuickFilters";
import { BookingEventsCalendar } from "./BookingEventsCalendar";
import { useCalendarContentData } from "./useCalendarContentData";

export function CalendarContent() {
  const {
    availabilityLayout,
    itemConfig,
    droppedFilterFields,
    calendarSearch,
    view,
    setView,
    layout,
    setLayout,
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
    setMineOnly,
    myItemsOnly,
    setMyItemsOnly,
    target,
    targetName,
    replaceSearch,
  } = useCalendarContentData();

  return (
    <>
      <DroppedFilterNotice fields={droppedFilterFields} />
      <BookingEventsCalendar
        availabilityLayout={availabilityLayout}
        itemFilterConfig={itemConfig}
        onItemFilterChange={(expression) =>
          replaceSearch((search) => {
            if (expression) search.set("calendar-resources.where", serializeRsqlExpression(expression));
            else search.delete("calendar-resources.where");
            if (hasAvailabilityFilter(expression)) search.delete("date");
          })
        }
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
        targetFilter={
          target
            ? {
                globalId: target,
                name: targetName,
                onRemove: () => replaceSearch("target"),
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
          setMineOnly(next);
          resourceTable.setPage({ ...resourceTable.state.page, pageIndex: 0 });
        }}
        myItemsOnly={myItemsOnly}
        onMyItemsChange={(next) => {
          setMyItemsOnly(next);
          resourceTable.setPage({ ...resourceTable.state.page, pageIndex: 0 });
        }}
        isLoading={
          layout === "availability"
            ? availabilityLayout.tableProps.status === "loading"
            : itemRuntimeFields.pending ||
              eventRuntimeFields.pending ||
              (displayReady && events.isPending && resourceTable.tableProps.status !== "error")
        }
        isError={
          layout === "availability"
            ? availabilityLayout.queryReady && availabilityLayout.tableProps.status === "error"
            : events.isError || (layout === "resources" && resourceTable.tableProps.status === "error")
        }
        onRetry={() => {
          if (layout === "availability") {
            void availabilityLayout.refetch();
            return;
          }
          if (layout === "resources" && resourceTable.tableProps.status === "error") {
            void resourceTable.refetch();
          } else {
            void events.refetch();
          }
        }}
        onDateChange={(nextDate) =>
          replaceSearch((search) => {
            search.set("date", nextDate);
            if (layout === "availability") {
              const remaining = withAvailability(itemExpression, undefined);
              if (remaining) search.set("calendar-resources.where", serializeRsqlExpression(remaining));
              else search.delete("calendar-resources.where");
            }
          })
        }
        onControlsReset={() => {
          replaceSearch((search) => {
            for (const key of [
              "date",
              "target",
              "calendar-resources.q",
              "calendar-resources.where",
              "calendar-events.where",
              "view",
              "layout",
            ])
              search.delete(key);
          });
          resourceTable.setPage({ ...resourceTable.state.page, pageIndex: 0 });
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
    </>
  );
}
