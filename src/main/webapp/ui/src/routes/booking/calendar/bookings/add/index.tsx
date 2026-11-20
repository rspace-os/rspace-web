import { createFileRoute } from "@tanstack/react-router";
import { enabledBookingPickerTargetQuery } from "@/modules/booking/domain/bookingCatalogue";
import { bookingFormSearch } from "@/modules/booking/pages/bookingFormSearch";
import AddBookingPage from "@/modules/booking/pages/bookings/AddBookingPage";

export const Route = createFileRoute("/booking/calendar/bookings/add/")({
  validateSearch: bookingFormSearch,
  loaderDeps: ({ search }) => ({ target: search.target }),
  loader: ({ context, deps }) => {
    if (deps.target === undefined) return;
    const target = deps.target;
    void context.queryClient.ensureQueryData(enabledBookingPickerTargetQuery(target)).catch(() => undefined);
  },
  component: AddBookingPage,
});
