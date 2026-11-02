import { createFileRoute } from "@tanstack/react-router";
import { bookingQueryOptions } from "@/modules/booking/domain/booking";
import { BookingEventPage } from "@/modules/booking/pages/bookings/BookingEventPage";
import { oauthTokenQueryOptions } from "@/modules/common/hooks/auth";

export const Route = createFileRoute("/booking/calendar/bookings/$id")({
  loader: ({ context, params }) => {
    const id = Number(params.id);
    if (!Number.isSafeInteger(id) || id <= 0) return;
    void context.queryClient
      .fetchQuery(oauthTokenQueryOptions(true))
      .then((token) => context.queryClient.ensureQueryData(bookingQueryOptions(id, token)))
      .catch(() => undefined);
  },
  component: BookingEventPage,
});
