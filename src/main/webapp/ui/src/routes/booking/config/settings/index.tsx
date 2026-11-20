import { createFileRoute } from "@tanstack/react-router";
import { bookingAdminSettingsQuery } from "@/modules/booking/configuration/schedulingSettingsApi";
import BookingSettingsRoutePage from "@/modules/booking/pages/bookable-items/BookingSettingsRoutePage";
import { currentUserQueryOptions } from "@/modules/common/queries/currentUser";

export const Route = createFileRoute("/booking/config/settings/")({
  loader: ({ context }) => {
    void context.queryClient
      .ensureQueryData(currentUserQueryOptions())
      .then(async (user) => {
        if (user.hasSysAdminRole) {
          await context.queryClient.ensureQueryData(bookingAdminSettingsQuery());
        }
      })
      .catch(() => undefined);
  },
  component: BookingSettingsRoutePage,
});
