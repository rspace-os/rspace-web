import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it } from "vitest";
import { DEFAULT_SCHEDULING_SETTINGS } from "@/modules/booking/configuration/schedulingSettings";
import { BookingCreationButtonGroup } from "../BookingCreationButtonGroup";
import { bookableItemOption } from "../bookableItemOption";
import { BookingCreationStoreProvider, createBookingCreationStore } from "../bookingCreationStore";

it.each([true, false])(
  "uses the item's maintenance capability (%s), without requiring a global role",
  async (allowed) => {
    const user = userEvent.setup();
    const store = createBookingCreationStore();
    const target = bookableItemOption({
      id: 7,
      target: { globalId: "IN123", value: { id: 123, name: "Microscope" } },
      timezone: "UTC",
      ...DEFAULT_SCHEDULING_SETTINGS,
      capabilities: { canCreateBlockout: allowed },
    });
    render(
      <BookingCreationStoreProvider store={store}>
        <BookingCreationButtonGroup ownerId="test" target={target} />
      </BookingCreationStoreProvider>,
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
