import { useParams } from "@tanstack/react-router";
import { Suspense } from "react";
import { BookingEventContent } from "./BookingEventContent";
import { BookingEventSkeleton } from "./BookingEventSkeleton";

export { BookingDetailsView } from "./BookingDetailsView";
export { Panel, useBookingEvent } from "./BookingEventContext";

export function BookingEventPage() {
  const { id } = useParams({ from: "/booking/calendar/bookings/$id" });
  return (
    <Suspense fallback={<BookingEventSkeleton />}>
      <BookingEventContent key={id} />
    </Suspense>
  );
}
