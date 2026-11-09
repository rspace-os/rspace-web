import { createFileRoute } from "@tanstack/react-router";
import { bookingQueryOptions } from "@/modules/booking/domain/booking";
import { BookingEventPage } from "@/modules/booking/pages/bookings/BookingEventPage";

export const Route = createFileRoute("/booking/calendar/bookings/$id")({
  loader: ({ context, params }) => {
    const id = Number(params.id);
    if (!Number.isSafeInteger(id) || id <= 0) return;
    void context.queryClient.ensureQueryData(bookingQueryOptions(id)).catch(() => undefined);
  },
  component: BookingEventPage,
});
