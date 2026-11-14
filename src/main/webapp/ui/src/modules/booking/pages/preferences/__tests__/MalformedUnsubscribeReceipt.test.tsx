import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { BookingNotificationSubscriptionControllerUnsubscribeResult } from "@/modules/common/api/v2/generated/models/bookingNotificationSubscriptionControllerUnsubscribeResult";
import { renderPage, useDefaultBookingPreferencesHandlers } from "./bookingPreferencesPageTestUtils";

it("reports an unreadable unsubscribe receipt as a mutation failure", async () => {
  useDefaultBookingPreferencesHandlers();
  server.use(
    http.delete<never, never, Partial<BookingNotificationSubscriptionControllerUnsubscribeResult>>(
      "/api/v2/users/me/booking-notification-subscriptions",
      () => HttpResponse.json({}),
    ),
  );
  const user = userEvent.setup();
  renderPage();
  await user.click(
    await screen.findByRole("button", { name: "booking:notificationSubscriptions.preferences.unsubscribeAll" }),
  );
  expect(await screen.findByText("booking:notificationSubscriptions.preferences.unsubscribeError")).toBeInTheDocument();
});
