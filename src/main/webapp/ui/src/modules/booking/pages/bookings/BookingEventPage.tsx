import { Suspense } from "react";
import { BookingEventContent } from "./BookingEventContent";
import { BookingEventSkeleton } from "./BookingEventSkeleton";

export function BookingEventPage() {
  return (
    <Suspense fallback={<BookingEventSkeleton />}>
      <BookingEventContent />
    </Suspense>
  );
}
