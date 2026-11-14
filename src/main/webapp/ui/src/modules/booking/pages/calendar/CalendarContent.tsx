import * as React from "react";
import { BookingEventsCalendar } from "./BookingEventsCalendar";
import { useCalendarContentData } from "./useCalendarContentData";

export function CalendarContent() {
  const {
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
