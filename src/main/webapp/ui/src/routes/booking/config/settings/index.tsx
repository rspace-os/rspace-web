import { createFileRoute } from "@tanstack/react-router";
import { bookingAdminSettingsQuery } from "@/modules/booking/configuration/schedulingSettingsApi";
import BookingSettingsRoutePage from "@/modules/booking/pages/bookable-items/BookingSettingsRoutePage";
import { oauthTokenQueryOptions } from "@/modules/common/hooks/auth";
import { currentUserQueryOptions } from "@/modules/common/queries/currentUser";

export const Route = createFileRoute("/booking/config/settings/")({
  loader: ({ context }) => {
    void context.queryClient
      .fetchQuery(oauthTokenQueryOptions(true))
      .then(async (token) => {
        const user = await context.queryClient.ensureQueryData(currentUserQueryOptions(token));
        if (user.hasSysAdminRole) {
          await context.queryClient.ensureQueryData(bookingAdminSettingsQuery(token));
        }
      })
      .catch(() => undefined);
  },
  component: BookingSettingsRoutePage,
});
