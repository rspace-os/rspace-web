import { useNavigate, useSearch } from "@tanstack/react-router";
import * as React from "react";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { BookingEventsCalendar, type CalendarLayout, type CalendarView, calendarDates } from "./BookingEventsCalendar";
import { useCalendarEvents } from "./calendarEvents";

export default function CalendarPage() {
  const { date } = useSearch({ from: "/booking/calendar" });
  const navigate = useNavigate({ from: "/booking/calendar" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  const [view, setView] = React.useState<CalendarView>("week");
  const [layout, setLayout] = React.useState<CalendarLayout>("time-grid");
  const preferences = useBookingDisplayPreferences();
  const selectedDate = date ?? todayInTimeZone(preferences.timeZone);
  const dates = calendarDates(selectedDate, view);
  const events = useCalendarEvents(dates[0], dates.at(-1) ?? dates[0], preferences.timeZone, token);

  return (
    <BookingEventsCalendar
      date={selectedDate}
      view={view}
      layout={layout}
      timezone={preferences.timeZone}
      availabilityStartMinute={preferences.availabilityWindow.startMinute}
      availabilityEndMinute={preferences.availabilityWindow.endMinute}
      events={events.data ?? []}
      currentUserId={currentUser.id}
      isLoading={events.isPending}
      isError={events.isError}
      onRetry={() => void events.refetch()}
      onDateChange={(nextDate) =>
        void navigate({ search: (current) => ({ ...current, date: nextDate }), replace: true })
      }
      onViewChange={setView}
      onLayoutChange={setLayout}
    />
  );
}
