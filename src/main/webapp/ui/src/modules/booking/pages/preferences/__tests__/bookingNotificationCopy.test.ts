import { expect, it } from "vitest";
import booking from "@/modules/common/i18n/locales/en-US/booking.json";
import profile from "@/modules/common/i18n/locales/en-US/server.admin.json";

it("uses the same booking event descriptions as My Profile", () => {
  expect(booking.notificationSubscriptions.preferences.events.created).toBe(profile.userProfile.message.bookingCreated);
});

it("uses the same cancellation description as My Profile", () => {
  expect(booking.notificationSubscriptions.preferences.events.cancelled).toBe(
    profile.userProfile.message.bookingCancelled,
  );
});
