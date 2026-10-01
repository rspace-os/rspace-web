import { Suspense } from "react";
import { BookingEventContent } from "./BookingEventContent";
import { BookingEventSkeleton } from "./BookingEventSkeleton";

export { BookingDetailsView } from "./BookingDetailsView";
export { Panel, useBookingEvent } from "./BookingEventContext";

export function BookingEventPage() {
  return (
    <Suspense fallback={<BookingEventSkeleton />}>
      <BookingEventContent />
    </Suspense>
  );
}
