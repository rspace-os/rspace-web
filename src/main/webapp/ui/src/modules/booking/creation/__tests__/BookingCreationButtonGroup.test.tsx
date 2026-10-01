import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it } from "vitest";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { DEFAULT_SCHEDULING_SETTINGS } from "@/modules/booking/configuration/schedulingSettings";
import { currentUser } from "@/modules/booking/pages/calendar/calendarFixtures";
import { currentUserQueryKeys } from "@/modules/common/queries/currentUser";
import { BookingCreationButtonGroup } from "../BookingCreationButtonGroup";
import { bookableItemOption } from "../bookableItemOption";
import { BookingCreationStoreProvider, createBookingCreationStore } from "../bookingCreationStore";

it.each([true, false])(
  "uses the item's maintenance capability (%s), without requiring a global role",
  async (allowed) => {
    const user = userEvent.setup();
    const queryClient = new QueryClient();
    queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
    queryClient.setQueryData(currentUserQueryKeys.me(), currentUser);
    const store = createBookingCreationStore();
    const target = bookableItemOption({
      id: 7,
      target: { globalId: "IN123", value: { id: 123, name: "Microscope" } },
      timezone: "UTC",
      ...DEFAULT_SCHEDULING_SETTINGS,
      capabilities: { canCreateBlockout: allowed },
    });
    render(
      <QueryClientProvider client={queryClient}>
        <BookingCreationStoreProvider store={store}>
          <BookingCreationButtonGroup ownerId="test" target={target} />
        </BookingCreationStoreProvider>
      </QueryClientProvider>,
    );
    const menu = screen.queryByRole("button", { name: "booking:bookings.actions.moreCreationOptions" });
    if (!allowed) {
      expect(menu).not.toBeInTheDocument();
      return;
    }
    expect(menu).toBeVisible();
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.moreCreationOptions" }));
    await user.click(await screen.findByRole("menuitem", { name: "booking:bookings.actions.newMaintenance" }));
    expect(store.getState().activeCreation).toMatchObject({ eventKind: "MAINTENANCE", target });
  },
);
