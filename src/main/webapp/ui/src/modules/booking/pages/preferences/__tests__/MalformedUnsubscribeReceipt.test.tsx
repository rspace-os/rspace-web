import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { UnsubscribeResult } from "@/modules/common/api/v2/generated/models/unsubscribeResult";
import { renderPage, useDefaultBookingPreferencesHandlers } from "./bookingPreferencesPageTestUtils";

it("reports an unreadable unsubscribe receipt as a mutation failure", async () => {
  useDefaultBookingPreferencesHandlers();
  server.use(
    http.delete<never, never, Partial<UnsubscribeResult>>("/api/v2/users/me/booking-notification-subscriptions", () =>
      HttpResponse.json({}),
    ),
  );
  const user = userEvent.setup();
  renderPage();
  await user.click(
    await screen.findByRole("button", { name: "booking:notificationSubscriptions.preferences.unsubscribeAll" }),
  );
  expect(await screen.findByText("booking:notificationSubscriptions.preferences.unsubscribeError")).toBeInTheDocument();
});
