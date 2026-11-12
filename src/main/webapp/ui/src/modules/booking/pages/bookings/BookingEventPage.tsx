import { Suspense } from "react";
import { BookingEventContent } from "./BookingEventContent";
import { BookingEventSkeleton } from "./BookingEventSkeleton";

export const eventPageClassName = "@container mx-auto max-w-5xl space-y-6 p-4 sm:p-8";

export function BookingEventPage() {
  return (
    <Suspense fallback={<BookingEventSkeleton />}>
      <BookingEventContent />
    </Suspense>
  );
}
