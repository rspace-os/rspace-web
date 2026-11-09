import { describe, expect, it } from "vitest";
import { createBookingCreationStore } from "../bookingCreationStore";

describe("bookingCreationStore", () => {
  it("allows only the owning calendar to hold and release the creation lease", () => {
    const store = createBookingCreationStore();
    const firstCreation = {
      ownerId: "calendar-1",
      triggerId: "row-1",
      eventKind: "BOOKING" as const,
      window: {
        startDate: "2026-10-25",
        startTime: "02:30",
        startOccurrence: "earlier" as const,
        endDate: "2026-10-25",
        endTime: "02:15",
        endOccurrence: "later" as const,
      },
    };
    const secondCreation = { ownerId: "calendar-2", triggerId: "row-2", eventKind: "BOOKING" as const };

    expect(store.getState().beginCreation(firstCreation)).toBe(true);
    expect(store.getState().beginCreation(secondCreation)).toBe(false);
    expect(store.getState().activeCreation).toEqual(firstCreation);

    store.getState().endCreation(secondCreation.ownerId);
    expect(store.getState().activeCreation).toEqual(firstCreation);

    store.getState().endCreation(firstCreation.ownerId);
    expect(store.getState().activeCreation).toBeNull();
    expect(store.getState().beginCreation(secondCreation)).toBe(true);
  });
});
